package com.dreamy.domain.site_builder.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.site_builder.entity.HomePagePreviewToken;
import com.dreamy.domain.site_builder.entity.HomePageRelease;
import com.dreamy.domain.site_builder.entity.SiteBuilderConfig;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class HomePageReleaseRepository {

    private final HomePageReleaseMapper releaseMapper;
    private final HomePagePreviewTokenMapper previewTokenMapper;
    private final SiteBuilderConfigMapper configMapper;

    public HomePageReleaseRepository(HomePageReleaseMapper releaseMapper,
                                     HomePagePreviewTokenMapper previewTokenMapper,
                                     SiteBuilderConfigMapper configMapper) {
        this.releaseMapper = releaseMapper;
        this.previewTokenMapper = previewTokenMapper;
        this.configMapper = configMapper;
    }

    public HomePageRelease findActive() {
        SiteBuilderConfig config = configMapper.selectById(1L);
        return config == null || config.getActiveHomeReleaseId() == null
                ? null : releaseMapper.selectById(config.getActiveHomeReleaseId());
    }

    public SiteBuilderConfig lockConfig() {
        SiteBuilderConfig config = configMapper.lockSingleton();
        if (config != null) {
            return config;
        }
        SiteBuilderConfig created = new SiteBuilderConfig();
        created.setId(1L);
        created.setNavigationVersion(0);
        created.setFooterVersion(0);
        created.setUpdatedAt(LocalDateTime.now());
        configMapper.insert(created);
        return configMapper.lockSingleton();
    }

    public HomePageRelease findById(Long id) {
        return id == null ? null : releaseMapper.selectById(id);
    }

    public List<HomePageRelease> listRecent(int limit) {
        return releaseMapper.selectList(new LambdaQueryWrapper<HomePageRelease>()
                .orderByDesc(HomePageRelease::getPublishedAt)
                .orderByDesc(HomePageRelease::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    public int nextReleaseNo() {
        HomePageRelease latest = releaseMapper.selectOne(new LambdaQueryWrapper<HomePageRelease>()
                .orderByDesc(HomePageRelease::getReleaseNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getReleaseNo() + 1;
    }

    public void insert(HomePageRelease release) {
        releaseMapper.insert(release);
    }

    public void activate(Long releaseId) {
        int rows = configMapper.update(null, new LambdaUpdateWrapper<SiteBuilderConfig>()
                .eq(SiteBuilderConfig::getId, 1L)
                .set(SiteBuilderConfig::getActiveHomeReleaseId, releaseId)
                .set(SiteBuilderConfig::getUpdatedAt, LocalDateTime.now()));
        if (rows != 1) {
            throw new IllegalStateException("site_builder_config singleton is missing");
        }
    }

    public void insertPreviewToken(HomePagePreviewToken token) {
        previewTokenMapper.insert(token);
    }

    public void deleteExpiredPreviewTokens(LocalDateTime now) {
        previewTokenMapper.delete(new LambdaQueryWrapper<HomePagePreviewToken>()
                .le(HomePagePreviewToken::getExpiresAt, now));
    }

    public HomePagePreviewToken findValidPreviewToken(String tokenHash, LocalDateTime now) {
        return previewTokenMapper.selectOne(new LambdaQueryWrapper<HomePagePreviewToken>()
                .eq(HomePagePreviewToken::getTokenHash, tokenHash)
                .gt(HomePagePreviewToken::getExpiresAt, now)
                .last("LIMIT 1"));
    }
}
