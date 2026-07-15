package com.dreamy.domain.cache.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.cache.entity.CacheInvalidationStep;
import com.dreamy.domain.cache.entity.CacheInvalidationTask;
import com.dreamy.domain.cache.repository.CacheInvalidationStepRepository;
import com.dreamy.domain.cache.repository.CacheInvalidationTaskRepository;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CacheInvalidationTaskService {

    public static final String MODE_BUSINESS_WRITE = "BUSINESS_WRITE";
    public static final String MODE_MANUAL = "MANUAL";
    public static final String MODE_SCHEDULED = "SCHEDULED";
    public static final String MODE_SYSTEM_EVENT = "SYSTEM_EVENT";

    private final CacheInvalidationTaskRepository tasks;
    private final CacheInvalidationStepRepository steps;
    private final Clock clock;

    public CacheInvalidationTaskService(CacheInvalidationTaskRepository tasks,
                                        CacheInvalidationStepRepository steps, Clock clock) {
        this.tasks = tasks;
        this.steps = steps;
        this.clock = clock;
    }

    @Transactional
    public Long enqueue(String triggerMode, String triggerPoint, String resourceType, Object resourceId,
                        String resourceLabel, List<CacheInvalidationTarget> targets,
                        LocalDateTime scheduledAt, Map<String, Object> details, String triggeredBy) {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("at least one cache target is required");
        }
        LocalDateTime now = now();
        LocalDateTime due = scheduledAt == null ? now : scheduledAt;
        CacheInvalidationTask task = new CacheInvalidationTask();
        task.setCorrelationId(UUID.randomUUID().toString());
        task.setTriggerMode(triggerMode);
        task.setTriggerPoint(triggerPoint);
        task.setResourceType(resourceType);
        task.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
        task.setResourceLabel(resourceLabel);
        task.setTargets(targets.stream().map(Enum::name).distinct().toList());
        task.setDetails(details == null ? Map.of() : new LinkedHashMap<>(details));
        task.setTriggeredBy(triggeredBy == null ? currentActor() : triggeredBy);
        task.setTriggeredAt(now);
        task.setScheduledAt(due);
        task.setStatus(due.isAfter(now) ? CacheInvalidationTask.STATUS_SCHEDULED : CacheInvalidationTask.STATUS_PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(3);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        tasks.insert(task);
        return task.getId();
    }

    @Transactional
    public int cancelFuture(String resourceType, Object resourceId, String triggerPointPrefix) {
        if (resourceId == null) return 0;
        return tasks.cancelFuture(resourceType, String.valueOf(resourceId), triggerPointPrefix, now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CacheInvalidationTask claim(Long id) {
        LocalDateTime now = now();
        if (!tasks.claim(id, now)) return null;
        CacheInvalidationTask task = tasks.selectById(id);
        if (task != null && task.getStartedAt() == null) {
            task.setStartedAt(now);
            task.setUpdatedAt(now);
            tasks.updateById(task);
        }
        return task;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long beginStep(Long taskId, int attempt, String target) {
        LocalDateTime now = now();
        CacheInvalidationStep step = new CacheInvalidationStep();
        step.setTaskId(taskId);
        step.setStepType("LOCAL_CACHE");
        step.setTarget(target);
        step.setStatus(CacheInvalidationStep.STATUS_RUNNING);
        step.setAttempt(attempt);
        step.setStartedAt(now);
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        steps.insert(step);
        return step.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeStep(Long stepId, boolean success, String result, String error) {
        CacheInvalidationStep step = steps.selectById(stepId);
        if (step == null) return;
        LocalDateTime now = now();
        step.setStatus(success ? CacheInvalidationStep.STATUS_SUCCEEDED : CacheInvalidationStep.STATUS_FAILED);
        step.setResultDetail(result);
        step.setErrorMessage(error);
        step.setCompletedAt(now);
        step.setUpdatedAt(now);
        steps.updateById(step);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishAttempt(Long taskId, int successCount, int failureCount, String error) {
        CacheInvalidationTask task = tasks.selectById(taskId);
        if (task == null || task.getStatus() == CacheInvalidationTask.STATUS_CANCELLED) return;
        LocalDateTime now = now();
        int attempt = task.getAttemptCount() == null ? 1 : task.getAttemptCount() + 1;
        task.setAttemptCount(attempt);
        task.setErrorMessage(error);
        task.setUpdatedAt(now);
        if (failureCount == 0) {
            task.setStatus(CacheInvalidationTask.STATUS_SUCCEEDED);
            task.setCompletedAt(now);
            task.setNextRetryAt(null);
        } else if (attempt < task.getMaxAttempts()) {
            task.setStatus(CacheInvalidationTask.STATUS_RETRYING);
            task.setNextRetryAt(now.plusSeconds(5L << Math.max(0, attempt - 1)));
        } else {
            task.setStatus(successCount > 0 ? CacheInvalidationTask.STATUS_PARTIAL : CacheInvalidationTask.STATUS_FAILED);
            task.setCompletedAt(now);
            task.setNextRetryAt(null);
        }
        tasks.updateById(task);
    }

    @Transactional
    public void retry(Long taskId) {
        CacheInvalidationTask task = tasks.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("cache task not found");
        if (task.getStatus() != CacheInvalidationTask.STATUS_FAILED
                && task.getStatus() != CacheInvalidationTask.STATUS_PARTIAL) {
            throw new IllegalStateException("only failed or partial tasks can be retried");
        }
        LocalDateTime now = now();
        task.setStatus(CacheInvalidationTask.STATUS_PENDING);
        task.setAttemptCount(0);
        task.setScheduledAt(now);
        task.setNextRetryAt(null);
        task.setCompletedAt(null);
        task.setErrorMessage(null);
        task.setUpdatedAt(now);
        tasks.updateById(task);
    }

    public List<CacheInvalidationTask> findDue(int limit) {
        return tasks.findDue(now(), limit);
    }

    /** Recover tasks abandoned by a terminated worker. A crash consumes one attempt just like an execution error. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleRunning(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("running timeout must be positive");
        }
        LocalDateTime now = now();
        List<CacheInvalidationTask> stale = tasks.findStaleRunning(now.minus(timeout), 100);
        for (CacheInvalidationTask task : stale) {
            String error = "worker interrupted before the attempt completed";
            steps.failRunningByTaskId(task.getId(), now, error);
            int attempt = (task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1;
            task.setAttemptCount(attempt);
            task.setErrorMessage(error);
            task.setUpdatedAt(now);
            if (attempt < task.getMaxAttempts()) {
                task.setStatus(CacheInvalidationTask.STATUS_RETRYING);
                task.setNextRetryAt(now);
                task.setCompletedAt(null);
            } else {
                task.setStatus(CacheInvalidationTask.STATUS_FAILED);
                task.setNextRetryAt(null);
                task.setCompletedAt(now);
            }
            tasks.updateById(task);
        }
        return stale.size();
    }

    public long countByStatus(int status) {
        return tasks.selectCount(new LambdaQueryWrapper<CacheInvalidationTask>()
                .eq(CacheInvalidationTask::getStatus, status));
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String currentActor() {
        AuthPrincipal principal = AuthContext.get();
        return principal != null && AuthPrincipal.TYPE_ADMIN.equals(principal.type())
                ? "admin:" + principal.subject()
                : "system";
    }
}
