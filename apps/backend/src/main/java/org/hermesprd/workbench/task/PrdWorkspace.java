package org.hermesprd.workbench.task;

import org.hermesprd.workbench.shared.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * GENERATE 文档包沙箱：白名单、读取与按 README 顺序组装 Markdown。
 * Demo / POC 仍走 {@link PocWorkspace}，不要把 PRD 规则写进 Demo。
 */
public final class PrdWorkspace {
    public static final int MAX_FILE_BYTES = 200 * 1024;
    public static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;
    public static final String README = "README.md";
    public static final String CATALOG = "data/catalog.json";

    private static final Pattern README_PATH = Pattern.compile("`([^`]+)`");
    /** ATX 标题：`#`～`######`，允许可选闭合尾缀 `#`。 */
    private static final Pattern ATX_HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.+?)(?:[ \\t]+#+)?[ \\t]*$");
    /** 合并编号前剥掉已有阿拉伯数字前缀，避免 `1 1. 概述` 双重编号。 */
    private static final Pattern LEADING_SECTION_NUMBER = Pattern.compile("^\\d+(?:\\.\\d+)*\\.?[ \\t]+");
    private static final Pattern FENCE_OPEN = Pattern.compile("^([`~]{3,})(.*)$");
    private static final List<String> DEFAULT_CHAPTERS = List.of(
            "00-overview.md",
            "01-ia.md",
            "02-global.md",
            "03-permissions.md",
            "04-nfr.md"
    );

    private PrdWorkspace() {
    }

    public static String normalizeRelativePath(String rawFilePath) {
        return PocWorkspace.normalizeRelativePath(rawFilePath);
    }

    public static boolean isPrdAllowedPath(String path) {
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
        if (README.equals(path) || path.endsWith(".md") && path.indexOf('/') < 0) {
            return fileNameOk(path);
        }
        if (path.startsWith("modules/") && path.endsWith(".md") && path.indexOf('/', 8) < 0) {
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
                    .filter(PrdWorkspace::isPrdAllowedPath)
                    .sorted()
                    .forEach(files::add);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "列出 PRD 工作区文件失败: " + ex.getMessage());
        }
        return files;
    }

    public static Map<String, String> readAllowedFiles(Path wsDir, List<String> requested) {
        List<String> paths = requested == null || requested.isEmpty() ? listRelativeFiles(wsDir) : requested;
        Map<String, String> files = new LinkedHashMap<>();
        long total = 0;
        for (String raw : paths) {
            String path = normalizeRelativePath(raw);
            if (!isPrdAllowedPath(path)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "PRD 沙箱不允许文件: " + path);
            }
            Path target = wsDir.resolve(path).toAbsolutePath().normalize();
            if (!target.startsWith(wsDir.toAbsolutePath().normalize()) || !Files.isRegularFile(target)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "工作区文件不存在: " + path);
            }
            try {
                byte[] bytes = Files.readAllBytes(target);
                if (bytes.length > MAX_FILE_BYTES) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE",
                            "PRD 单文件不能超过 200KB: " + path);
                }
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESULT_TOO_LARGE",
                            "PRD 工作区合计不能超过 8MB");
                }
                files.put(path, new String(bytes, StandardCharsets.UTF_8));
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IO_ERROR", "读取 PRD 工作区文件失败: " + path);
            }
        }
        if (!files.containsKey(README)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "PRD 文档包需要 README.md");
        }
        return files;
    }

    public static String assembleMarkdown(Map<String, String> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        List<String> order = assemblyOrder(files);
        StringBuilder out = new StringBuilder();
        Set<String> used = new LinkedHashSet<>();
        for (String path : order) {
            String content = files.get(path);
            if (content == null || path.toLowerCase(Locale.ROOT).endsWith(".json")) {
                continue;
            }
            used.add(path);
            appendChapter(out, path, content);
        }
        List<String> remaining = new ArrayList<>();
        for (String path : files.keySet()) {
            if (used.contains(path) || path.toLowerCase(Locale.ROOT).endsWith(".json")) {
                continue;
            }
            remaining.add(path);
        }
        remaining.sort(String::compareTo);
        for (String path : remaining) {
            appendChapter(out, path, files.get(path));
        }
        return numberHeadings(out.toString().trim());
    }

    /**
     * 按文档出现顺序为全部 ATX 标题补全层级编号：1 / 1.1 / 1.1.1 / 1.1.1.1 …
     * 跳过围栏代码块内的行；会剥掉标题上已有的阿拉伯数字前缀后重新编号。
     */
    public static String numberHeadings(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown == null ? "" : markdown;
        }
        String[] lines = markdown.split("\\R", -1);
        int[] counters = new int[6];
        StringBuilder out = new StringBuilder(markdown.length() + 64);
        String fenceMarker = null;
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            String line = lines[i];
            if (fenceMarker != null) {
                out.append(line);
                if (closesFence(line, fenceMarker)) {
                    fenceMarker = null;
                }
                continue;
            }
            Matcher fence = FENCE_OPEN.matcher(line);
            if (fence.matches() && looksLikeFenceOpener(fence.group(1), fence.group(2))) {
                fenceMarker = fence.group(1).substring(0, 1);
                out.append(line);
                continue;
            }
            Matcher heading = ATX_HEADING.matcher(line);
            if (!heading.matches()) {
                out.append(line);
                continue;
            }
            int level = heading.group(1).length();
            String title = stripLeadingSectionNumber(heading.group(2).trim());
            if (title.isEmpty()) {
                out.append(line);
                continue;
            }
            counters[level - 1]++;
            for (int j = level; j < counters.length; j++) {
                counters[j] = 0;
            }
            out.append(heading.group(1)).append(' ').append(formatSectionNumber(counters, level)).append(' ').append(title);
        }
        return out.toString();
    }

    static String stripLeadingSectionNumber(String title) {
        if (title == null || title.isBlank()) {
            return title == null ? "" : title;
        }
        return LEADING_SECTION_NUMBER.matcher(title.trim()).replaceFirst("");
    }

    private static String formatSectionNumber(int[] counters, int level) {
        StringBuilder number = new StringBuilder();
        for (int i = 0; i < level; i++) {
            if (i > 0) {
                number.append('.');
            }
            number.append(counters[i]);
        }
        return number.toString();
    }

    private static boolean looksLikeFenceOpener(String ticks, String info) {
        if (ticks == null || ticks.length() < 3) {
            return false;
        }
        char mark = ticks.charAt(0);
        for (int i = 1; i < ticks.length(); i++) {
            if (ticks.charAt(i) != mark) {
                return false;
            }
        }
        // 信息串里再出现围栏标记时不当作开围栏，避免误伤。
        return info == null || info.indexOf(mark) < 0;
    }

    private static boolean closesFence(String line, String fenceMarker) {
        String trimmed = line.trim();
        if (trimmed.length() < 3) {
            return false;
        }
        char mark = fenceMarker.charAt(0);
        int i = 0;
        while (i < trimmed.length() && trimmed.charAt(i) == mark) {
            i++;
        }
        return i >= 3 && trimmed.substring(i).isBlank();
    }

    public static List<String> assemblyOrder(Map<String, String> files) {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        String readme = files.get(README);
        if (readme != null) {
            Matcher matcher = README_PATH.matcher(readme);
            while (matcher.find()) {
                String path = matcher.group(1).replace('\\', '/').trim();
                if (files.containsKey(path) && isPrdAllowedPath(path) && !path.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    order.add(path);
                }
            }
        }
        if (order.isEmpty() || order.size() == 1 && order.contains(README)) {
            order.clear();
            for (String chapter : DEFAULT_CHAPTERS) {
                if (files.containsKey(chapter)) {
                    order.add(chapter);
                }
            }
            files.keySet().stream()
                    .filter(path -> path.startsWith("modules/") && path.endsWith(".md"))
                    .sorted()
                    .forEach(order::add);
            if (files.containsKey("open-questions.md")) {
                order.add("open-questions.md");
            }
        }
        return List.copyOf(order);
    }

    private static void appendChapter(StringBuilder out, String path, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        if (!out.isEmpty()) {
            out.append("\n\n---\n\n");
        }
        out.append("<!-- file: ").append(path).append(" -->\n\n");
        out.append(content.trim());
        out.append("\n");
    }

    private static boolean fileNameOk(String name) {
        return !name.isBlank() && !name.contains("/") && !name.equals(".") && !name.equals("..");
    }
}
