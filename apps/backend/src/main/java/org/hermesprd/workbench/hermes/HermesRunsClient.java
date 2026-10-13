package org.hermesprd.workbench.hermes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.SecretFiles;
import org.hermesprd.workbench.shared.WorkbenchProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HermesRunsClient {
    private final WorkbenchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HermesRunsClient(WorkbenchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.hermes().connectTimeout())
                .build();
    }

    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.hermes().baseUrl() + "/health"))
                    .timeout(properties.hermes().connectTimeout())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            return false;
        }
    }

    public String createRun(String sessionId, String input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);
        body.put("session_id", sessionId);
        JsonNode response = send("POST", "/v1/runs", body, 202);
        JsonNode runId = response.get("run_id");
        if (runId == null || runId.asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "HERMES_UNAVAILABLE", "分析服务未返回任务编号");
        }
        return runId.asText();
    }

    public JsonNode getRun(String hermesRunId) {
        return send("GET", "/v1/runs/" + hermesRunId, null, 200, false);
    }

    public JsonNode stopRun(String hermesRunId) {
        return send("POST", "/v1/runs/" + hermesRunId + "/stop", Map.of(), 200, true);
    }

    private JsonNode send(String method, String path, Map<String, Object> body, int expectedStatus) {
        return send(method, path, body, expectedStatus, false);
    }

    private JsonNode send(String method, String path, Map<String, Object> body, int expectedStatus, boolean allowNotFound) {
        try {
            String apiKey = SecretFiles.readRequired(properties.hermes().apiKeyFile(), "hermes.api-key-file");
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.hermes().baseUrl() + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json");
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            } else if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 401) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "HERMES_UNAVAILABLE", "分析服务认证失败");
            }
            if (response.statusCode() == 404) {
                if (allowNotFound) {
                    return objectMapper.createObjectNode();
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY, "HERMES_UNAVAILABLE", "分析任务不存在或已被清理");
            }
            if (response.statusCode() != expectedStatus && !(expectedStatus == 202 && response.statusCode() == 200)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "HERMES_UNAVAILABLE", "分析服务返回 HTTP " + response.statusCode());
            }
            if (response.body() == null || response.body().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "HERMES_UNAVAILABLE", "分析服务返回空响应");
            }
            return objectMapper.readTree(response.body());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "HERMES_UNAVAILABLE", "无法连接分析服务");
        }
    }
}
