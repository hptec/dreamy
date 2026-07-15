package com.dreamy.sched;

import com.dreamy.domain.cache.entity.CacheInvalidationTask;
import com.dreamy.domain.cache.service.CacheInvalidationDispatcher;
import com.dreamy.domain.cache.service.CacheInvalidationTarget;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;

/** Durable cache task worker. A Redis lock prevents duplicate execution across application instances. */
@Component
public class CacheInvalidationTaskWorker {

    public static final String LOCK_KEY = "sched:cache-invalidation-worker";
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationTaskWorker.class);

    private final RedissonClient redisson;
    private final CacheInvalidationTaskService taskService;
    private final CacheInvalidationDispatcher dispatcher;
    private final Duration runningTimeout;

    public CacheInvalidationTaskWorker(RedissonClient redisson, CacheInvalidationTaskService taskService,
                                       CacheInvalidationDispatcher dispatcher,
                                       @Value("${dreamy.cache.running-timeout-seconds:120}") long runningTimeoutSeconds) {
        this.redisson = redisson;
        this.taskService = taskService;
        this.dispatcher = dispatcher;
        if (runningTimeoutSeconds <= 0) throw new IllegalArgumentException("running timeout must be positive");
        this.runningTimeout = Duration.ofSeconds(runningTimeoutSeconds);
    }

    @Scheduled(fixedDelayString = "${dreamy.cache.worker-delay-ms:1000}")
    public void run() {
        RLock lock = redisson.getLock(LOCK_KEY);
        if (!lock.tryLock()) return;
        try {
            int recovered = taskService.recoverStaleRunning(runningTimeout);
            if (recovered > 0) log.warn("[CACHE-TASK] recovered {} stale running tasks", recovered);
            for (CacheInvalidationTask due : taskService.findDue(20)) {
                execute(due.getId());
            }
        } catch (Exception ex) {
            log.error("[CACHE-TASK] worker iteration failed", ex);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    void execute(Long taskId) {
        CacheInvalidationTask task = taskService.claim(taskId);
        if (task == null) return;
        int attempt = (task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1;
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (String rawTarget : task.getTargets()) {
            Long stepId = taskService.beginStep(taskId, attempt, rawTarget);
            try {
                String result = dispatcher.execute(CacheInvalidationTarget.valueOf(rawTarget));
                taskService.completeStep(stepId, true, result, null);
                success++;
            } catch (Exception ex) {
                String message = ex.getClass().getSimpleName() + ": " + safeMessage(ex);
                taskService.completeStep(stepId, false, null, message);
                errors.add(rawTarget + " - " + message);
            }
        }
        taskService.finishAttempt(taskId, success, errors.size(), errors.isEmpty() ? null : String.join("; ", errors));
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return "execution failed";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
