package com.dreamy.domain.site_builder.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.site_builder.entity.Announcement;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AnnouncementRepository {

    private final AnnouncementMapper mapper;

    public AnnouncementRepository(AnnouncementMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<Announcement> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public IPage<Announcement> findAllOrderByPriorityId(int pageNum, int pageSize, Boolean enabledOnly) {
        LambdaQueryWrapper<Announcement> qw = new LambdaQueryWrapper<Announcement>()
                .orderByDesc(Announcement::getPriority)
                .orderByAsc(Announcement::getId);
        if (Boolean.TRUE.equals(enabledOnly)) {
            qw.eq(Announcement::getEnabled, true);
        }
        return mapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    public List<Announcement> findActiveByTimeWindow(LocalDateTime now) {
        return mapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getEnabled, true)
                .and(w -> w.isNull(Announcement::getStartAt).or().le(Announcement::getStartAt, now))
                .and(w -> w.isNull(Announcement::getEndAt).or().ge(Announcement::getEndAt, now))
                .orderByDesc(Announcement::getPriority)
                .orderByAsc(Announcement::getId));
    }

    public List<Announcement> findOverlapByPriorityAndTime(Integer priority, LocalDateTime start, LocalDateTime end) {
        return mapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getPriority, priority)
                .eq(Announcement::getEnabled, true)
                .and(w -> w.isNull(Announcement::getStartAt).or().le(Announcement::getStartAt, end))
                .and(w -> w.isNull(Announcement::getEndAt).or().ge(Announcement::getEndAt, start)));
    }

    public int insert(Announcement entity) {
        return mapper.insert(entity);
    }

    public int updateByIdAndVersion(Announcement entity) {
        int rows = mapper.update(entity, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Announcement>()
                .eq(Announcement::getId, entity.getId())
                .eq(Announcement::getVersion, entity.getVersion()));
        if (rows > 0) {
            entity.setVersion(entity.getVersion() + 1);
        }
        return rows;
    }

    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }

    public int updateEnabled(Long id, Boolean enabled, Integer expectedVersion) {
        return mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Announcement>()
                .eq(Announcement::getId, id)
                .eq(Announcement::getVersion, expectedVersion)
                .set(Announcement::getEnabled, enabled)
                .setSql("version = version + 1"));
    }
}
