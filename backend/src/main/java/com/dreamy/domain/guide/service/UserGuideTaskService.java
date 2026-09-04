package com.dreamy.domain.guide.service;

import com.dreamy.domain.guide.entity.UserGuideTask;
import com.dreamy.domain.guide.repository.UserGuideTaskRepository;
import com.dreamy.error.MarketingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserGuideTaskService {
    private final UserGuideTaskRepository repository;
    private final GuideService guideService;
    public UserGuideTaskService(UserGuideTaskRepository repository, GuideService guideService) {
        this.repository = repository;
        this.guideService = guideService;
    }
    public Map<Long, List<Long>> list(Long userId) {
        return repository.list(userId).stream().collect(Collectors.groupingBy(UserGuideTask::getGuideId, LinkedHashMap::new, Collectors.mapping(UserGuideTask::getTaskId, Collectors.toList())));
    }
    @Transactional
    public Map<Long, List<Long>> replace(Long userId, Long guideId, List<Long> tasks) {
        List<Long> normalized = tasks == null ? List.of() : tasks.stream().filter(Objects::nonNull).distinct().limit(50).toList();
        Set<Long> validTaskIds = guideService.publishedTaskIds(guideId);
        if (!validTaskIds.containsAll(normalized)) {
            throw MarketingException.fieldValidation("task_ids", "invalid_task_id");
        }
        repository.replace(userId, guideId, normalized);
        return list(userId);
    }
}
