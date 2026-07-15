package com.dreamy.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class CacheInvalidationTaskDto {
    private Long id;
    private String correlationId;
    private String triggerMode;
    private String triggerPoint;
    private String resourceType;
    private String resourceId;
    private String resourceLabel;
    private List<String> targets;
    private Map<String, Object> details;
    private String triggeredBy;
    private LocalDateTime triggeredAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime nextRetryAt;
    private Integer status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String errorMessage;
    private List<StepDto> steps;

    @Data
    public static class StepDto {
        private Long id;
        private String stepType;
        private String target;
        private Integer status;
        private Integer attempt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String resultDetail;
        private String errorMessage;
    }
}
