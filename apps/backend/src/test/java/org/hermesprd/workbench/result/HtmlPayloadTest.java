package org.hermesprd.workbench.result;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlPayloadTest {
    @Test
    void unwrapsMarkdownFence() {
        String html = HtmlPayload.unwrap("```html\n<!DOCTYPE html><html><head></head><body>ok</body></html>\n```");
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertFalse(html.startsWith("```"));
    }

    @Test
    void detectsBrokenNestedQuotesInsideSingleQuotedInnerHtml() {
        String html = """
                <!DOCTYPE html><html><body>
                <main id="main"></main>
                <script>
                $('main').innerHTML='<button onclick="goMod('alert')">预警</button>';
                </script>
                </body></html>
                """;
        assertTrue(HtmlPayload.hasBrokenNestedEventHandlerQuotes(html));
        assertTrue(HtmlPayload.quotedStringBreaksOnEventHandler(
                "$('main').innerHTML='<button onclick=\"goMod('alert')\">预警</button>';"
        ));
    }

    @Test
    void acceptsTemplateLiteralWithQuotedHandlerArgs() {
        String html = """
                <!DOCTYPE html><html><body>
                <main id="main"></main>
                <script>
                $('main').innerHTML=`<button onclick="goMod('alert')">预警</button>`;
                </script>
                </body></html>
                """;
        assertFalse(HtmlPayload.hasBrokenNestedEventHandlerQuotes(html));
    }

    @Test
    void acceptsDelegatedHandlersWithoutInlineArgs() {
        String html = """
                <!DOCTYPE html><html><body>
                <button data-view="alert">预警</button>
                <script>
                document.addEventListener('click', (e) => {
                  const btn = e.target.closest('[data-view]');
                  if (btn) goMod(btn.dataset.view);
                });
                </script>
                </body></html>
                """;
        assertFalse(HtmlPayload.hasBrokenNestedEventHandlerQuotes(html));
    }

    @Test
    void acceptsNoArgHandlersInsideSingleQuotedInnerHtml() {
        String html = """
                <!DOCTYPE html><html><body>
                <script>
                $('main').innerHTML='<button onclick="newApply()">申请</button>';
                </script>
                </body></html>
                """;
        assertFalse(HtmlPayload.hasBrokenNestedEventHandlerQuotes(html));
    }

    @Test
    void acceptsConcatenatedHandlerArgsInsideSingleQuotedInnerHtml() {
        String html = """
                <!DOCTYPE html><html><body>
                <script>
                openSheet('<button onclick="clearAlert('+id+')">登记处理</button>');
                </script>
                </body></html>
                """;
        assertFalse(HtmlPayload.hasBrokenNestedEventHandlerQuotes(html));
    }

    @Test
    void detectsTruncatedScriptWithEllipsis() {
        String html = """
                <!DOCTYPE html><html><body>
                <h1>广东安捷伦 ERP 系统</h1><p>用车申请单管理与流转核算</p>
                <script>
                function rmbUpper(n) {
                  var s = '人民币' + ...;
                }
                </script>
                </body></html>
                """;
        assertTrue(HtmlPayload.hasTruncatedScriptOrEllipsis(html));
    }

    @Test
    void detectsTruncatedScriptWithSevereUnclosedBraces() {
        String html = """
                <!DOCTYPE html><html><body>
                <h1>广东安捷伦 ERP 系统</h1><p>用车申请单管理与流转核算</p>
                <script>
                function approve() {
                  if (status === 'DRAFT') {
                    for (var i = 0; i < 5; i++) {
                      doSomething();
                }
                </script>
                </body></html>
                """;
        assertTrue(HtmlPayload.hasTruncatedScriptOrEllipsis(html));
    }

    @Test
    void detectsMismatchedClosingParenthesisInsideBlock() {
        String html = """
                <!DOCTYPE html><html><body>
                <main><h1>系统</h1></main>
                <script>
                function openForm() {
                  items.forEach(function(o) {
                    h += '<option>' + o + '</option>');
                  });
                }
                </script>
                </body></html>
                """;
        assertTrue(HtmlPayload.hasTruncatedScriptOrEllipsis(html));
    }

    @Test
    void acceptsReplaceRegexThatLooksLikeAQuote() {
        String html = """
                <!DOCTYPE html><html><body>
                <div class="app">示例CRM客户档案管理工作台</div>
                <script>
                (function () {
                  function esc(v) {
                    return String(v == null ? '' : v)
                      .replace(/&/g, '&amp;')
                      .replace(/</g, '&lt;')
                      .replace(/>/g, '&gt;')
                      .replace(/"/g, '&quot;');
                  }
                  window.esc = esc;
                })();
                </script>
                </body></html>
                """;
        assertFalse(HtmlPayload.hasTruncatedScriptOrEllipsis(html));
        assertFalse(HtmlPayload.hasBrokenNestedEventHandlerQuotes(html));
    }

    @Test
    void stillTreatsDivisionSlashAsNotARegex() {
        String html = """
                <!DOCTYPE html><html><body>
                <div class="app">示例CRM工作台</div>
                <script>
                function ratio(a, b) {
                  return a / b;
                }
                </script>
                </body></html>
                """;
        assertFalse(HtmlPayload.hasTruncatedScriptOrEllipsis(html));
    }

    @Test
    void detectsEmptyBodySkeletonWithoutSubstantiveContent() {
        String html = """
                <!DOCTYPE html><html><body>
                <aside id="sidebar"></aside>
                <main id="main"></main>
                <script>
                console.log('empty');
                </script>
                </body></html>
                """;
        assertFalse(HtmlPayload.hasSubstantiveBodyContent(html));
    }

    @Test
    void acceptsCompleteDocumentWithSubstantiveContent() {
        String html = """
                <!DOCTYPE html><html><body>
                <header class="topbar"><h1>广东安捷伦新材料 ERP 行政管理</h1></header>
                <aside class="sidebar"><nav><a href="#">用车申请</a></nav></aside>
                <main class="main">
                  <h2>用车单据 CR-20260908-0012</h2>
                  <table><thead><tr><th>序号</th><th>起运地</th><th>目的地</th></tr></thead>
                  <tbody><tr><td>01</td><td>安捷伦研发楼</td><td>松山湖实验室</td></tr></tbody></table>
                </main>
                <script>
                document.addEventListener('DOMContentLoaded', function() {
                  console.log('ready');
                });
                </script>
                </body></html>
                """;
        assertTrue(HtmlPayload.looksLikeHtmlDocument(html));
        assertTrue(HtmlPayload.hasSubstantiveBodyContent(html));
        assertFalse(HtmlPayload.hasTruncatedScriptOrEllipsis(html));
        assertFalse(HtmlPayload.hasBrokenNestedEventHandlerQuotes(html));
    }

    @Test
    void detectsInteractiveControlsWithoutClickWiring() {
        String inert = """
                <!DOCTYPE html><html><body>
                <button data-act="create">新增</button>
                <script>var ORDERS = [];</script>
                </body></html>
                """;
        String wired = """
                <!DOCTYPE html><html><body>
                <button data-act="create">新增</button>
                <script>document.addEventListener('click', function () {});</script>
                </body></html>
                """;

        assertTrue(HtmlPayload.hasInteractiveControlsWithoutClickWiring(inert));
        assertFalse(HtmlPayload.hasInteractiveControlsWithoutClickWiring(wired));
    }

    @Test
    void detectsUnresolvedGenerationPlaceholders() {
        String html = "<!DOCTYPE html><html><body><script>var rows = [[\"__SEED_ROWS__\"]];</script></body></html>";

        assertTrue(HtmlPayload.hasGenerationPlaceholders(html));
        assertFalse(HtmlPayload.hasGenerationPlaceholders(html.replace("__SEED_ROWS__", "SO-001")));
    }

    @Test
    void acceptsEs6SpreadAndRestSyntax() {
        String[] legitimate = {
                "function f(...args){ return args; }",
                "var a=[...items];",
                "var o={...obj, id:1};",
                "var m=Math.max(...nums);",
                "f(...a, b);",
                "var rows=[...document.querySelectorAll('tr')];",
                "const [first, ...rest] = list;",
                "const merged={...base, ...override};",
                "fn(...`tpl`);",
                "g(...'ab');"
        };
        for (String script : legitimate) {
            assertFalse(
                    HtmlPayload.scriptContainsTruncationOrSevereUnbalance(script),
                    "合法展开/剩余语法被误判为截断: " + script
            );
        }
    }

    @Test
    void stillDetectsRealEllipsisTruncation() {
        String[] truncated = {
                "var s='x' + ...;",
                "var items=[ ... ];",
                "obj = { ... }",
                "total = a + ... * b",
                "var list=[1,2,...",
                "render(...\n",
                "var x= ...;"
        };
        for (String script : truncated) {
            assertTrue(
                    HtmlPayload.scriptContainsTruncationOrSevereUnbalance(script),
                    "真实截断未被拦截: " + script
            );
        }
    }

    @Test
    void unclosedScriptTagsDoNotTriggerQuadraticScan() {
        // 被截断的 LLM 输出正是本类要检测的场景，不应因回溯导致耗时随长度平方增长。
        String head = "<!DOCTYPE html><html><head></head><body>hello world padding text here</body>";
        String small = head + "<script>".repeat(4_000) + "</html>";
        String large = head + "<script>".repeat(16_000) + "</html>";

        long smallNanos = timeTruncationScan(small);
        long largeNanos = timeTruncationScan(large);

        // 线性实现下 4 倍输入约 4 倍耗时；给足余量，二次方实现会到约 16 倍。
        assertTrue(
                largeNanos < smallNanos * 8 + Duration.ofMillis(50).toNanos(),
                "未闭合 script 扫描疑似仍是二次方: small=" + smallNanos + "ns large=" + largeNanos + "ns"
        );
        assertTrue(
                Duration.ofNanos(largeNanos).toMillis() < 1_000,
                "128K 未闭合 script 扫描超过 1 秒: " + Duration.ofNanos(largeNanos).toMillis() + "ms"
        );
    }

    @Test
    void manyOpenParensInsideStringDoNotTriggerQuadraticScan() {
        String head = "<!DOCTYPE html><html><body>hello world padding text here";
        String small = head + "<script>var s='" + "(".repeat(10_000) + "</script></body></html>";
        String large = head + "<script>var s='" + "(".repeat(40_000) + "</script></body></html>";

        long smallNanos = timeHandlerScan(small);
        long largeNanos = timeHandlerScan(large);

        assertTrue(
                largeNanos < smallNanos * 8 + Duration.ofMillis(50).toNanos(),
                "事件处理器扫描疑似仍是二次方: small=" + smallNanos + "ns large=" + largeNanos + "ns"
        );
        assertTrue(
                Duration.ofNanos(largeNanos).toMillis() < 1_000,
                "40K 左括号扫描超过 1 秒: " + Duration.ofNanos(largeNanos).toMillis() + "ms"
        );
    }

    private static long timeTruncationScan(String html) {
        HtmlPayload.hasTruncatedScriptOrEllipsis(html);
        long start = System.nanoTime();
        HtmlPayload.hasTruncatedScriptOrEllipsis(html);
        return System.nanoTime() - start;
    }

    @Test
    void detectsUndefinedMethodCallHallucination() {
        String html = """
                <!DOCTYPE html><html><body>
                <script>
                function rows(arr) {
                  var h = '';
                  for (var i = 0; i < arr.length; i++) {
                    var r = arr[i];
                    h += r.html(i);
                  }
                  return h;
                }
                </script>
                </body></html>
                """;
        assertTrue(HtmlPayload.hasUndefinedMethodCallHallucination(html));
    }

    @Test
    void acceptsValidRenderCallbackPattern() {
        String html = """
                <!DOCTYPE html><html><body>
                <script>
                function rows(arr, s, fn) {
                  var h = '';
                  for (var i = 0; i < arr.length; i++) {
                    h += fn(i);
                  }
                  return h;
                }
                </script>
                </body></html>
                """;
        assertFalse(HtmlPayload.hasUndefinedMethodCallHallucination(html));
    }

    @Test
    void detectsStubOrInertScript() {
        String stubHtml = """
                <!DOCTYPE html><html><body>
                <button class="menu">用车申请</button>
                <script>
                var CARS = [["粤A12345", "张三"]];
                </script>
                </body></html>
                """;
        assertTrue(HtmlPayload.hasStubOrInertScript(stubHtml));

        String activeHtml = """
                <!DOCTYPE html><html><body>
                <button class="menu">用车申请</button>
                <script>
                document.addEventListener('click', function(e) {
                  console.log('clicked');
                });
                </script>
                </body></html>
                """;
        assertFalse(HtmlPayload.hasStubOrInertScript(activeHtml));
    }

    private static long timeHandlerScan(String html) {
        HtmlPayload.hasBrokenNestedEventHandlerQuotes(html);
        long start = System.nanoTime();
        HtmlPayload.hasBrokenNestedEventHandlerQuotes(html);
        return System.nanoTime() - start;
    }
}
