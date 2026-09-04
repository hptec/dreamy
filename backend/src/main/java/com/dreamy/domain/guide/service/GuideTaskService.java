package com.dreamy.domain.guide.service;

import com.dreamy.domain.guide.entity.GuideTask;
import com.dreamy.domain.guide.entity.GuideTaskTranslation;
import com.dreamy.domain.guide.repository.GuideTaskRepository;
import com.dreamy.domain.guide.repository.GuideTaskTranslationRepository;
import com.dreamy.dto.AdminMarketingDtos;
import com.dreamy.dto.MarketingTranslationDtos.GuideTaskTranslationDto;
import com.dreamy.dto.MarketingTranslationDtos.GuideTranslationDto;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GuideTaskService {
    private final GuideTaskRepository repository;
    private final GuideTaskTranslationRepository translationRepository;
    public GuideTaskService(GuideTaskRepository repository, GuideTaskTranslationRepository translationRepository) {
        this.repository = repository;
        this.translationRepository = translationRepository;
    }
    public List<AdminMarketingDtos.GuideTask> list(Long guideId) { return toDtos(repository.listByGuideId(guideId)); }
    public Map<Long, List<AdminMarketingDtos.GuideTask>> listByGuideIds(Collection<Long> ids) {
        return repository.listByGuideIds(ids).stream().collect(Collectors.groupingBy(GuideTask::getGuideId, LinkedHashMap::new, Collectors.collectingAndThen(Collectors.toList(), this::toDtos)));
    }
    public Map<Long, List<AdminMarketingDtos.GuideTask>> listByGuideIds(Collection<Long> ids, String locale) {
        List<GuideTask> tasks = repository.listByGuideIds(ids);
        Map<Long, String> labels = translationRepository.listByTaskIds(tasks.stream().map(GuideTask::getId).toList())
                .stream().filter(row -> locale != null && locale.equals(row.getLocale()))
                .collect(Collectors.toMap(GuideTaskTranslation::getTaskId, GuideTaskTranslation::getLabel));
        return tasks.stream().collect(Collectors.groupingBy(GuideTask::getGuideId, LinkedHashMap::new,
                Collectors.mapping(task -> new AdminMarketingDtos.GuideTask(task.getId(),
                        labels.getOrDefault(task.getId(), task.getLabel())), Collectors.toList())));
    }
    public Set<Long> ids(Long guideId) { return repository.listByGuideId(guideId).stream().map(GuideTask::getId).collect(Collectors.toSet()); }
    public void deleteByGuideId(Long guideId) {
        repository.listByGuideId(guideId).forEach(task -> translationRepository.deleteByTaskId(task.getId()));
        repository.deleteByGuideId(guideId);
    }
    public void replace(Long guideId, List<AdminMarketingDtos.GuideTask> input) {
        List<GuideTask> existing = repository.listByGuideId(guideId);
        Map<Long, GuideTask> byId = existing.stream().collect(Collectors.toMap(GuideTask::getId, item -> item));
        Set<Long> retained = new HashSet<>();
        for (int index = 0; index < input.size(); index++) {
            AdminMarketingDtos.GuideTask item = input.get(index);
            if (item.taskId() != null && byId.containsKey(item.taskId())) {
                GuideTask task = byId.get(item.taskId()); task.setLabel(item.label()); task.setSortOrder(index); repository.update(task); retained.add(task.getId());
            } else {
                GuideTask task = new GuideTask(); task.setGuideId(guideId); task.setLabel(item.label()); task.setSortOrder(index); repository.insert(task);
            }
        }
        existing.stream().filter(task -> !retained.contains(task.getId())).forEach(task -> {
            translationRepository.deleteByTaskId(task.getId());
            repository.deleteById(task.getId());
        });
    }
    public void replaceTranslations(Long guideId, List<GuideTranslationDto> translations) {
        List<GuideTask> persisted = repository.listByGuideId(guideId);
        Map<Long, GuideTask> byId = persisted.stream().collect(Collectors.toMap(GuideTask::getId, item -> item));
        for (GuideTask task : persisted) translationRepository.deleteByTaskId(task.getId());
        if (translations == null) return;
        for (GuideTranslationDto translation : translations) {
            List<GuideTaskTranslationDto> items = translation.tasks() == null ? List.of() : translation.tasks();
            for (int index = 0; index < items.size(); index++) {
                GuideTaskTranslationDto item = items.get(index);
                GuideTask task = item.taskId() == null ? (index < persisted.size() ? persisted.get(index) : null) : byId.get(item.taskId());
                if (task == null || item.label() == null || item.label().isBlank()) continue;
                GuideTaskTranslation row = new GuideTaskTranslation();
                row.setLocale(translation.locale());
                row.setLabel(item.label().trim());
                translationRepository.replace(task.getId(), mergeTranslation(task.getId(), row));
            }
        }
    }
    public Map<String, List<GuideTaskTranslationDto>> translations(Long guideId) {
        List<GuideTask> tasks = repository.listByGuideId(guideId);
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) order.put(tasks.get(i).getId(), i);
        return translationRepository.listByTaskIds(order.keySet()).stream()
                .sorted(Comparator.comparingInt(row -> order.getOrDefault(row.getTaskId(), Integer.MAX_VALUE)))
                .collect(Collectors.groupingBy(GuideTaskTranslation::getLocale, LinkedHashMap::new,
                        Collectors.mapping(row -> new GuideTaskTranslationDto(row.getTaskId(), row.getLabel()), Collectors.toList())));
    }
    /** Adds only missing locale rows; demo backfills must never overwrite CMS edits. */
    public void seedMissingTranslations(Long guideId, String locale, List<String> labels) {
        List<GuideTask> tasks = repository.listByGuideId(guideId);
        Set<Long> existing = translationRepository.listByTaskIds(tasks.stream().map(GuideTask::getId).toList()).stream()
                .filter(row -> locale.equals(row.getLocale()))
                .map(GuideTaskTranslation::getTaskId).collect(Collectors.toSet());
        for (int i = 0; i < Math.min(tasks.size(), labels.size()); i++) {
            if (existing.contains(tasks.get(i).getId())) continue;
            GuideTaskTranslation row = new GuideTaskTranslation();
            row.setTaskId(tasks.get(i).getId());
            row.setLocale(locale);
            row.setLabel(labels.get(i));
            translationRepository.insert(row);
        }
    }
    private List<GuideTaskTranslation> mergeTranslation(Long taskId, GuideTaskTranslation next) {
        List<GuideTaskTranslation> rows = new ArrayList<>(translationRepository.listByTaskIds(List.of(taskId)));
        rows.removeIf(row -> row.getLocale().equals(next.getLocale()));
        rows.add(next);
        return rows;
    }
    private List<AdminMarketingDtos.GuideTask> toDtos(List<GuideTask> tasks) { return tasks.stream().map(task -> new AdminMarketingDtos.GuideTask(task.getId(), task.getLabel())).toList(); }
}
