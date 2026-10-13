package org.hermesprd.workbench.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * TECH_SPEC 沙箱：架构 Markdown + 表/API JSON（可选 modules 补充），提交时组装。
 * 不再要求研发工单拆解。
 */
public final class TechSpecWorkspace {
    public static final int MAX_FILE_BYTES = 200 * 1024;
    public static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;
    public static final String README = "README.md";
    public static final String ARCHITECTURE = "architecture.md";
    public static final String TABLES = "data/tables.json";
    public static final String APIS = "data/apis.json";
    public static final String TASKS = "data/tasks.json";
    public static final String CATALOG = "data/catalog.json";
    public static final String REQUIRED_MODULES = "data/required-modules.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TechSpecWorkspace() {
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
        if (README.equals(path) || ARCHITECTURE.equals(path)) {
            return true;
        }
        if (path.endsWith(".md") && path.indexOf('/') < 0) {
            return fileNameOk(path);
        }
        if (path.startsWith("modules/") && (path.endsWith(".md") || path.endsWith(".json")) && path.indexOf('/', 8) < 0) {
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
                    .filter(TechSpecWorkspace::isAllowedPath)
                    .sorted()
                    .forEach(files::add);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "列出技术方案工作区文件失败: " + ex.getMessage());
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
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "技术方案沙箱不允许文件: " + path);
            }
            Path target = wsDir.resolve(path).toAbsolutePath().normalize();
            if (!target.startsWith(wsDir.toAbsolutePath().normalize()) || !Files.isRegularFile(target)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "工作区文件不存在: " + path);
            }
            try {
                byte[] bytes = Files.readAllBytes(target);
                if (bytes.length > MAX_FILE_BYTES) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE",
                            "技术方案单文件不能超过 200KB: " + path);
                }
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE",
                            "技术方案工作区合计不能超过 8MB");
                }
                files.put(path, new String(bytes, StandardCharsets.UTF_8));
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "读取技术方案工作区文件失败: " + path);
            }
        }
        if (!files.containsKey(ARCHITECTURE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "技术方案文档包需要 architecture.md");
        }
        if (!files.containsKey(TABLES) && assembleTables(files).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "技术方案文档包需要 data/tables.json");
        }
        if (!files.containsKey(APIS) && assembleApis(files).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "技术方案文档包需要 data/apis.json");
        }
        return files;
    }

    public static String architectureMarkdown(Map<String, String> files) {
        if (files == null) {
            return "";
        }
        String arch = files.get(ARCHITECTURE);
        return arch == null ? "" : arch.trim();
    }

    public static ArrayNode readJsonArray(Map<String, String> files, String path, String fieldName) {
        ArrayNode empty = MAPPER.createArrayNode();
        if (files == null || !files.containsKey(path)) {
            return empty;
        }
        try {
            JsonNode root = MAPPER.readTree(files.get(path));
            if (root.isArray()) {
                return (ArrayNode) root;
            }
            if (root.isObject() && root.has(fieldName) && root.get(fieldName).isArray()) {
                return (ArrayNode) root.get(fieldName);
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", path + " 必须是数组或含 " + fieldName + " 数组");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "无法解析 " + path + ": " + ex.getMessage());
        }
    }

    public static ArrayNode assembleTables(Map<String, String> files) {
        return mergeJsonArrays(files, TABLES, "databaseTables", "tables");
    }

    public static ArrayNode assembleApis(Map<String, String> files) {
        return mergeJsonArrays(files, APIS, "apiEndpoints", "apis");
    }

    public static ArrayNode assembleTasks(Map<String, String> files) {
        // Legacy optional; empty when the pack has no work-order files.
        return mergeJsonArrays(files, TASKS, "tasks");
    }

    /**
     * Prefer the canonical path, then merge any other data/*.json / modules/*.json that carry the same array field
     * so oversized packs can be split without losing delivery content.
     * Root JSON arrays are only accepted from the primary path (or companion files whose name contains
     * the primary stem, e.g. tables / apis / tasks); other companions must use a named object field.
     */
    private static ArrayNode mergeJsonArrays(Map<String, String> files, String primaryPath, String... fieldNames) {
        ArrayNode merged = MAPPER.createArrayNode();
        if (files == null || files.isEmpty()) {
            return merged;
        }
        String primaryStem = primaryStem(primaryPath);
        List<String> paths = new ArrayList<>();
        if (files.containsKey(primaryPath)) {
            paths.add(primaryPath);
        }
        files.keySet().stream()
                .filter(path -> !path.equals(primaryPath))
                .filter(path -> path.startsWith("data/") || path.startsWith("modules/"))
                .filter(path -> path.endsWith(".json"))
                .sorted()
                .forEach(paths::add);
        for (String path : paths) {
            boolean allowRootArray = path.equals(primaryPath) || pathContainsStem(path, primaryStem);
            ArrayNode part = tryReadJsonArray(files.get(path), allowRootArray, fieldNames);
            if (part != null) {
                part.forEach(merged::add);
            }
        }
        return merged;
    }

    private static String primaryStem(String primaryPath) {
        String name = primaryPath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - 5);
        }
        return name.toLowerCase();
    }

    private static boolean pathContainsStem(String path, String stem) {
        String lower = path.toLowerCase();
        return !stem.isBlank() && lower.contains(stem);
    }

    private static ArrayNode tryReadJsonArray(String content, boolean allowRootArray, String... fieldNames) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(content);
            if (root.isArray()) {
                return allowRootArray ? (ArrayNode) root : null;
            }
            if (root.isObject()) {
                for (String field : fieldNames) {
                    if (root.has(field) && root.get(field).isArray()) {
                        return (ArrayNode) root.get(field);
                    }
                }
            }
        } catch (Exception ignored) {
            // Split companion files may be unrelated JSON; skip quietly during merge.
        }
        return null;
    }

    private static boolean fileNameOk(String name) {
        return !name.isBlank() && !name.contains("/") && !name.equals(".") && !name.equals("..");
    }
}
