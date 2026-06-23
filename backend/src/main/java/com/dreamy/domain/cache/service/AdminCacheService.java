package com.dreamy.domain.cache.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.cache.entity.CacheInvalidationLog;
import com.dreamy.domain.cache.repository.CacheInvalidationLogRepository;
import com.dreamy.dto.CacheInvalidationLogDto;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 缓存失效日志服务。
 * 提供查询失效日志和手动触发失效的功能。
 */
@Service
public class AdminCacheService {

    private final CacheInvalidationLogRepository repository;

    public AdminCacheService(CacheInvalidationLogRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页查询失效日志。
     * @param page 页码（从 1 开始）
     * @param pageSize 每页大小
     * @param eventType 事件类型过滤（可选）
     * @param resourceType 资源类型过滤（可选）
     * @param status 状态过滤（可选）
     * @return 分页结果
     */
    public Paginated<CacheInvalidationLogDto> pageList(Integer page, Integer pageSize,
                                                       String eventType, String resourceType, Integer status) {
        int p = page != null && page > 0 ? page : 1;
        int ps = pageSize != null && pageSize > 0 && pageSize <= 100 ? pageSize : 50;

        Page<CacheInvalidationLog> mybatisPage = new Page<>(p, ps);
        Page<CacheInvalidationLog> result = repository.pageList(mybatisPage, eventType, resourceType, status);

        List<CacheInvalidationLogDto> records = new ArrayList<>();
        for (CacheInvalidationLog log : result.getRecords()) {
            records.add(toDto(log));
        }

        Paginated<CacheInvalidationLogDto> paginated = new Paginated<>();
        paginated.setData(records);
        paginated.setTotalElements(result.getTotal());
        paginated.setPageNumber(p);
        paginated.setPageSize(ps);
        paginated.setNumberOfElements(records.size());
        paginated.setTotalPages((int) Math.ceil((double) result.getTotal() / ps));
        return paginated;
    }

    /**
     * 记录缓存失效日志（由 ContentInvalidatedPublisher 调用）。
     * @return 新建日志记录的 ID，供 CDN 回写状态用；失败返回 null
     */
    @Transactional
    public Long logInvalidation(String eventType, String resourceType, Long resourceId,
                                String slug, String oldSlug, List<String> locales, String triggeredBy) {
        return logInvalidation(eventType, resourceType, resourceId, slug, oldSlug, locales, triggeredBy, null);
    }

    /**
     * 记录缓存失效日志（带显式 affectedPaths，手动失效场景用）。
     * @param affectedPaths 显式指定受影响路径；非 null 时覆盖自动推测的路径
     * @return 新建日志记录的 ID；失败返回 null
     */
    @Transactional
    public Long logInvalidation(String eventType, String resourceType, Long resourceId,
                                String slug, String oldSlug, List<String> locales,
                                String triggeredBy, List<String> affectedPaths) {
        CacheInvalidationLog log = new CacheInvalidationLog();
        log.setEventType(eventType);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setSlug(slug);
        log.setOldSlug(oldSlug);
        log.setLocales(locales);
        log.setTriggeredBy(triggeredBy);
        log.setTriggeredAt(LocalDateTime.now());
        log.setStatus(CacheInvalidationLog.STATUS_PENDING);
        log.setCreatedAt(LocalDateTime.now());

        // 根据资源类型和 slug 推测受影响的路径
        List<String> paths = new ArrayList<>();
        if ("product".equals(resourceType) && slug != null) {
            for (String locale : (locales != null ? locales : List.of("en"))) {
                String prefix = "en".equals(locale) ? "" : "/" + locale;
                paths.add(prefix + "/product/" + slug);
            }
            // 旧 slug 也需要失效
            if (oldSlug != null && !oldSlug.equals(slug)) {
                for (String locale : (locales != null ? locales : List.of("en"))) {
                    String prefix = "en".equals(locale) ? "" : "/" + locale;
                    paths.add(prefix + "/product/" + oldSlug);
                }
            }
        } else if ("blog".equals(resourceType) && slug != null) {
            for (String locale : (locales != null ? locales : List.of("en"))) {
                String prefix = "en".equals(locale) ? "" : "/" + locale;
                paths.add(prefix + "/blog/" + slug);
            }
            if (oldSlug != null && !oldSlug.equals(slug)) {
                for (String locale : (locales != null ? locales : List.of("en"))) {
                    String prefix = "en".equals(locale) ? "" : "/" + locale;
                    paths.add(prefix + "/blog/" + oldSlug);
                }
            }
        } else if ("wedding".equals(resourceType) && resourceId != null) {
            for (String locale : (locales != null ? locales : List.of("en"))) {
                String prefix = "en".equals(locale) ? "" : "/" + locale;
                paths.add(prefix + "/real-weddings/" + resourceId);
            }
        }
        // 分类/集合变更影响列表页
        else if ("category".equals(resourceType) || "collection".equals(resourceType)) {
            paths.add("/products");
            paths.add("/es/products");
            paths.add("/fr/products");
        }

        // 显式传入的 paths 优先于自动推测
        List<String> finalPaths = (affectedPaths != null && !affectedPaths.isEmpty())
                ? affectedPaths
                : (paths.isEmpty() ? null : paths);
        log.setAffectedPaths(finalPaths);
        repository.insert(log);
        return log.getId();
    }

    /**
     * 回写日志状态（由 CdnInvalidationService 在 CDN 调用完成后调用）。
     */
    @Transactional
    public void updateLogStatus(Long logId, int status, String errorMessage) {
        if (logId == null) {
            return;
        }
        CacheInvalidationLog log = repository.selectById(logId);
        if (log == null) {
            return;
        }
        log.setStatus(status);
        log.setCompletedAt(LocalDateTime.now());
        if (errorMessage != null) {
            log.setErrorMessage(errorMessage);
        }
        repository.updateById(log);
    }

    private CacheInvalidationLogDto toDto(CacheInvalidationLog log) {
        CacheInvalidationLogDto dto = new CacheInvalidationLogDto();
        dto.setId(log.getId());
        dto.setEventType(log.getEventType());
        dto.setResourceType(log.getResourceType());
        dto.setResourceId(log.getResourceId());
        dto.setSlug(log.getSlug());
        dto.setOldSlug(log.getOldSlug());
        dto.setAffectedPaths(log.getAffectedPaths());
        dto.setLocales(log.getLocales());
        dto.setTriggeredBy(log.getTriggeredBy());
        dto.setTriggeredAt(log.getTriggeredAt());
        dto.setStatus(log.getStatus());
        dto.setCompletedAt(log.getCompletedAt());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
