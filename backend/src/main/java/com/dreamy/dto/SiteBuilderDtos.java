package com.dreamy.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * site_builder 域 DTO（admin 端）。
 */
public class SiteBuilderDtos {

    @Data
    public static class HomePageSectionDto {
        private Long id;
        private String sectionType;
        private Boolean enabled;
        private Integer sortOrder;
        @JsonRawValue
        private String dataJson;
        @JsonRawValue
        private String i18nJson;
        private String label;
        private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class HomePageSectionUpsert {
        private String sectionType;
        private Boolean enabled;
        private Integer sortOrder;
        private JsonNode dataJson;
        private JsonNode i18nJson;
        private String label;
        private Integer version;
    }

    @Data
    public static class HomePageDraftItem extends HomePageSectionUpsert {
        private Long id;
    }

    @Data
    public static class HomePageDraftSaveRequest {
        private List<HomePageDraftItem> items;
    }

    @Data
    public static class SortItem {
        private Long id;
        private Integer sortOrder;
    }

    @Data
    public static class SortRequest {
        private java.util.List<SortItem> items;
    }

    @Data
    public static class ToggleRequest {
        private Boolean enabled;
    }

    @Data
    public static class HomePagePublicationStatusDto {
        private boolean hasPublishedRelease;
        private boolean draftModified;
        private Long activeReleaseId;
        private Integer activeReleaseNo;
        private String activeReleaseName;
        private LocalDateTime publishedAt;
        private String draftRevision;
    }

    @Data
    public static class HomePageReleaseDto {
        private Long id;
        private Integer releaseNo;
        private String name;
        private Long sourceReleaseId;
        private Long publishedBy;
        private LocalDateTime publishedAt;
        private boolean active;
    }

    @Data
    public static class HomePagePublishRequest {
        private String name;
        private String expectedDraftRevision;
    }

    @Data
    public static class HomePagePreviewTokenDto {
        private String token;
        private LocalDateTime expiresAt;
    }

    @Data
    public static class NavigationItemDto {
        private Long id;
        private Long parentId;
        private String label;
        private String labelI18nKey;
        private String url;
        private String target;
        private String linkType;
        private Long taxonomyId;
        @JsonRawValue
        private String megaMenuJson;
        @JsonRawValue
        private String i18nJson;
        private Integer sortOrder;
        private Boolean enabled;
        private Integer version;
    }

    @Data
    public static class NavigationItemUpsert {
        private Long id;
        private Long parentId;
        private String label;
        private String labelI18nKey;
        private String url;
        private String target;
        private String linkType;
        private Long taxonomyId;
        private JsonNode megaMenuJson;
        private JsonNode i18nJson;
        private Integer sortOrder;
        private Boolean enabled;
    }

    @Data
    public static class NavigationSaveRequest {
        private java.util.List<NavigationItemUpsert> items;
        private Integer version;
    }

    @Data
    public static class FooterLinkDto {
        private Long id;
        private Long columnId;
        private String label;
        private String url;
        private String target;
        @JsonRawValue
        private String i18nJson;
        private Integer sortOrder;
    }

    @Data
    public static class FooterColumnDto {
        private Long id;
        private String title;
        @JsonRawValue
        private String i18nJson;
        private Integer sortOrder;
        private Boolean enabled;
        private java.util.List<FooterLinkDto> links;
    }

    @Data
    public static class FooterLinkUpsert {
        private Long id;
        private Long columnId;
        private String label;
        private String url;
        private String target;
        private JsonNode i18nJson;
        private Integer sortOrder;
    }

    @Data
    public static class FooterColumnUpsert {
        private Long id;
        private String title;
        private JsonNode i18nJson;
        private Integer sortOrder;
        private Boolean enabled;
        private java.util.List<FooterLinkUpsert> links;
    }

    @Data
    public static class FooterSaveRequest {
        private java.util.List<FooterColumnUpsert> columns;
        private Integer version;
    }

    @Data
    public static class AnnouncementDto {
        private Long id;
        private Boolean enabled;
        private Integer priority;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private String content;
        @JsonRawValue
        private String contentI18nJson;
        @JsonRawValue
        private String i18nJson;
        private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class AnnouncementUpsert {
        private Boolean enabled;
        private Integer priority;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private JsonNode contentI18nJson;
        private JsonNode i18nJson;
        private Integer version;
    }

    // ===== 消费端 DTO（扁平化后） =====

    @Data
    public static class StoreHomeSectionDto {
        private String sectionType;
        private Object data;
    }

    @Data
    public static class StoreHomePageDto {
        private List<StoreHomeSectionDto> sections;
        private Integer releaseNo;
        private Boolean preview;
    }

    @Data
    public static class StoreNavigationItemDto {
        private Long id;
        private Long parentId;
        private String label;
        private String url;
        private String target;
        private String linkType;
        private Long taxonomyId;
        private Object megaMenu;
        private Integer sortOrder;
    }

    @Data
    public static class StoreNavigationDto {
        private java.util.List<StoreNavigationItemDto> items;
    }

    @Data
    public static class StoreFooterLinkDto {
        private Long id;
        private String label;
        private String url;
        private String target;
        private Integer sortOrder;
    }

    @Data
    public static class StoreFooterColumnDto {
        private Long id;
        private String title;
        private Integer sortOrder;
        private java.util.List<StoreFooterLinkDto> links;
    }

    @Data
    public static class StoreFooterDto {
        private java.util.List<StoreFooterColumnDto> columns;
    }

    @Data
    public static class StoreAnnouncementDto {
        private Long id;
        private Integer priority;
        private String content;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
    }

    @Data
    public static class StoreAnnouncementListDto {
        private java.util.List<StoreAnnouncementDto> announcements;
    }

    @Data
    public static class NewsletterSubscribeRequest {
        private String email;
        private String source;
        private String locale;
    }
}
