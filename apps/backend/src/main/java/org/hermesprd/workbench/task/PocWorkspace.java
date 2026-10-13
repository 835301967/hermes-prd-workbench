package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * POC 沙箱路径白名单与预览内联。DEMO 仍只允许单文件 index.html，不受这里的多文件规则影响。
 */
public final class PocWorkspace {
    public static final int MAX_FILE_BYTES = 100 * 1024;
    public static final int MAX_TOTAL_BYTES = 5 * 1024 * 1024;
    public static final String INDEX = "index.html";
    public static final String REQUIRED_MODULES = "data/required-modules.json";

    private static final Pattern STYLESHEET = Pattern.compile(
            "<link\\b[^>]*rel\\s*=\\s*['\"]stylesheet['\"][^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HREF = Pattern.compile(
            "href\\s*=\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SCRIPT_SRC = Pattern.compile(
            "<script\\b([^>]*)>\\s*</script>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SRC = Pattern.compile(
            "src\\s*=\\s*['\"]([^'\"]+)['\"]",
            Pattern.CASE_INSENSITIVE
    );

    private PocWorkspace() {
    }

    public static String normalizeRelativePath(String rawFilePath) {
        if (rawFilePath == null || rawFilePath.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "filePath 不能为空");
        }
        String path = rawFilePath.replace('\\', '/').trim();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        path = path.replaceAll("/{2,}", "/");
        if (path.startsWith("/") || path.contains("..") || path.contains(":")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SECURITY_ERROR", "非法文件路径");
        }
        if (path.isBlank() || path.endsWith("/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "filePath 不能为空");
        }
        return path;
    }

    public static boolean isDemoAllowedPath(String path) {
        return INDEX.equals(path);
    }

    public static boolean isPocAllowedPath(String path) {
        if (path == null || path.isBlank() || path.length() > 512) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.equals(".env") || lower.contains("/.env") || lower.startsWith(".env.")) {
            return false;
        }
        if (lower.equals("package.json") || lower.startsWith("src/") || lower.contains("/src/")) {
            return false;
        }
        if (INDEX.equals(path)) {
            return true;
        }
        if (path.startsWith("css/") && path.endsWith(".css") && path.indexOf('/', 4) < 0) {
            return fileNameOk(path.substring(4));
        }
        if (path.startsWith("js/") && path.endsWith(".js")) {
            return segmentsOk(path.substring(3));
        }
        if (path.startsWith("data/") && path.endsWith(".json") && path.indexOf('/', 5) < 0) {
            return fileNameOk(path.substring(5));
        }
        return false;
    }

    public static void rejectViteSkeleton(String filePath, String content) {
        if (content == null || !INDEX.equalsIgnoreCase(Path.of(filePath).getFileName().toString())) {
            return;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (content.length() < 1200 && lower.contains("<div id=\"app\">") && lower.contains("main.ts")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "index.html 必须是可运行的静态原型，严禁用 Vite/脚手架挂载骨架替换！");
        }
    }

    public static List<String> listRelativeFiles(Path wsDir) {
        if (!Files.isDirectory(wsDir)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(wsDir)) {
            walk.filter(Files::isRegularFile)
                    .map(path -> wsDir.relativize(path).toString().replace('\\', '/'))
                    .filter(PocWorkspace::isPocAllowedPath)
                    .sorted()
                    .forEach(files::add);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "列出工作区文件失败: " + ex.getMessage());
        }
        return files;
    }

    public static Map<String, String> readAllowedFiles(Path wsDir, List<String> requested) {
        List<String> paths = requested == null || requested.isEmpty() ? listRelativeFiles(wsDir) : requested;
        Map<String, String> files = new LinkedHashMap<>();
        long total = 0;
        for (String raw : paths) {
            String path = normalizeRelativePath(raw);
            if (!isPocAllowedPath(path)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 沙箱不允许文件: " + path);
            }
            Path target = wsDir.resolve(path).toAbsolutePath().normalize();
            if (!target.startsWith(wsDir.toAbsolutePath().normalize()) || !Files.isRegularFile(target)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "工作区文件不存在: " + path);
            }
            try {
                byte[] bytes = Files.readAllBytes(target);
                if (bytes.length > MAX_FILE_BYTES) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE",
                            "POC 单文件不能超过 100KB: " + path);
                }
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE",
                            "POC 工作区合计不能超过 5MB");
                }
                files.put(path, new String(bytes, StandardCharsets.UTF_8));
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "读取工作区文件失败: " + path);
            }
        }
        if (!files.containsKey(INDEX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "POC 结果需要 index.html");
        }
        return files;
    }

    public static String inline(String indexHtml, Map<String, String> files) {
        String html = indexHtml == null ? "" : indexHtml;
        Matcher css = STYLESHEET.matcher(html);
        StringBuffer cssOut = new StringBuffer();
        while (css.find()) {
            String tag = css.group();
            Matcher href = HREF.matcher(tag);
            if (href.find()) {
                String path = normalizeHref(href.group(1));
                String content = files.get(path);
                if (content != null) {
                    css.appendReplacement(cssOut, Matcher.quoteReplacement("<style>\n" + content + "\n</style>"));
                    continue;
                }
            }
            css.appendReplacement(cssOut, Matcher.quoteReplacement(tag));
        }
        css.appendTail(cssOut);
        html = cssOut.toString();

        Matcher scripts = SCRIPT_SRC.matcher(html);
        StringBuffer jsOut = new StringBuffer();
        boolean injectedMock = false;
        while (scripts.find()) {
            String attrs = scripts.group(1);
            Matcher src = SRC.matcher(attrs);
            if (!src.find() || attrs.toLowerCase(Locale.ROOT).contains("type=\"module\"")
                    || attrs.toLowerCase(Locale.ROOT).contains("type='module'")) {
                scripts.appendReplacement(jsOut, Matcher.quoteReplacement(scripts.group()));
                continue;
            }
            String path = normalizeHref(src.group(1));
            String content = files.get(path);
            if (content == null) {
                scripts.appendReplacement(jsOut, Matcher.quoteReplacement(scripts.group()));
                continue;
            }
            StringBuilder replacement = new StringBuilder();
            if (!injectedMock && files.containsKey("data/mock.json")) {
                replacement.append("<script>window.__POC_MOCK__=")
                        .append(files.get("data/mock.json"))
                        .append(";</script>\n");
                injectedMock = true;
            }
            replacement.append("<script>\n").append(content).append("\n</script>");
            scripts.appendReplacement(jsOut, Matcher.quoteReplacement(replacement.toString()));
        }
        scripts.appendTail(jsOut);
        html = jsOut.toString();
        if (!injectedMock && files.containsKey("data/mock.json") && html.toLowerCase(Locale.ROOT).contains("</body>")) {
            html = html.replaceFirst("(?i)</body>",
                    Matcher.quoteReplacement("<script>window.__POC_MOCK__=" + files.get("data/mock.json") + ";</script>\n</body>"));
        }
        return html;
    }

    private static String normalizeHref(String href) {
        String path = href == null ? "" : href.trim();
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        return path.replace('\\', '/');
    }

    private static boolean fileNameOk(String name) {
        return !name.isBlank() && !name.contains("/") && !name.equals(".") && !name.equals("..");
    }

    private static boolean segmentsOk(String relative) {
        String[] parts = relative.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                return false;
            }
        }
        return parts.length >= 1;
    }
}
