package org.hermesprd.workbench.updatelog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class UpdateLogService {
    private static final Logger log = LoggerFactory.getLogger(UpdateLogService.class);

    private final UpdateLogMapper updateLogMapper;
    private final ObjectMapper objectMapper;

    public UpdateLogService(UpdateLogMapper updateLogMapper, ObjectMapper objectMapper) {
        this.updateLogMapper = updateLogMapper;
        this.objectMapper = objectMapper;
    }

    public UpdateLogModels.UpdateLogSummaryResponse getPublishedUpdateLogs() {
        List<UpdateLogEntity> entities = updateLogMapper.selectList(
                new LambdaQueryWrapper<UpdateLogEntity>()
                        .eq(UpdateLogEntity::getStatus, "PUBLISHED")
                        .orderByDesc(UpdateLogEntity::getReleaseDate)
                        .orderByDesc(UpdateLogEntity::getCreatedAt)
        );

        List<UpdateLogModels.UpdateLogDto> all = entities.stream()
                .map(this::toDto)
                .toList();

        if (all.isEmpty()) {
            return new UpdateLogModels.UpdateLogSummaryResponse(null, Collections.emptyList());
        }

        UpdateLogModels.UpdateLogDto latest = all.stream()
                .filter(UpdateLogModels.UpdateLogDto::isLatest)
                .findFirst()
                .orElse(all.get(0));

        return new UpdateLogModels.UpdateLogSummaryResponse(latest, all);
    }

    public List<UpdateLogModels.UpdateLogDto> getAdminUpdateLogs(String keyword, String status) {
        LambdaQueryWrapper<UpdateLogEntity> wrapper = new LambdaQueryWrapper<UpdateLogEntity>()
                .orderByDesc(UpdateLogEntity::getReleaseDate)
                .orderByDesc(UpdateLogEntity::getCreatedAt);

        if (status != null && !status.isBlank()) {
            wrapper.eq(UpdateLogEntity::getStatus, status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(UpdateLogEntity::getVersion, kw).or().like(UpdateLogEntity::getTitle, kw));
        }

        return updateLogMapper.selectList(wrapper).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public UpdateLogModels.UpdateLogDto createUpdateLog(String operatorId, UpdateLogModels.CreateUpdateLogRequest req) {
        String version = req.version().trim();
        Long exists = updateLogMapper.selectCount(new LambdaQueryWrapper<UpdateLogEntity>().eq(UpdateLogEntity::getVersion, version));
        if (exists != null && exists > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_EXISTS", "版本号已存在: " + version);
        }

        boolean setLatest = Boolean.TRUE.equals(req.isLatest());
        if (setLatest) {
            clearLatestFlag();
        }

        Instant now = Instant.now();
        UpdateLogEntity entity = new UpdateLogEntity();
        entity.setId(Ids.next("log_"));
        entity.setVersion(version);
        entity.setReleaseDate(req.releaseDate().trim());
        entity.setTitle(req.title().trim());
        entity.setSummary(req.summary().trim());
        entity.setHighlightsJson(toJson(req.highlights() != null ? req.highlights() : Collections.emptyList()));
        entity.setItemsJson(toJson(req.items()));
        entity.setIsLatest(setLatest ? 1 : 0);
        entity.setStatus(req.status() != null && !req.status().isBlank() ? req.status().trim() : "PUBLISHED");
        entity.setCreatedBy(operatorId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        updateLogMapper.insert(entity);
        return toDto(entity);
    }

    @Transactional
    public UpdateLogModels.UpdateLogDto updateUpdateLog(String operatorId, String id, UpdateLogModels.UpdateUpdateLogRequest req) {
        UpdateLogEntity entity = updateLogMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "UPDATE_LOG_NOT_FOUND", "更新日志不存在: " + id);
        }

        String version = req.version().trim();
        if (!version.equalsIgnoreCase(entity.getVersion())) {
            Long exists = updateLogMapper.selectCount(new LambdaQueryWrapper<UpdateLogEntity>().eq(UpdateLogEntity::getVersion, version));
            if (exists != null && exists > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "VERSION_EXISTS", "版本号已存在: " + version);
            }
        }

        boolean setLatest = Boolean.TRUE.equals(req.isLatest());
        if (setLatest && entity.getIsLatest() != 1) {
            clearLatestFlag();
        }

        entity.setVersion(version);
        entity.setReleaseDate(req.releaseDate().trim());
        entity.setTitle(req.title().trim());
        entity.setSummary(req.summary().trim());
        entity.setHighlightsJson(toJson(req.highlights() != null ? req.highlights() : Collections.emptyList()));
        entity.setItemsJson(toJson(req.items()));
        entity.setIsLatest(setLatest ? 1 : 0);
        if (req.status() != null && !req.status().isBlank()) {
            entity.setStatus(req.status().trim());
        }
        entity.setUpdatedAt(Instant.now());

        updateLogMapper.updateById(entity);
        return toDto(entity);
    }

    @Transactional
    public void deleteUpdateLog(String operatorId, String id) {
        UpdateLogEntity entity = updateLogMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "UPDATE_LOG_NOT_FOUND", "更新日志不存在: " + id);
        }
        updateLogMapper.deleteById(id);
    }

    @Transactional
    public UpdateLogModels.UpdateLogDto toggleStatus(String operatorId, String id, String status, Boolean isLatest) {
        UpdateLogEntity entity = updateLogMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "UPDATE_LOG_NOT_FOUND", "更新日志不存在: " + id);
        }

        if (Boolean.TRUE.equals(isLatest)) {
            clearLatestFlag();
            entity.setIsLatest(1);
        } else if (Boolean.FALSE.equals(isLatest)) {
            entity.setIsLatest(0);
        }

        if (status != null && !status.isBlank()) {
            entity.setStatus(status.trim());
        }

        entity.setUpdatedAt(Instant.now());
        updateLogMapper.updateById(entity);
        return toDto(entity);
    }

    private void clearLatestFlag() {
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<UpdateLogEntity> wrapper = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.set("is_latest", 0);
        updateLogMapper.update(null, wrapper);
    }

    private UpdateLogModels.UpdateLogDto toDto(UpdateLogEntity entity) {
        List<String> highlights = parseJson(entity.getHighlightsJson(), new TypeReference<List<String>>() {});
        List<UpdateLogModels.UpdateLogItemDto> items = parseJson(entity.getItemsJson(), new TypeReference<List<UpdateLogModels.UpdateLogItemDto>>() {});

        return new UpdateLogModels.UpdateLogDto(
                entity.getId(),
                entity.getVersion(),
                entity.getReleaseDate(),
                entity.getTitle(),
                entity.getSummary(),
                highlights != null ? highlights : Collections.emptyList(),
                items != null ? items : Collections.emptyList(),
                entity.getIsLatest() != null && entity.getIsLatest() == 1,
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON: {}", e.getClass().getSimpleName());
            return "[]";
        }
    }

    private <T> T parseJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
