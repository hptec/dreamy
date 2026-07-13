package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.banner.entity.Banner;
import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.domain.site_builder.entity.HomePagePreviewToken;
import com.dreamy.domain.site_builder.entity.HomePageRelease;
import com.dreamy.domain.site_builder.entity.HomePageSection;
import com.dreamy.domain.site_builder.entity.SiteBuilderConfig;
import com.dreamy.domain.site_builder.repository.HomePageReleaseRepository;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.dto.SiteBuilderDtos.HomePagePreviewTokenDto;
import com.dreamy.dto.SiteBuilderDtos.HomePagePublicationStatusDto;
import com.dreamy.dto.SiteBuilderDtos.HomePageReleaseDto;
import com.dreamy.dto.SiteBuilderDtos.StoreHomePageDto;
import com.dreamy.enums.BannerPosition;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class HomePagePublicationService {

    private static final int PREVIEW_TTL_MINUTES = 30;
    private static final List<String> LOCALES = List.of("en", "es", "fr");

    private final HomePageSectionRepository sectionRepository;
    private final HomePageReleaseRepository releaseRepository;
    private final BannerRepository bannerRepository;
    private final StoreContentService storeContentService;
    private final SiteBuilderCacheService cacheService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public HomePagePublicationService(HomePageSectionRepository sectionRepository,
                                      HomePageReleaseRepository releaseRepository,
                                      BannerRepository bannerRepository,
                                      StoreContentService storeContentService,
                                      SiteBuilderCacheService cacheService,
                                      ObjectMapper objectMapper) {
        this.sectionRepository = sectionRepository;
        this.releaseRepository = releaseRepository;
        this.bannerRepository = bannerRepository;
        this.storeContentService = storeContentService;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public StoreHomePageDto previewDraft(String locale) {
        validateWorkspace();
        return storeContentService.getDraftHome(locale);
    }

    @Transactional(readOnly = true)
    public HomePagePublicationStatusDto status() {
        HomePageRelease active = releaseRepository.findActive();
        DraftBundle draft = draftBundle();
        HomePagePublicationStatusDto dto = new HomePagePublicationStatusDto();
        dto.setHasPublishedRelease(active != null);
        dto.setDraftModified(active == null || !sameContent(active, draft));
        dto.setDraftRevision(draftRevision(draft));
        if (active != null) {
            dto.setActiveReleaseId(active.getId());
            dto.setActiveReleaseNo(active.getReleaseNo());
            dto.setActiveReleaseName(active.getName());
            dto.setPublishedAt(active.getPublishedAt());
        }
        return dto;
    }

    public List<HomePageReleaseDto> history(int limit) {
        HomePageRelease active = releaseRepository.findActive();
        Long activeId = active == null ? null : active.getId();
        return releaseRepository.listRecent(limit).stream()
                .map(item -> toDto(item, item.getId().equals(activeId)))
                .toList();
    }

    @Transactional
    public HomePageReleaseDto publish(String requestedName, String expectedDraftRevision) {
        releaseRepository.lockConfig();
        validateWorkspace();
        DraftBundle draft = draftBundle();
        if (expectedDraftRevision == null || !MessageDigest.isEqual(
                expectedDraftRevision.getBytes(StandardCharsets.UTF_8),
                draftRevision(draft).getBytes(StandardCharsets.UTF_8))) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_RELEASE_DRAFT_CHANGED);
        }
        HomePageRelease active = releaseRepository.findActive();
        if (active != null && sameContent(active, draft)) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_RELEASE_NO_CHANGES);
        }
        HomePageRelease release = buildRelease(draft, requestedName, null);
        releaseRepository.insert(release);
        releaseRepository.activate(release.getId());
        cacheService.invalidateHomeSectionFamily();
        return toDto(release, true);
    }

    @Transactional
    public HomePageReleaseDto rollback(Long releaseId) {
        releaseRepository.lockConfig();
        HomePageRelease source = releaseRepository.findById(releaseId);
        if (source == null) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_RELEASE_NOT_FOUND);
        }
        HomePageRelease release = new HomePageRelease();
        int releaseNo = releaseRepository.nextReleaseNo();
        release.setReleaseNo(releaseNo);
        release.setName("回滚至 V" + source.getReleaseNo());
        release.setSnapshotJson(source.getSnapshotJson());
        release.setContentEnJson(source.getContentEnJson());
        release.setContentEsJson(source.getContentEsJson());
        release.setContentFrJson(source.getContentFrJson());
        release.setSourceReleaseId(source.getId());
        release.setPublishedBy(currentAdminId());
        release.setPublishedAt(LocalDateTime.now());
        releaseRepository.insert(release);
        releaseRepository.activate(release.getId());
        restoreWorkspace(source.getSnapshotJson());
        cacheService.invalidateHomeSectionFamily();
        return toDto(release, true);
    }

    @Transactional
    public HomePagePreviewTokenDto createPreviewToken() {
        releaseRepository.lockConfig();
        validateWorkspace();
        LocalDateTime now = LocalDateTime.now();
        releaseRepository.deleteExpiredPreviewTokens(now);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        HomePagePreviewToken token = new HomePagePreviewToken();
        token.setTokenHash(hashValue(rawToken));
        DraftBundle draft = draftBundle();
        token.setSnapshotJson(draft.snapshotJson());
        token.setContentEnJson(draft.contentEnJson());
        token.setContentEsJson(draft.contentEsJson());
        token.setContentFrJson(draft.contentFrJson());
        token.setExpiresAt(now.plusMinutes(PREVIEW_TTL_MINUTES));
        token.setIssuedBy(currentAdminId());
        releaseRepository.insertPreviewToken(token);
        HomePagePreviewTokenDto dto = new HomePagePreviewTokenDto();
        dto.setToken(rawToken);
        dto.setExpiresAt(token.getExpiresAt());
        return dto;
    }

    public StoreHomePageDto previewByToken(String rawToken, String locale) {
        if (rawToken == null || rawToken.length() < 32) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_PREVIEW_TOKEN_INVALID);
        }
        HomePagePreviewToken token = releaseRepository.findValidPreviewToken(
                hashValue(rawToken), LocalDateTime.now());
        if (token == null) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_PREVIEW_TOKEN_INVALID);
        }
        String contentJson = switch (locale) {
            case "es" -> token.getContentEsJson();
            case "fr" -> token.getContentFrJson();
            default -> token.getContentEnJson();
        };
        return storeContentService.readContentJson(contentJson, true);
    }

    @Transactional
    public void ensureInitialRelease() {
        SiteBuilderConfig config = releaseRepository.lockConfig();
        if (config != null && config.getActiveHomeReleaseId() != null) {
            return;
        }
        validateWorkspace();
        HomePageRelease release = buildRelease(draftBundle(), "初始首页版本", null);
        releaseRepository.insert(release);
        releaseRepository.activate(release.getId());
    }

    private HomePageRelease buildRelease(DraftBundle draft, String requestedName, Long sourceReleaseId) {
        int releaseNo = releaseRepository.nextReleaseNo();
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty()) {
            name = "首页发布 V" + releaseNo;
        }
        if (name.length() > 128) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_RELEASE_VALIDATION_FAILED);
        }
        HomePageRelease release = new HomePageRelease();
        release.setReleaseNo(releaseNo);
        release.setName(name);
        release.setSnapshotJson(draft.snapshotJson());
        release.setContentEnJson(draft.contentEnJson());
        release.setContentEsJson(draft.contentEsJson());
        release.setContentFrJson(draft.contentFrJson());
        release.setSourceReleaseId(sourceReleaseId);
        release.setPublishedBy(currentAdminId());
        release.setPublishedAt(LocalDateTime.now());
        return release;
    }

    private void validateWorkspace() {
        List<HomePageSection> enabled = sectionRepository.findEnabledOrderBySort();
        if (enabled.isEmpty()) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_RELEASE_VALIDATION_FAILED,
                    java.util.Map.of("reason", "at least one enabled section is required"));
        }
        long heroCount = enabled.stream().filter(section -> "hero".equals(section.getSectionType())).count();
        if (heroCount > 1) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_RELEASE_VALIDATION_FAILED,
                    java.util.Map.of("reason", "only one enabled hero section is allowed"));
        }
        for (HomePageSection section : enabled) {
            if (!"hero".equals(section.getSectionType())) {
                continue;
            }
            Long selectedBannerId = readBannerId(section.getDataJson());
            if (selectedBannerId != null) {
                Banner banner = bannerRepository.findById(selectedBannerId);
                if (banner == null || banner.getPosition() != BannerPosition.HERO) {
                    throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_RELEASE_VALIDATION_FAILED,
                            java.util.Map.of("reason", "selected hero banner is missing or has the wrong position"));
                }
            } else if (bannerRepository.listStoreActive(BannerPosition.HERO, LocalDateTime.now()).isEmpty()) {
                throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_RELEASE_VALIDATION_FAILED,
                        java.util.Map.of("reason", "hero requires a selected banner"));
            }
        }
    }

    private Long readBannerId(String dataJson) {
        if (dataJson == null) return null;
        try {
            JsonNode node = objectMapper.readTree(dataJson).get("banner_id");
            return node != null && node.canConvertToLong() && node.asLong() > 0 ? node.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String workspaceJson() {
        try {
            List<WorkspaceSection> snapshot = new ArrayList<>();
            for (HomePageSection section : sectionRepository.findAllOrderBySort()) {
                snapshot.add(new WorkspaceSection(section.getSectionType(), section.getEnabled(),
                        section.getSortOrder(), parseJson(section.getDataJson()), parseJson(section.getI18nJson()),
                        section.getLabel()));
            }
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SITE_BUILDER_INTERNAL_ERROR);
        }
    }

    private JsonNode parseJson(String raw) throws Exception {
        return raw == null ? null : objectMapper.readTree(raw);
    }

    private void restoreWorkspace(String snapshotJson) {
        try {
            WorkspaceSection[] snapshot = objectMapper.readValue(snapshotJson, WorkspaceSection[].class);
            List<HomePageSection> sections = new ArrayList<>();
            for (WorkspaceSection item : snapshot) {
                HomePageSection section = new HomePageSection();
                section.setSectionType(item.sectionType());
                section.setEnabled(item.enabled());
                section.setSortOrder(item.sortOrder());
                section.setDataJson(item.dataJson() == null ? null : objectMapper.writeValueAsString(item.dataJson()));
                section.setI18nJson(item.i18nJson() == null ? null : objectMapper.writeValueAsString(item.i18nJson()));
                section.setLabel(item.label());
                section.setVersion(0);
                sections.add(section);
            }
            sectionRepository.replaceAll(sections);
        } catch (Exception e) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SITE_BUILDER_INTERNAL_ERROR);
        }
    }

    private String contentJson(String locale) {
        try {
            StoreHomePageDto content = storeContentService.getDraftHome(locale);
            content.setPreview(false);
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SITE_BUILDER_INTERNAL_ERROR);
        }
    }

    private DraftBundle draftBundle() {
        return new DraftBundle(workspaceJson(), contentJson("en"), contentJson("es"), contentJson("fr"));
    }

    private boolean sameContent(HomePageRelease active, DraftBundle draft) {
        return sameJson(draft.snapshotJson(), active.getSnapshotJson())
                && sameJson(draft.contentEnJson(), active.getContentEnJson())
                && sameJson(draft.contentEsJson(), active.getContentEsJson())
                && sameJson(draft.contentFrJson(), active.getContentFrJson());
    }

    private boolean sameJson(String left, String right) {
        try {
            return objectMapper.readTree(left).equals(objectMapper.readTree(right));
        } catch (Exception e) {
            return false;
        }
    }

    private String draftRevision(DraftBundle draft) {
        String value = draft.snapshotJson().length() + ":" + draft.snapshotJson()
                + draft.contentEnJson().length() + ":" + draft.contentEnJson()
                + draft.contentEsJson().length() + ":" + draft.contentEsJson()
                + draft.contentFrJson().length() + ":" + draft.contentFrJson();
        return hashValue(value);
    }

    private String hashValue(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Long currentAdminId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || principal.subject() == null) {
            return null;
        }
        try {
            return Long.parseLong(principal.subject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private HomePageReleaseDto toDto(HomePageRelease release, boolean active) {
        HomePageReleaseDto dto = new HomePageReleaseDto();
        dto.setId(release.getId());
        dto.setReleaseNo(release.getReleaseNo());
        dto.setName(release.getName());
        dto.setSourceReleaseId(release.getSourceReleaseId());
        dto.setPublishedBy(release.getPublishedBy());
        dto.setPublishedAt(release.getPublishedAt());
        dto.setActive(active);
        return dto;
    }

    private record WorkspaceSection(String sectionType, Boolean enabled, Integer sortOrder,
                                    JsonNode dataJson, JsonNode i18nJson, String label) {
    }

    private record DraftBundle(String snapshotJson, String contentEnJson,
                               String contentEsJson, String contentFrJson) {
    }
}
