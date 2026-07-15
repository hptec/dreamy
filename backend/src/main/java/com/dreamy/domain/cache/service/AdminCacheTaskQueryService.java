package com.dreamy.domain.cache.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.cache.entity.CacheInvalidationStep;
import com.dreamy.domain.cache.entity.CacheInvalidationTask;
import com.dreamy.domain.cache.repository.CacheInvalidationStepRepository;
import com.dreamy.domain.cache.repository.CacheInvalidationTaskRepository;
import com.dreamy.dto.CacheInvalidationTaskDto;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminCacheTaskQueryService {

    private final CacheInvalidationTaskRepository tasks;
    private final CacheInvalidationStepRepository steps;
    private final CacheInvalidationTaskService taskService;

    public AdminCacheTaskQueryService(CacheInvalidationTaskRepository tasks,
                                      CacheInvalidationStepRepository steps,
                                      CacheInvalidationTaskService taskService) {
        this.tasks = tasks;
        this.steps = steps;
        this.taskService = taskService;
    }

    public Paginated<CacheInvalidationTaskDto> pageList(Integer page, Integer pageSize, String triggerMode,
                                                        String resourceType, Integer status) {
        int p = page != null && page > 0 ? page : 1;
        int ps = pageSize != null && pageSize > 0 && pageSize <= 100 ? pageSize : 30;
        Page<CacheInvalidationTask> result = tasks.pageList(new Page<>(p, ps), triggerMode, resourceType, status);
        List<Long> ids = result.getRecords().stream().map(CacheInvalidationTask::getId).toList();
        Map<Long, List<CacheInvalidationStep>> stepMap = steps.listByTaskIds(ids).stream()
                .collect(Collectors.groupingBy(CacheInvalidationStep::getTaskId, LinkedHashMap::new, Collectors.toList()));
        List<CacheInvalidationTaskDto> records = result.getRecords().stream()
                .map(task -> toDto(task, stepMap.getOrDefault(task.getId(), List.of())))
                .toList();
        Paginated<CacheInvalidationTaskDto> paginated = new Paginated<>();
        paginated.setData(records);
        paginated.setTotalElements(result.getTotal());
        paginated.setPageNumber(p);
        paginated.setPageSize(ps);
        paginated.setNumberOfElements(records.size());
        paginated.setTotalPages((int) Math.ceil((double) result.getTotal() / ps));
        return paginated;
    }

    public Map<String, Long> summary() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("scheduled", taskService.countByStatus(CacheInvalidationTask.STATUS_SCHEDULED));
        result.put("pending", taskService.countByStatus(CacheInvalidationTask.STATUS_PENDING));
        result.put("running", taskService.countByStatus(CacheInvalidationTask.STATUS_RUNNING));
        result.put("retrying", taskService.countByStatus(CacheInvalidationTask.STATUS_RETRYING));
        result.put("succeeded", taskService.countByStatus(CacheInvalidationTask.STATUS_SUCCEEDED));
        result.put("partial", taskService.countByStatus(CacheInvalidationTask.STATUS_PARTIAL));
        result.put("failed", taskService.countByStatus(CacheInvalidationTask.STATUS_FAILED));
        return result;
    }

    private CacheInvalidationTaskDto toDto(CacheInvalidationTask task, List<CacheInvalidationStep> taskSteps) {
        CacheInvalidationTaskDto dto = new CacheInvalidationTaskDto();
        dto.setId(task.getId());
        dto.setCorrelationId(task.getCorrelationId());
        dto.setTriggerMode(task.getTriggerMode());
        dto.setTriggerPoint(task.getTriggerPoint());
        dto.setResourceType(task.getResourceType());
        dto.setResourceId(task.getResourceId());
        dto.setResourceLabel(task.getResourceLabel());
        dto.setTargets(task.getTargets());
        dto.setDetails(task.getDetails());
        dto.setTriggeredBy(task.getTriggeredBy());
        dto.setTriggeredAt(task.getTriggeredAt());
        dto.setScheduledAt(task.getScheduledAt());
        dto.setStartedAt(task.getStartedAt());
        dto.setCompletedAt(task.getCompletedAt());
        dto.setNextRetryAt(task.getNextRetryAt());
        dto.setStatus(task.getStatus());
        dto.setAttemptCount(task.getAttemptCount());
        dto.setMaxAttempts(task.getMaxAttempts());
        dto.setErrorMessage(task.getErrorMessage());
        List<CacheInvalidationTaskDto.StepDto> stepDtos = new ArrayList<>();
        for (CacheInvalidationStep step : taskSteps) {
            CacheInvalidationTaskDto.StepDto item = new CacheInvalidationTaskDto.StepDto();
            item.setId(step.getId());
            item.setStepType(step.getStepType());
            item.setTarget(step.getTarget());
            item.setStatus(step.getStatus());
            item.setAttempt(step.getAttempt());
            item.setStartedAt(step.getStartedAt());
            item.setCompletedAt(step.getCompletedAt());
            item.setResultDetail(step.getResultDetail());
            item.setErrorMessage(step.getErrorMessage());
            stepDtos.add(item);
        }
        dto.setSteps(stepDtos);
        return dto;
    }
}
