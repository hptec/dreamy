package com.dreamy.domain.guide.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.guide.entity.UserGuideTask;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class UserGuideTaskRepository {
    private final UserGuideTaskMapper mapper;
    public UserGuideTaskRepository(UserGuideTaskMapper mapper) { this.mapper = mapper; }
    public List<UserGuideTask> list(Long userId) { return mapper.selectList(new LambdaQueryWrapper<UserGuideTask>().eq(UserGuideTask::getUserId, userId)); }
    public void replace(Long userId, Long guideId, List<Long> tasks) {
        mapper.delete(new LambdaQueryWrapper<UserGuideTask>().eq(UserGuideTask::getUserId, userId).eq(UserGuideTask::getGuideId, guideId));
        for (Long task : tasks) {
            UserGuideTask row = new UserGuideTask(); row.setUserId(userId); row.setGuideId(guideId); row.setTaskId(task);
            try { mapper.insert(row); } catch (DuplicateKeyException ignored) { }
        }
    }
}
