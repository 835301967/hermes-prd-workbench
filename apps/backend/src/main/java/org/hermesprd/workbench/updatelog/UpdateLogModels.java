package org.hermesprd.workbench.updatelog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class UpdateLogModels {
    private UpdateLogModels() {
    }

    public record UpdateLogItemDto(
            @NotBlank @Size(max = 255) String title,
            @NotBlank String detail,
            String type,
            String tone
    ) {
        public String type() {
            return type == null || type.isBlank() ? "feature" : type;
        }

        public String tone() {
            return tone == null || tone.isBlank() ? "accent" : tone;
        }
    }

    public record UpdateLogDto(
            String id,
            String version,
            String releaseDate,
            String title,
            String summary,
            List<String> highlights,
            List<UpdateLogItemDto> items,
            boolean isLatest,
            String status,
            String createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record UpdateLogSummaryResponse(
            UpdateLogDto latest,
            List<UpdateLogDto> history
    ) {
    }

    public record CreateUpdateLogRequest(
            @NotBlank @Size(max = 32) String version,
            @NotBlank @Size(max = 16) String releaseDate,
            @NotBlank @Size(max = 255) String title,
            @NotBlank String summary,
            List<String> highlights,
            @NotEmpty @Valid List<UpdateLogItemDto> items,
            Boolean isLatest,
            String status
    ) {
    }

    public record UpdateUpdateLogRequest(
            @NotBlank @Size(max = 32) String version,
            @NotBlank @Size(max = 16) String releaseDate,
            @NotBlank @Size(max = 255) String title,
            @NotBlank String summary,
            List<String> highlights,
            @NotEmpty @Valid List<UpdateLogItemDto> items,
            Boolean isLatest,
            String status
    ) {
    }

    public record UpdateStatusRequest(
            String status,
            Boolean isLatest
    ) {
    }
}
