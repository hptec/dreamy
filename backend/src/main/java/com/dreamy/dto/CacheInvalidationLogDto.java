package com.dreamy.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 缓存失效日志 DTO。
 */
@Data
public class CacheInvalidationLogDto {
    private Long id;
    private String eventType;
    private String resourceType;
    private Long resourceId;
    private String slug;
    private String oldSlug;
    private List<String> affectedPaths;
    private List<String> locales;
    private String triggeredBy;
    private LocalDateTime triggeredAt;
    private Integer status;
    private LocalDateTime completedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
}
