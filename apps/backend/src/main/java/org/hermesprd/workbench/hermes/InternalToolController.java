package org.hermesprd.workbench.hermes;

import org.hermesprd.workbench.task.ApiModels.SourcePageResponse;
import org.hermesprd.workbench.task.ApiModels.SubmitResultRequest;
import org.hermesprd.workbench.task.ApiModels.ToolSourceListResponse;
import org.hermesprd.workbench.task.RunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/runs/{runId}")
public class InternalToolController {
    private final ToolService toolService;
    private final RunService runService;

    public InternalToolController(ToolService toolService, RunService runService) {
        this.toolService = toolService;
        this.runService = runService;
    }

    @GetMapping("/sources")
    public ToolSourceListResponse listSources(@PathVariable String runId) {
        return new ToolSourceListResponse(runId, toolService.listSources(runId));
    }

    @GetMapping("/sources/{sourceId}")
    public SourcePageResponse readSource(
            @PathVariable String runId,
            @PathVariable String sourceId,
            @RequestParam(required = false) String cursor
    ) {
        return toolService.readSource(runId, sourceId, cursor);
    }

    @PostMapping("/result")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> submit(@PathVariable String runId, @Valid @RequestBody SubmitResultRequest request) {
        boolean completed = runService.acceptResult(
                runId,
                request.mode(),
                request.payload(),
                request.partIndex(),
                request.totalParts(),
                request.isFinal()
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accepted", true);
        if (!completed) {
            body.put("status", "CONTINUE");
            body.put("message", "分段内容已接收暂存，请继续生成后续部分并提交最终结果");
        } else {
            body.put("status", "SUCCEEDED");
        }
        return body;
    }

    @PostMapping("/workspace/files")
    public Map<String, Object> writeWorkspaceFile(
            @PathVariable String runId,
            @RequestBody Map<String, Object> body
    ) {
        String filePath = body != null && body.get("filePath") != null ? String.valueOf(body.get("filePath")) : null;
        String content = body != null && body.get("content") != null ? String.valueOf(body.get("content")) : "";
        return runService.writeWorkspaceFile(runId, filePath, content);
    }

    @GetMapping("/workspace/files")
    public Map<String, Object> readWorkspaceFile(
            @PathVariable String runId,
            @RequestParam(name = "filePath", required = false) String filePath
    ) {
        if (filePath == null || filePath.isBlank()) {
            return runService.listWorkspaceFiles(runId);
        }
        return runService.readWorkspaceFile(runId, filePath);
    }
}
