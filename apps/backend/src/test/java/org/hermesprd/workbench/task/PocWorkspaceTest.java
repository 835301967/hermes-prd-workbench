package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocWorkspaceTest {
    @Test
    void demoAllowlistStaysSingleIndexHtml() {
        assertTrue(PocWorkspace.isDemoAllowedPath("index.html"));
        assertFalse(PocWorkspace.isDemoAllowedPath("css/app.css"));
        assertFalse(PocWorkspace.isDemoAllowedPath("js/app.js"));
    }

    @Test
    void pocAllowlistAcceptsStaticSpaFilesAndRejectsEngineeringPaths() {
        assertTrue(PocWorkspace.isPocAllowedPath("index.html"));
        assertTrue(PocWorkspace.isPocAllowedPath("css/app.css"));
        assertTrue(PocWorkspace.isPocAllowedPath("js/views/customers.js"));
        assertTrue(PocWorkspace.isPocAllowedPath("data/mock.json"));
        assertFalse(PocWorkspace.isPocAllowedPath("package.json"));
        assertFalse(PocWorkspace.isPocAllowedPath("src/main.ts"));
        assertFalse(PocWorkspace.isPocAllowedPath(".env"));
        assertFalse(PocWorkspace.isPocAllowedPath("../index.html"));
    }

    @Test
    void rejectsViteSkeletonIndex() {
        ApiException ex = assertThrows(ApiException.class, () -> PocWorkspace.rejectViteSkeleton(
                "index.html",
                "<!DOCTYPE html><html><body><div id=\"app\"></div><script type=\"module\" src=\"/src/main.ts\"></script></body></html>"
        ));
        assertEquals("INVALID_REQUEST", ex.code());
    }

    @Test
    void inlinesCssJsAndMockForSrcdoc(@TempDir Path dir) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("index.html", "<!DOCTYPE html><html><head><link rel=\"stylesheet\" href=\"css/app.css\"></head><body><div class=\"app\">POC</div><script src=\"js/app.js\"></script></body></html>");
        files.put("css/app.css", ".app{color:red}");
        files.put("js/app.js", "window.ready=true;");
        files.put("data/mock.json", "{\"ok\":true}");
        Files.createDirectories(dir.resolve("css"));
        Files.createDirectories(dir.resolve("js"));
        Files.createDirectories(dir.resolve("data"));
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Files.writeString(dir.resolve(entry.getKey()), entry.getValue());
        }
        Map<String, String> loaded = PocWorkspace.readAllowedFiles(dir, List.of());
        String html = PocWorkspace.inline(loaded.get("index.html"), loaded);
        assertTrue(html.contains("<style>"));
        assertTrue(html.contains(".app{color:red}"));
        assertTrue(html.contains("window.ready=true;"));
        assertTrue(html.contains("window.__POC_MOCK__"));
        assertFalse(html.contains("href=\"css/app.css\""));
    }

    @Test
    void normalizeRejectsTraversal() {
        assertThrows(ApiException.class, () -> PocWorkspace.normalizeRelativePath("../secret.js"));
        assertThrows(ApiException.class, () -> PocWorkspace.normalizeRelativePath("/tmp/index.html"));
    }
}
