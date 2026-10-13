package org.hermesprd.workbench.telemetry;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface TelemetryEventMapper extends BaseMapper<TelemetryEventEntity> {

    @Select("""
        SELECT module, action, count(*) as count,
               COALESCE(AVG(duration_ms), 0) as avgDuration,
               SUM(CASE WHEN status IN ('SUCCESS', 'SUCCEEDED') THEN 1 ELSE 0 END) as successCount,
               SUM(CASE WHEN status NOT IN ('SUCCESS', 'SUCCEEDED') THEN 1 ELSE 0 END) as failCount
        FROM prd_telemetry_event
        WHERE created_at >= #{since}
        GROUP BY module, action
        ORDER BY count DESC
    """)
    List<Map<String, Object>> aggregateFeatureUsage(@Param("since") Instant since);

    @Select("""
        SELECT module, error_code as errorCode, count(*) as count,
               MAX(created_at) as lastOccurredAt
        FROM prd_telemetry_event
        WHERE status NOT IN ('SUCCESS', 'SUCCEEDED') AND error_code IS NOT NULL AND created_at >= #{since}
        GROUP BY module, error_code
        ORDER BY count DESC
        LIMIT 20
    """)
    List<Map<String, Object>> aggregateErrors(@Param("since") Instant since);

    @Select("""
        SELECT u.id as userId, u.username, u.display_name as displayName, u.role, u.created_at as registeredAt,
               (SELECT MAX(e.created_at) FROM prd_telemetry_event e WHERE e.user_id = u.id) as lastActiveAt,
               (SELECT COUNT(*) FROM prd_task t WHERE t.user_id = u.id) as taskCount,
               (SELECT COUNT(*) FROM agent_run r JOIN prd_task t ON r.task_id = t.id WHERE t.user_id = u.id) as runCount,
               (SELECT COUNT(*) FROM agent_run r JOIN prd_task t ON r.task_id = t.id
                WHERE t.user_id = u.id AND r.status = 'FAILED'
                  AND r.mode IN ('GENERATE', 'POC', 'TEST_CASE', 'TECH_SPEC', 'REVIEW')) as errorCount
        FROM prd_user u
        ORDER BY lastActiveAt DESC, registeredAt DESC
    """)
    List<Map<String, Object>> aggregateUserProfiles();
}
