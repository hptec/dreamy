package com.dreamy.domain.guide.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.guide.entity.GuideTask;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public class GuideTaskRepository {
    private final GuideTaskMapper mapper;
    public GuideTaskRepository(GuideTaskMapper mapper) { this.mapper = mapper; }
    public List<GuideTask> listByGuideId(Long guideId) { return mapper.selectList(new LambdaQueryWrapper<GuideTask>().eq(GuideTask::getGuideId, guideId).orderByAsc(GuideTask::getSortOrder).orderByAsc(GuideTask::getId)); }
    public List<GuideTask> listByGuideIds(Collection<Long> ids) { return ids == null || ids.isEmpty() ? List.of() : mapper.selectList(new LambdaQueryWrapper<GuideTask>().in(GuideTask::getGuideId, ids).orderByAsc(GuideTask::getSortOrder).orderByAsc(GuideTask::getId)); }
    public void insert(GuideTask task) { mapper.insert(task); }
    public void update(GuideTask task) { mapper.updateById(task); }
    public void deleteById(Long id) { mapper.deleteById(id); }
    public void deleteByGuideId(Long guideId) { mapper.delete(new LambdaQueryWrapper<GuideTask>().eq(GuideTask::getGuideId, guideId)); }
}
