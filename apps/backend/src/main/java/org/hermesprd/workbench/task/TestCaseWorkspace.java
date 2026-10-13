package org.hermesprd.workbench.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.stream.Stream;

/**
 * TEST_CASE 沙箱：按模块拆写用例 JSON，提交时组装 items。
 */
public final class TestCaseWorkspace {
    public static final int MAX_FILE_BYTES = 200 * 1024;
    public static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;
    public static final String README = "README.md";
    public static final String CATALOG = "data/catalog.json";
    public static final String REQUIRED_MODULES = "data/required-modules.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestCaseWorkspace() {
    }

    public static String normalizeRelativePath(String rawFilePath) {
        return PocWorkspace.normalizeRelativePath(rawFilePath);
    }

    public static boolean isAllowedPath(String path) {
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
        if (path.contains("..") || path.startsWith("/") || path.contains("\\") || path.contains(":")) {
            return false;
        }
        if (README.equals(path)) {
            return true;
        }
        if (path.startsWith("modules/") && path.endsWith(".json") && path.indexOf('/', 8) < 0) {
            return fileNameOk(path.substring(8));
        }
        if (path.startsWith("data/") && path.endsWith(".json") && path.indexOf('/', 5) < 0) {
            return fileNameOk(path.substring(5));
        }
        return false;
    }

    public static List<String> listRelativeFiles(Path wsDir) {
        if (!Files.isDirectory(wsDir)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(wsDir)) {
            walk.filter(Files::isRegularFile)
                    .map(path -> wsDir.relativize(path).toString().replace('\\', '/'))
                    .filter(TestCaseWorkspace::isAllowedPath)
                    .sorted()
                    .forEach(files::add);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "列出用例工作区文件失败: " + ex.getMessage());
        }
        return files;
    }

    public static Map<String, String> readAllowedFiles(Path wsDir, List<String> requested) {
        List<String> paths = requested == null || requested.isEmpty() ? listRelativeFiles(wsDir) : requested;
        Map<String, String> files = new LinkedHashMap<>();
        long total = 0;
        for (String raw : paths) {
            String path = normalizeRelativePath(raw);
            if (!isAllowedPath(path)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "用例沙箱不允许文件: " + path);
            }
            Path target = wsDir.resolve(path).toAbsolutePath().normalize();
            if (!target.startsWith(wsDir.toAbsolutePath().normalize()) || !Files.isRegularFile(target)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "工作区文件不存在: " + path);
            }
            try {
                byte[] bytes = Files.readAllBytes(target);
                if (bytes.length > MAX_FILE_BYTES) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE",
                            "用例单文件不能超过 200KB: " + path);
                }
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE",
                            "用例工作区合计不能超过 8MB");
                }
                files.put(path, new String(bytes, StandardCharsets.UTF_8));
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "读取用例工作区文件失败: " + path);
            }
        }
        if (!files.containsKey(CATALOG)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "用例文档包需要 data/catalog.json");
        }
        return files;
    }

    public static ArrayNode assembleItems(Map<String, String> files) {
        ArrayNode items = MAPPER.createArrayNode();
        if (files == null || files.isEmpty()) {
            return items;
        }
        List<String> modulePaths = files.keySet().stream()
                .filter(path -> path.startsWith("modules/") && path.endsWith(".json"))
                .sorted()
                .toList();
        for (String path : modulePaths) {
            String key = path.substring("modules/".length(), path.length() - ".json".length());
            try {
                JsonNode root = MAPPER.readTree(files.get(path));
                String moduleTitle = key;
                ArrayNode moduleItems = null;
                if (root.isArray()) {
                    moduleItems = (ArrayNode) root;
                } else if (root.isObject()) {
                    if (root.has("module") && root.get("module").isTextual() && !root.get("module").asText().isBlank()) {
                        moduleTitle = root.get("module").asText().trim();
                    } else if (root.has("title") && root.get("title").isTextual()) {
                        moduleTitle = root.get("title").asText().trim();
                    }
                    if (root.has("items") && root.get("items").isArray()) {
                        moduleItems = (ArrayNode) root.get("items");
                    }
                }
                if (moduleItems == null) {
                    continue;
                }
                for (JsonNode item : moduleItems) {
                    if (item == null || !item.isObject()) {
                        continue;
                    }
                    ObjectNode copy = item.deepCopy();
                    if (!copy.has("module") || !copy.get("module").isTextual() || copy.get("module").asText().isBlank()) {
                        copy.put("module", moduleTitle);
                    }
                    if (!copy.has("moduleKey") || !copy.get("moduleKey").isTextual()) {
                        copy.put("moduleKey", key);
                    }
                    items.add(copy);
                }
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "无法解析用例模块文件 " + path + ": " + ex.getMessage());
            }
        }
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "用例文档包未组装出任何 items");
        }
        return items;
    }

    private static boolean fileNameOk(String name) {
        return !name.isBlank() && !name.contains("/") && !name.equals(".") && !name.equals("..");
    }
}
