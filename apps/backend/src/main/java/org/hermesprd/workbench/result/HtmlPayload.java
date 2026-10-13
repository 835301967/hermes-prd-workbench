package org.hermesprd.workbench.result;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class HtmlPayload {
    private static final Pattern EVENT_HANDLER_START = Pattern.compile(
            "on[a-z]+\\s*=\\s*(['\"])[A-Za-z_$][\\w$]*\\s*\\(\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    // 该正则只需匹配缓冲区尾部；限制窗口避免对增长缓冲区反复全量扫描（O(n^2)）。
    private static final int HANDLER_TAIL_WINDOW = 512;

    // `...` 紧跟标识符/开括号/引号时是 ES6 展开或剩余语法，不是输出截断。
    private static final Pattern ELLIPSIS_TRUNCATION = Pattern.compile(
            "\\.{3}\\s*(?![A-Za-z_$\\[\\{\\(`'\"])([;,\\]\\}\\+\\-\\*/=]|\\n|$)"
    );

    /** `/` 之前若是这些关键字，更可能是正则字面量而不是除法。 */
    private static final Set<String> REGEX_PREFIX_KEYWORDS = Set.of(
            "return", "throw", "case", "new", "delete", "void", "typeof",
            "in", "instanceof", "else", "do", "yield", "await", "of"
    );

    private HtmlPayload() {
    }

    public static String unwrap(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int newline = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (newline > 0 && end > newline) {
                text = text.substring(newline + 1, end).trim();
            }
        }
        return text;
    }

    public static boolean looksLikeHtmlDocument(String raw) {
        String html = unwrap(raw).toLowerCase(Locale.ROOT);
        return html.contains("<html")
                && (html.contains("<!doctype html") || html.contains("<head") || html.contains("<body"))
                && html.contains("</html>");
    }

    public static boolean hasSubstantiveBodyContent(String raw) {
        String unwrapped = unwrap(raw);
        String bodyContent = extractBody(unwrapped);
        if (bodyContent == null) {
            return false;
        }
        return stripTagsAndEmbeddedBlocks(bodyContent).length() >= 15;
    }

    /** 取首个闭合 {@code <body>} 的内容；无闭合标签时返回 null。 */
    private static String extractBody(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        int open = indexOfTag(lower, "<body", 0);
        if (open < 0) {
            return null;
        }
        int attrsEnd = lower.indexOf('>', open);
        if (attrsEnd < 0) {
            return null;
        }
        int close = lower.indexOf("</body", attrsEnd + 1);
        if (close < 0) {
            return null;
        }
        return html.substring(attrsEnd + 1, close);
    }

    /**
     * 去掉标签，并整体丢弃 script/style 的内容，等价于原 TAG_OR_SCRIPT_STYLE 的 replaceAll，
     * 但用线性扫描替代可回溯正则。
     */
    private static String stripTagsAndEmbeddedBlocks(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        StringBuilder visible = new StringBuilder(html.length());
        int i = 0;
        while (i < html.length()) {
            char c = html.charAt(i);
            if (c != '<') {
                visible.append(c);
                i += 1;
                continue;
            }
            String embedded = embeddedBlockAt(lower, i);
            if (embedded != null) {
                int attrsEnd = lower.indexOf('>', i);
                int close = attrsEnd < 0 ? -1 : lower.indexOf("</" + embedded, attrsEnd + 1);
                int closeEnd = close < 0 ? -1 : lower.indexOf('>', close);
                if (closeEnd >= 0) {
                    i = closeEnd + 1;
                    continue;
                }
                // 未闭合的 script/style：原正则会退化为匹配单个标签，行为一致。
            }
            int tagEnd = lower.indexOf('>', i);
            if (tagEnd < 0) {
                // 无闭合 '>' 的残缺标签不被原正则匹配，保留为可见文本。
                visible.append(html, i, html.length());
                return visible.toString().trim();
            }
            i = tagEnd + 1;
        }
        return visible.toString().trim();
    }

    private static String embeddedBlockAt(String lowerHtml, int position) {
        for (String tag : new String[]{"script", "style"}) {
            if (indexOfTag(lowerHtml, "<" + tag, position) == position) {
                return tag;
            }
        }
        return null;
    }

    public static boolean hasTruncatedScriptOrEllipsis(String raw) {
        for (String script : extractScripts(unwrap(raw))) {
            if (scriptContainsTruncationOrSevereUnbalance(script)) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern UNDEFINED_ROW_HTML_CALL = Pattern.compile(
            "\\b[a-zA-Z_$][\\w$]*\\.html\\s*\\("
    );

    private static final Pattern INTERACTIVE_DATA_ATTRIBUTE = Pattern.compile(
            "\\bdata-(?:act|action|a|view|v|tab)\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CLICK_WIRING = Pattern.compile(
            "(?:addEventListener\\s*\\(\\s*['\"]click['\"]|\\.onclick\\s*=|\\bonclick\\s*=)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GENERATION_PLACEHOLDER = Pattern.compile(
            "__(?:SEED|TODO|PLACEHOLDER|FILL)[A-Z0-9_]*__",
            Pattern.CASE_INSENSITIVE
    );

    public static boolean hasBrokenNestedEventHandlerQuotes(String raw) {
        for (String script : extractScripts(unwrap(raw))) {
            if (quotedStringBreaksOnEventHandler(script)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasUndefinedMethodCallHallucination(String raw) {
        for (String script : extractScripts(unwrap(raw))) {
            if (UNDEFINED_ROW_HTML_CALL.matcher(script).find()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasInteractiveControlsWithoutClickWiring(String raw) {
        String html = unwrap(raw);
        return INTERACTIVE_DATA_ATTRIBUTE.matcher(html).find()
                && !CLICK_WIRING.matcher(html).find();
    }

    public static boolean hasGenerationPlaceholders(String raw) {
        return GENERATION_PLACEHOLDER.matcher(unwrap(raw)).find();
    }

    public static boolean hasStubOrInertScript(String raw) {
        List<String> scripts = extractScripts(unwrap(raw));
        if (scripts.isEmpty()) {
            return false;
        }
        String combined = String.join("\n", scripts).trim();
        if (combined.isEmpty()) {
            return false;
        }
        String html = unwrap(raw);
        boolean hasControls = html.contains("<button") || html.contains("class='menu'") || html.contains("class=\"menu\"") || INTERACTIVE_DATA_ATTRIBUTE.matcher(html).find();
        if (hasControls) {
            boolean hasWiring = CLICK_WIRING.matcher(html).find();
            boolean hasFunctionsOrDom = combined.contains("function")
                    || combined.contains("=>")
                    || combined.contains("document.")
                    || combined.contains("window.")
                    || combined.contains("addEventListener");
            if (!hasWiring || !hasFunctionsOrDom) {
                return true;
            }
        }
        return false;
    }

    static List<String> extractScripts(String html) {
        List<String> scripts = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return scripts;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        int cursor = 0;
        while (true) {
            int open = indexOfTag(lower, "<script", cursor);
            if (open < 0) {
                return scripts;
            }
            int attrsEnd = lower.indexOf('>', open);
            if (attrsEnd < 0) {
                return scripts;
            }
            int close = lower.indexOf("</script", attrsEnd + 1);
            if (close < 0) {
                // 未闭合：原正则在此处会对每个起始标签回溯扫到串尾（O(n^2)），这里直接停止。
                return scripts;
            }
            int closeEnd = lower.indexOf('>', close);
            if (closeEnd < 0) {
                return scripts;
            }
            if (!lower.substring(open + "<script".length(), attrsEnd).contains("src=")) {
                scripts.add(html.substring(attrsEnd + 1, close));
            }
            cursor = closeEnd + 1;
        }
    }

    /** 定位 {@code <tag}，要求其后紧跟非单词字符，等价于原正则的 {@code \b} 边界。 */
    private static int indexOfTag(String lowerHtml, String lowerTag, int from) {
        int i = from;
        while (i <= lowerHtml.length() - lowerTag.length()) {
            int hit = lowerHtml.indexOf(lowerTag, i);
            if (hit < 0) {
                return -1;
            }
            int after = hit + lowerTag.length();
            if (after >= lowerHtml.length()) {
                return -1;
            }
            char next = lowerHtml.charAt(after);
            if (!Character.isLetterOrDigit(next) && next != '_') {
                return hit;
            }
            i = hit + 1;
        }
        return -1;
    }

    static boolean scriptContainsTruncationOrSevereUnbalance(String script) {
        if (script == null || script.isEmpty()) {
            return false;
        }
        if (ELLIPSIS_TRUNCATION.matcher(script).find()) {
            return true;
        }
        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
        int i = 0;
        int n = script.length();
        while (i < n) {
            int skipped = skipCommentOrRegex(script, i);
            if (skipped >= 0) {
                i = skipped;
                continue;
            }
            char c = script.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuoted(script, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(script, i);
                continue;
            }
            if (c == '(' || c == '{' || c == '[') {
                stack.push(c);
            } else if (c == ')') {
                if (stack.isEmpty() || stack.pop() != '(') {
                    return true;
                }
            } else if (c == '}') {
                if (stack.isEmpty() || stack.pop() != '{') {
                    return true;
                }
            } else if (c == ']') {
                if (stack.isEmpty() || stack.pop() != '[') {
                    return true;
                }
            }
            i += 1;
        }
        return !stack.isEmpty();
    }

    static boolean quotedStringBreaksOnEventHandler(String script) {
        if (script == null || script.isEmpty()) {
            return false;
        }
        int i = 0;
        int n = script.length();
        while (i < n) {
            int skipped = skipCommentOrRegex(script, i);
            if (skipped >= 0) {
                i = skipped;
                continue;
            }
            char c = script.charAt(i);
            if (c == '\'' || c == '"') {
                if (stringContainsBrokenHandler(script, i, c)) {
                    return true;
                }
                i = skipQuoted(script, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(script, i);
                continue;
            }
            i += 1;
        }
        return false;
    }

    private static boolean stringContainsBrokenHandler(String script, int open, char quote) {
        StringBuilder content = new StringBuilder();
        int i = open + 1;
        while (i < script.length()) {
            char c = script.charAt(i);
            if (c == '\\') {
                content.append(c);
                if (i + 1 < script.length()) {
                    content.append(script.charAt(i + 1));
                    i += 2;
                    continue;
                }
                break;
            }
            if (c == quote) {
                return false;
            }
            content.append(c);
            if (c == '(' && matchesHandlerStartAtTail(content)) {
                int next = i + 1;
                while (next < script.length() && Character.isWhitespace(script.charAt(next))) {
                    next += 1;
                }
                if (next < script.length() && script.charAt(next) == quote
                        && !followedByConcatenation(script, next + 1)) {
                    return true;
                }
            }
            i += 1;
        }
        return false;
    }

    /**
     * EVENT_HANDLER_START 以 {@code $} 结尾，只可能命中缓冲区末尾的少量字符，
     * 因此只在尾部窗口内匹配，避免每遇 '(' 就重扫整个缓冲区。
     */
    private static boolean matchesHandlerStartAtTail(StringBuilder content) {
        int from = Math.max(0, content.length() - HANDLER_TAIL_WINDOW);
        return EVENT_HANDLER_START.matcher(content.subSequence(from, content.length())).find();
    }

    private static boolean followedByConcatenation(String script, int from) {
        int i = from;
        while (i < script.length() && Character.isWhitespace(script.charAt(i))) {
            i += 1;
        }
        return i < script.length() && script.charAt(i) == '+';
    }

    /**
     * 跳过行/块注释或正则字面量。无法判定时返回 -1，由调用方按普通 {@code /}（除法）处理。
     * 否则 {@code replace(/"/g, ...)} 会被当成字符串起点，整段脚本被误判为括号未闭合。
     */
    private static int skipCommentOrRegex(String script, int i) {
        if (i >= script.length() || script.charAt(i) != '/') {
            return -1;
        }
        if (i + 1 >= script.length()) {
            return -1;
        }
        char next = script.charAt(i + 1);
        if (next == '/') {
            int nl = script.indexOf('\n', i + 2);
            return nl < 0 ? script.length() : nl + 1;
        }
        if (next == '*') {
            int end = script.indexOf("*/", i + 2);
            return end < 0 ? script.length() : end + 2;
        }
        if (regexLikely(script, i)) {
            return skipRegexLiteral(script, i);
        }
        return -1;
    }

    private static boolean regexLikely(String script, int slash) {
        int j = slash - 1;
        while (j >= 0 && Character.isWhitespace(script.charAt(j))) {
            j--;
        }
        if (j < 0) {
            return true;
        }
        char prev = script.charAt(j);
        if (prev == ')' || prev == ']' || prev == '"' || prev == '\'' || prev == '`') {
            return false;
        }
        if (Character.isLetterOrDigit(prev) || prev == '_' || prev == '$') {
            int start = j;
            while (start >= 0) {
                char ch = script.charAt(start);
                if (!(Character.isLetterOrDigit(ch) || ch == '_' || ch == '$')) {
                    break;
                }
                start--;
            }
            String word = script.substring(start + 1, j + 1);
            return REGEX_PREFIX_KEYWORDS.contains(word);
        }
        return true;
    }

    private static int skipRegexLiteral(String script, int open) {
        int i = open + 1;
        boolean inClass = false;
        while (i < script.length()) {
            char c = script.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '[' && !inClass) {
                inClass = true;
                i += 1;
                continue;
            }
            if (c == ']' && inClass) {
                inClass = false;
                i += 1;
                continue;
            }
            if (c == '/' && !inClass) {
                i += 1;
                while (i < script.length()) {
                    char flag = script.charAt(i);
                    if (flag >= 'a' && flag <= 'z') {
                        i += 1;
                        continue;
                    }
                    break;
                }
                return i;
            }
            if (c == '\n' && !inClass) {
                return i;
            }
            i += 1;
        }
        return script.length();
    }

    private static int skipQuoted(String script, int open, char quote) {
        int i = open + 1;
        while (i < script.length()) {
            char c = script.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i += 1;
        }
        return script.length();
    }

    private static int skipTemplate(String script, int open) {
        int i = open + 1;
        while (i < script.length()) {
            char c = script.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '`') {
                return i + 1;
            }
            if (c == '$' && i + 1 < script.length() && script.charAt(i + 1) == '{') {
                i = skipTemplateExpression(script, i + 2);
                continue;
            }
            i += 1;
        }
        return script.length();
    }

    private static int skipTemplateExpression(String script, int open) {
        int depth = 1;
        int i = open;
        while (i < script.length() && depth > 0) {
            int skipped = skipCommentOrRegex(script, i);
            if (skipped >= 0) {
                i = skipped;
                continue;
            }
            char c = script.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuoted(script, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(script, i);
                continue;
            }
            if (c == '{') {
                depth += 1;
            } else if (c == '}') {
                depth -= 1;
                if (depth == 0) {
                    return i + 1;
                }
            }
            i += 1;
        }
        return i;
    }
}
