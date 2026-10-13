package org.hermesprd.workbench.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface RunMapper extends BaseMapper<RunEntity> {
    @Select("""
            SELECT mode AS module, COALESCE(error_code, 'UNKNOWN') AS errorCode,
                   COUNT(*) AS count, MAX(finished_at) AS lastOccurredAt
            FROM agent_run
            WHERE status = 'FAILED'
              AND mode IN ('GENERATE', 'POC', 'TEST_CASE', 'TECH_SPEC', 'REVIEW')
              AND finished_at >= #{since}
            GROUP BY mode, COALESCE(error_code, 'UNKNOWN')
            ORDER BY count DESC, lastOccurredAt DESC
            """)
    List<Map<String, Object>> aggregateMonitoredFailures(@Param("since") Instant since);

    @Update("""
            UPDATE agent_run
            SET review_feedback = CAST(#{feedbackJson} AS JSON),
                review_feedback_revision = review_feedback_revision + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{runId}
              AND mode = 'REVIEW'
              AND status = 'SUCCEEDED'
              AND review_feedback_revision = #{expectedRevision}
            """)
    int updateReviewFeedback(
            @Param("runId") String runId,
            @Param("feedbackJson") String feedbackJson,
            @Param("expectedRevision") int expectedRevision
    );
}
