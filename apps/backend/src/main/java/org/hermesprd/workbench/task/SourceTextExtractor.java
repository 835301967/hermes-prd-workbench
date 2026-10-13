package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.EmptyFileException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SourceTextExtractor {
    static final Set<String> ALLOWED_EXTENSIONS = Set.of("md", "txt", "docx", "xlsx");

    private SourceTextExtractor() {
    }

    public static String extract(byte[] bytes, String extension) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "md", "txt" -> new String(bytes, StandardCharsets.UTF_8);
            case "docx" -> extractDocx(bytes);
            case "xlsx" -> extractXlsx(bytes);
            default -> throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", unsupportedTypeMessage());
        };
    }

    public static String mediaTypeFor(String extension) {
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "md" -> "text/markdown";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "text/plain";
        };
    }

    public static String unsupportedTypeMessage() {
        return "当前支持 MD、TXT、DOCX、XLSX";
    }

    private static String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                appendLine(builder, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cells.add(cell.getText().trim());
                    }
                    appendJoinedLine(builder, cells);
                }
                builder.append('\n');
            }
            return builder.toString().trim();
        } catch (IOException | UnsupportedFileFormatException | EmptyFileException | POIXMLException | OpenXML4JRuntimeException ex) {
            throw parseFailure("DOCX");
        }
    }

    private static String extractXlsx(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            DataFormatter formatter = new DataFormatter();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                builder.append("# Sheet: ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(formatter.formatCellValue(cell).trim());
                    }
                    appendJoinedLine(builder, cells);
                }
                builder.append('\n');
            }
            return builder.toString().trim();
        } catch (IOException | UnsupportedFileFormatException | EmptyFileException | POIXMLException | OpenXML4JRuntimeException ex) {
            throw parseFailure("XLSX");
        }
    }

    private static void appendLine(StringBuilder builder, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.isEmpty()) {
            builder.append(trimmed).append('\n');
        }
    }

    private static void appendJoinedLine(StringBuilder builder, List<String> cells) {
        if (cells.stream().anyMatch(value -> !value.isEmpty())) {
            builder.append(String.join("\t", cells)).append('\n');
        }
    }

    private static ApiException parseFailure(String format) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "无法解析 " + format + " 文件");
    }
}
