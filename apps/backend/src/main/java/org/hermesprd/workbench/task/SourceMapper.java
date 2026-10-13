package org.hermesprd.workbench.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface SourceMapper extends BaseMapper<SourceEntity> {
    @Update("""
            UPDATE prd_source
            SET content = #{content}, size_bytes = #{sizeBytes}, updated_at = #{updatedAt}
            WHERE id = #{sourceId}
              AND task_id = #{taskId}
              AND updated_at = #{expectedUpdatedAt}
            """)
    int updateDraftContent(
            @Param("taskId") String taskId,
            @Param("sourceId") String sourceId,
            @Param("content") String content,
            @Param("sizeBytes") long sizeBytes,
            @Param("expectedUpdatedAt") Instant expectedUpdatedAt,
            @Param("updatedAt") Instant updatedAt
    );
}
