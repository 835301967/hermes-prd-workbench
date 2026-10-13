package org.hermesprd.workbench.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.result.ResultEntity;
import org.hermesprd.workbench.result.ResultMapper;
import org.hermesprd.workbench.shared.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewFeedbackServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getReturnsEmptyRevisionZeroAndHidesNonReviewRuns() {
        Fixture fixture = fixture();
        RunEntity review = reviewRun("run_1", "SUCCEEDED", 0);
        RunEntity generate = reviewRun("run_generate", "SUCCEEDED", 0);
        generate.setMode("GENERATE");
        when(fixture.runMapper.selectById("run_1")).thenReturn(review);
        when(fixture.runMapper.selectById("run_generate")).thenReturn(generate);

        ApiModels.ReviewFeedbackResponse response = fixture.service.get("user_1", "run_1");
        ApiException hidden = assertThrows(ApiException.class,
                () -> fixture.service.get("user_1", "run_generate"));

        assertEquals(0, response.revision());
        assertTrue(response.items().isEmpty());
        assertEquals(404, hidden.status().value());
    }

    @Test
    void savesTypedFeedbackAndIncrementsRevision() throws Exception {
        Fixture fixture = fixture();
        RunEntity run = reviewRun("run_1", "SUCCEEDED", 0);
        when(fixture.runMapper.selectById("run_1")).thenReturn(run);
        when(fixture.resultMapper.selectOne(any())).thenReturn(result("run_1", reviewPayload(2, 2)));
        when(fixture.runMapper.updateReviewFeedback(eq("run_1"), any(), eq(0))).thenReturn(1);
        List<ApiModels.ReviewFeedbackItem> items = List.of(
                new ApiModels.ReviewFeedbackItem("blockingIssues/0", "EDITED", null),
                new ApiModels.ReviewFeedbackItem("otherSuggestions/1", "NEEDS_CUSTOMER", "确认审批人\n范围")
        );

        ApiModels.ReviewFeedbackResponse response = fixture.service.update(
                "user_1", "run_1", new ApiModels.UpdateReviewFeedbackRequest(0, items)
        );

        assertEquals(1, response.revision());
        assertEquals(items, response.items());
        verify(fixture.taskService).requireNoActiveRun("task_1");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(fixture.runMapper).updateReviewFeedback(eq("run_1"), json.capture(), eq(0));
        JsonNode stored = objectMapper.readTree(json.getValue());
        assertFalse(stored.path("items").path(0).has("note"));
        assertEquals("确认审批人\n范围", stored.path("items").path(1).path("note").asText());
        assertFalse(stored.has("expectedRevision"));
    }

    @Test
    void rejectsMissingDecisionNoteAndOutOfRangePathWithoutWriting() {
        Fixture fixture = fixture();
        when(fixture.runMapper.selectById("run_1")).thenReturn(reviewRun("run_1", "SUCCEEDED", 0));
        when(fixture.resultMapper.selectOne(any())).thenReturn(result("run_1", reviewPayload(1, 0)));

        ApiException missingNote = assertThrows(ApiException.class, () -> fixture.service.update(
                "user_1", "run_1", new ApiModels.UpdateReviewFeedbackRequest(0, List.of(
                        new ApiModels.ReviewFeedbackItem("blockingIssues/0", "WONT_DO", null)
                ))
        ));
        ApiException badPath = assertThrows(ApiException.class, () -> fixture.service.update(
                "user_1", "run_1", new ApiModels.UpdateReviewFeedbackRequest(0, List.of(
                        new ApiModels.ReviewFeedbackItem("blockingIssues/1", "EDITED", null)
                ))
        ));

        assertEquals("INVALID_REQUEST", missingNote.code());
        assertEquals("INVALID_REQUEST", badPath.code());
        verify(fixture.runMapper, never()).updateReviewFeedback(any(), any(), anyInt());
    }

    @Test
    void rejectsDuplicateItemPathsWithoutWriting() {
        Fixture fixture = fixture();
        when(fixture.runMapper.selectById("run_1")).thenReturn(reviewRun("run_1", "SUCCEEDED", 0));
        when(fixture.resultMapper.selectOne(any())).thenReturn(result("run_1", reviewPayload(1, 0)));

        ApiException error = assertThrows(ApiException.class, () -> fixture.service.update(
                "user_1", "run_1", new ApiModels.UpdateReviewFeedbackRequest(0, List.of(
                        new ApiModels.ReviewFeedbackItem("blockingIssues/0", "EDITED", null),
                        new ApiModels.ReviewFeedbackItem("blockingIssues/0", "WONT_DO", "重复决定")
                ))
        ));

        assertEquals(400, error.status().value());
        assertTrue(error.getMessage().contains("不能重复"));
        verify(fixture.runMapper, never()).updateReviewFeedback(any(), any(), anyInt());
    }

    @Test
    void rejectsSerializedRequestOverOneHundredKilobytes() {
        Fixture fixture = fixture();
        when(fixture.runMapper.selectById("run_1")).thenReturn(reviewRun("run_1", "SUCCEEDED", 0));
        when(fixture.resultMapper.selectOne(any())).thenReturn(result("run_1", reviewPayload(20, 0)));
        String note = "中".repeat(2_000);
        List<ApiModels.ReviewFeedbackItem> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add(new ApiModels.ReviewFeedbackItem("blockingIssues/" + i, "EDITED", note));
        }

        ApiException error = assertThrows(ApiException.class, () -> fixture.service.update(
                "user_1", "run_1", new ApiModels.UpdateReviewFeedbackRequest(0, items)
        ));

        assertEquals(400, error.status().value());
        assertEquals("INVALID_REQUEST", error.code());
        verify(fixture.runMapper, never()).updateReviewFeedback(any(), any(), anyInt());
    }

    @Test
    void reportsRevisionConflict() {
        Fixture fixture = fixture();
        when(fixture.runMapper.selectById("run_1")).thenReturn(reviewRun("run_1", "SUCCEEDED", 3));
        when(fixture.resultMapper.selectOne(any())).thenReturn(result("run_1", reviewPayload(1, 0)));
        when(fixture.runMapper.updateReviewFeedback(eq("run_1"), any(), eq(2))).thenReturn(0);

        ApiException error = assertThrows(ApiException.class, () -> fixture.service.update(
                "user_1", "run_1", new ApiModels.UpdateReviewFeedbackRequest(2, List.of(
                        new ApiModels.ReviewFeedbackItem("blockingIssues/0", "EDITED", null)
                ))
        ));

        assertEquals(409, error.status().value());
        assertEquals("FEEDBACK_CHANGED", error.code());
    }

    @Test
    void activeRunBlocksFeedbackBeforeAnyResultOrJsonWrite() {
        Fixture fixture = fixture();
        when(fixture.runMapper.selectById("run_1")).thenReturn(reviewRun("run_1", "SUCCEEDED", 0));
        doThrow(new ApiException(org.springframework.http.HttpStatus.CONFLICT, "RUN_NOT_ACTIVE", "运行中"))
                .when(fixture.taskService).requireNoActiveRun("task_1");

        ApiException error = assertThrows(ApiException.class, () -> fixture.service.update(
                "user_1", "run_1", new ApiModels.UpdateReviewFeedbackRequest(0, List.of())
        ));

        assertEquals("RUN_NOT_ACTIVE", error.code());
        verify(fixture.resultMapper, never()).selectOne(any());
        verify(fixture.runMapper, never()).updateReviewFeedback(any(), any(), anyInt());
    }

    @Test
    void rendersRecentFeedbackAsBoundedUserContext() {
        Fixture fixture = fixture();
        RunEntity current = reviewRun("run_current", "RUNNING", 0);
        current.setCreatedAt(Instant.parse("2026-08-26T03:00:00Z"));
        RunEntity previous = reviewRun("run_previous", "SUCCEEDED", 1);
        previous.setCreatedAt(Instant.parse("2026-08-26T02:00:00Z"));
        previous.setReviewFeedback("""
                {"items":[
                  {"itemPath":"blockingIssues/0","action":"WONT_DO","note":"客户已明确不做"},
                  {"itemPath":"otherSuggestions/0","action":"EDITED","note":"已补充规则"}
                ]}
                """);
        when(fixture.runMapper.selectList(any())).thenReturn(List.of(previous));
        when(fixture.resultMapper.selectOne(any())).thenReturn(result("run_previous", reviewPayload(1, 1)));

        String context = fixture.service.historyContext(current);

        assertTrue(context.contains("用户在历史评审中的处理记录"));
        assertTrue(context.contains("阻断问题 0"));
        assertTrue(context.contains("处理：不做"));
        assertTrue(context.contains("  > 客户已明确不做"));
        assertTrue(context.contains("请以当前材料为准"));
        assertTrue(context.length() <= 20_000);
    }

    private Fixture fixture() {
        RunMapper runMapper = mock(RunMapper.class);
        ResultMapper resultMapper = mock(ResultMapper.class);
        TaskService taskService = mock(TaskService.class);
        when(taskService.requireOwned("user_1", "task_1")).thenReturn(new TaskEntity());
        return new Fixture(
                new ReviewFeedbackService(runMapper, resultMapper, taskService, objectMapper, new TaskMutationGate()),
                runMapper,
                resultMapper,
                taskService
        );
    }

    private ResultEntity result(String runId, String payload) {
        ResultEntity result = new ResultEntity();
        result.setRunId(runId);
        result.setPayload(payload);
        return result;
    }

    private String reviewPayload(int blockingCount, int suggestionCount) {
        StringBuilder json = new StringBuilder("{\"mode\":\"REVIEW\",\"title\":\"评审\",\"summary\":\"摘要\",\"blockingIssues\":[");
        for (int i = 0; i < blockingCount; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"title\":\"阻断问题 ").append(i).append("\"}");
        }
        json.append("],\"otherSuggestions\":[");
        for (int i = 0; i < suggestionCount; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"title\":\"建议 ").append(i).append("\"}");
        }
        return json.append("]}").toString();
    }

    private RunEntity reviewRun(String id, String status, int revision) {
        RunEntity run = new RunEntity();
        run.setId(id);
        run.setTaskId("task_1");
        run.setMode("REVIEW");
        run.setStatus(status);
        run.setReviewFeedbackRevision(revision);
        return run;
    }

    private record Fixture(
            ReviewFeedbackService service,
            RunMapper runMapper,
            ResultMapper resultMapper,
            TaskService taskService
    ) {
    }
}
