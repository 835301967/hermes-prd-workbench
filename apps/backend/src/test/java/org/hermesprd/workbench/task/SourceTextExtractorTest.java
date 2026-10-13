package org.hermesprd.workbench.task;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.hermesprd.workbench.shared.ApiException;
import org.springframework.http.HttpStatus;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceTextExtractorTest {

    @Test
    void readsPlainTextExtensions() {
        byte[] bytes = "需求说明".getBytes(StandardCharsets.UTF_8);
        assertEquals("需求说明", SourceTextExtractor.extract(bytes, "txt"));
        assertEquals("需求说明", SourceTextExtractor.extract(bytes, "md"));
    }

    @Test
    void extractsDocxText() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("智慧园区 PRD");
            XWPFTable table = document.createTable(1, 2);
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).setText("模块");
            row.getCell(1).setText("能耗看板");
            document.write(out);
            bytes = out.toByteArray();
        }

        String text = SourceTextExtractor.extract(bytes, "docx");
        assertTrue(text.contains("智慧园区 PRD"));
        assertTrue(text.contains("模块"));
        assertTrue(text.contains("能耗看板"));
    }

    @Test
    void extractsXlsxText() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("需求清单");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("字段");
            row.createCell(1).setCellValue("说明");
            Row row2 = sheet.createRow(1);
            row2.createCell(0).setCellValue("环比口径");
            row2.createCell(1).setCellValue("按自然月");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        String text = SourceTextExtractor.extract(bytes, "xlsx");
        assertTrue(text.contains("# Sheet: 需求清单"));
        assertTrue(text.contains("环比口径"));
        assertTrue(text.contains("按自然月"));
    }

    @Test
    void invalidOfficeBytesReturn400ForBothFormats() {
        for (String format : new String[]{"docx", "xlsx"}) {
            assertParseFailure(new byte[]{1, 2, 3, 4}, format);
            assertParseFailure(new byte[0], format);
        }
    }

    @Test
    void malformedDocxXmlReturns400() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("合成有效文本");
            doc.write(out);
            assertParseFailure(corruptXmlPart(out.toByteArray(), "word/document.xml"), "docx");
        }
    }

    @Test
    void malformedXlsxXmlReturns400() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("合成表格");
            workbook.write(out);
            assertParseFailure(corruptXmlPart(out.toByteArray(), "xl/workbook.xml"), "xlsx");
        }
    }

    private static void assertParseFailure(byte[] bytes, String format) {
        ApiException ex = assertThrows(ApiException.class, () -> SourceTextExtractor.extract(bytes, format));
        assertEquals(HttpStatus.BAD_REQUEST, ex.status());
        assertEquals("INVALID_REQUEST", ex.code());
    }

    private static byte[] corruptXmlPart(byte[] original, String part) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(original));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(entry.getName().equals(part) ? "<invalid".getBytes(StandardCharsets.UTF_8) : input.readAllBytes());
                output.closeEntry();
            }
            output.finish();
            return bytes.toByteArray();
        }
    }

    @Test
    void mapsMediaTypes() {
        assertEquals("text/markdown", SourceTextExtractor.mediaTypeFor("md"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", SourceTextExtractor.mediaTypeFor("docx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", SourceTextExtractor.mediaTypeFor("xlsx"));
    }
}
