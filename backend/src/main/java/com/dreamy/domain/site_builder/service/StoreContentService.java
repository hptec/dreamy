package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.banner.service.StoreBannerService;
import com.dreamy.domain.collection.service.StoreCollectionService;
import com.dreamy.domain.product.service.StoreProductService;
import com.dreamy.domain.product.service.RecommendationService;
import com.dreamy.domain.wedding.service.StoreWeddingService;
import com.dreamy.domain.site_builder.entity.Announcement;
import com.dreamy.domain.site_builder.entity.FooterColumn;
import com.dreamy.domain.site_builder.entity.FooterLink;
import com.dreamy.domain.site_builder.entity.HomePageSection;
import com.dreamy.domain.site_builder.entity.NavigationItem;
import com.dreamy.domain.site_builder.repository.AnnouncementRepository;
import com.dreamy.domain.site_builder.repository.FooterRepository;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.domain.site_builder.repository.NavigationItemRepository;
import com.dreamy.dto.StoreCollectionGroup.StoreCollectionItem;
import com.dreamy.dto.StoreMarketingDtos.StoreBanner;
import com.dreamy.dto.StoreMarketingDtos.StoreRealWedding;
import com.dreamy.dto.StoreProductCard;
import com.dreamy.dto.SiteBuilderDtos.StoreAnnouncementDto;
import com.dreamy.dto.SiteBuilderDtos.StoreAnnouncementListDto;
import com.dreamy.dto.SiteBuilderDtos.StoreFooterColumnDto;
import com.dreamy.dto.SiteBuilderDtos.StoreFooterDto;
import com.dreamy.dto.SiteBuilderDtos.StoreFooterLinkDto;
import com.dreamy.dto.SiteBuilderDtos.StoreHomeSectionDto;
import com.dreamy.dto.SiteBuilderDtos.StoreHomePageDto;
import com.dreamy.dto.SiteBuilderDtos.StoreNavigationDto;
import com.dreamy.dto.SiteBuilderDtos.StoreNavigationItemDto;
import com.dreamy.enums.BannerPosition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huihao.page.Paginated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消费端内容聚合服务（FLOW-SB05~SB08）。
 * 按 locale 扁平化 i18n_json（三层回退：locale→en→主表字段）。
 * 跨域调用失败降级空数据（DG-01~04）。
 */
@Service
public class StoreContentService {

    private static final Logger log = LoggerFactory.getLogger(StoreContentService.class);

    private final HomePageSectionRepository homeSectionRepository;
    private final NavigationItemRepository navigationRepository;
    private final FooterRepository footerRepository;
    private final AnnouncementRepository announcementRepository;
    private final ObjectMapper objectMapper;
    private final StoreBannerService bannerService;
    private final StoreCollectionService collectionService;
    private final StoreProductService productService;
    private final RecommendationService recommendationService;
    private final StoreWeddingService weddingService;
    private final Clock clock;
    private final SiteBuilderCacheService cache;

    public StoreContentService(HomePageSectionRepository homeSectionRepository,
                               NavigationItemRepository navigationRepository,
                               FooterRepository footerRepository,
                               AnnouncementRepository announcementRepository,
                               ObjectMapper objectMapper,
                               StoreBannerService bannerService,
                               StoreCollectionService collectionService,
                               StoreProductService productService,
                               RecommendationService recommendationService,
                               StoreWeddingService weddingService,
                               Clock clock, SiteBuilderCacheService cache) {
        this.homeSectionRepository = homeSectionRepository;
        this.navigationRepository = navigationRepository;
        this.footerRepository = footerRepository;
        this.announcementRepository = announcementRepository;
        this.objectMapper = objectMapper;
        this.bannerService = bannerService;
        this.collectionService = collectionService;
        this.productService = productService;
        this.recommendationService = recommendationService;
        this.weddingService = weddingService;
        this.clock = clock;
        this.cache = cache;
    }

    public StoreHomePageDto getHome(String locale) {
        SiteBuilderCacheService.Lookup lookup = cache.lookup(SiteBuilderCacheService.Family.HOME, locale);
        if (lookup.value() instanceof StoreHomePageDto hit) return hit;
        List<HomePageSection> sections = homeSectionRepository.findEnabledOrderBySort();
        List<StoreHomeSectionDto> dtos = new ArrayList<>();
        for (HomePageSection section : sections) {
            Map<String, Object> data = deriveSectionData(section, locale);
            if ("hero".equals(section.getSectionType()) && data.isEmpty()) {
                continue;
            }
            StoreHomeSectionDto dto = new StoreHomeSectionDto();
            dto.setSectionType(section.getSectionType());
            dto.setData(data);
            dtos.add(dto);
        }
        StoreHomePageDto result = new StoreHomePageDto();
        result.setSections(dtos);
        cache.put(lookup, result);
        return result;
    }

    private Map<String, Object> deriveSectionData(HomePageSection section, String locale) {
        Map<String, Object> data = new HashMap<>();
        switch (section.getSectionType()) {
            case "hero":
                data.putAll(deriveHeroData(locale));
                break;
            case "newsletter":
                Map<String, Object> i18n = resolveI18n(section.getI18nJson(), locale, null);
                data.putAll(i18n);
                break;
            case "theme_cards":
                data.putAll(deriveThemeCardsData(section, locale));
                break;
            case "product_rail":
                data.putAll(deriveProductRailData(section, locale));
                break;
            case "editorial_feature":
                data.putAll(deriveEditorialFeatureData(section, locale));
                break;
            case "custom":
            default:
                if (section.getDataJson() != null) {
                    try {
                        JsonNode dataJson = objectMapper.readTree(section.getDataJson());
                        data.putAll(objectMapper.convertValue(dataJson, Map.class));
                    } catch (Exception e) {
                        log.warn("[StoreContent] parse data_json failed section_id={}", section.getId());
                    }
                }
                Map<String, Object> sec = resolveI18n(section.getI18nJson(), locale, null);
                data.putAll(sec);
                break;
        }
        return data;
    }

    /**
     * Hero 派生：调用 StoreBannerService.list(HERO, locale) 返回全部有效 banner，按 sort/id 顺序轮播。
     * banners[] 是权威结构；同时保留首张 banner 的扁平字段，兼容前后端滚动部署。
     * 无有效 Banner 或调用失败时返回空数据，由首页聚合省略 Hero 区块。
     */
    private Map<String, Object> deriveHeroData(String locale) {
        Map<String, Object> hero = new HashMap<>();
        try {
            List<StoreBanner> banners = bannerService.list(BannerPosition.HERO, locale);
            if (!banners.isEmpty()) {
                List<Map<String, Object>> slides = banners.stream().map(this::toHeroSlide).toList();
                hero.put("banners", slides);

                // 兼容旧消费端：滚动部署期间仍可读取首张扁平字段。
                StoreBanner first = banners.getFirst();
                hero.put("title", first.title());
                hero.put("subtitle", first.subtitle());
                hero.put("cta_text", first.ctaText());
                hero.put("cta_link", first.ctaLink());
                hero.put("cta_text_secondary", first.ctaTextSecondary());
                hero.put("cta_link_secondary", first.ctaLinkSecondary());
                hero.put("image_url", first.imageUrl());
                log.debug("[StoreContent] Hero data derived from {} active banners", banners.size());
            }
        } catch (Exception e) {
            log.warn("[StoreContent] Hero BannerService unavailable, omit section (DG-01)", e);
        }
        return hero;
    }

    private Map<String, Object> toHeroSlide(StoreBanner banner) {
        Map<String, Object> slide = new HashMap<>();
        slide.put("id", banner.id());
        slide.put("title", banner.title());
        slide.put("subtitle", banner.subtitle());
        slide.put("cta_text", banner.ctaText());
        slide.put("cta_link", banner.ctaLink());
        slide.put("cta_text_secondary", banner.ctaTextSecondary());
        slide.put("cta_link_secondary", banner.ctaLinkSecondary());
        slide.put("image_url", banner.imageUrl());
        return slide;
    }

    /**
     * ThemeCards 派生：读取 Theme 集合分组，封面取集合内排序最前的已上架商品主图。
     * data_json config：{ mode: "auto", count: 6 } 或
     * { mode: "manual", collection_ids: [3, 1] }。手动模式严格保留运营选择顺序。
     * 失败降级空 cards + WARN（DG-02）。
     */
    private Map<String, Object> deriveThemeCardsData(HomePageSection section, String locale) {
        Map<String, Object> data = new HashMap<>();
        data.putAll(resolveI18n(section.getI18nJson(), locale, null));
        // limit 是旧配置字段，只作为 count 缺失时的向后兼容读取。
        int legacyLimit = readBoundedIntConfig(section.getDataJson(), "limit", 6, 1, 8);
        int count = readBoundedIntConfig(section.getDataJson(), "count", legacyLimit, 1, 8);
        String mode = readStringConfig(section.getDataJson(), "mode", "auto");
        try {
            List<StoreCollectionItem> themes = collectionService.listThemeCollections(locale);
            List<StoreCollectionItem> selected;
            if ("manual".equals(mode)) {
                Map<Long, StoreCollectionItem> byId = themes.stream()
                        .collect(Collectors.toMap(StoreCollectionItem::id, item -> item));
                selected = readLongListConfig(section.getDataJson(), "collection_ids").stream()
                        .map(byId::get)
                        .filter(java.util.Objects::nonNull)
                        .toList();
            } else {
                selected = themes.stream().limit(count).toList();
            }
            List<Map<String, Object>> cards = new ArrayList<>();
            for (StoreCollectionItem theme : selected) {
                Map<String, Object> card = new HashMap<>();
                card.put("id", theme.id());
                card.put("name", theme.name());
                card.put("product_count", theme.productCount());
                card.put("image_url", theme.imageUrl());
                cards.add(card);
            }
            data.put("cards", cards);
            log.debug("[StoreContent] ThemeCards derived {} theme collections", cards.size());
        } catch (Exception e) {
            log.warn("[StoreContent] ThemeCards CollectionService unavailable, degrade to empty (DG-02)", e);
            data.put("cards", List.of());
        }
        return data;
    }

    /**
     * ProductRail 派生：调用 StoreProductService.listProducts 取商品卡片。
     * data_json config：{ source, limit, product_ids, category_id, sort }。
     * sort 仅对 category 来源生效；人工推荐严格保留 product_ids 顺序。
     * 失败降级空 products + WARN（DG-03）。
     */
    private Map<String, Object> deriveProductRailData(HomePageSection section, String locale) {
        Map<String, Object> data = new HashMap<>();
        data.putAll(resolveI18n(section.getI18nJson(), locale, null));
        int limit = readBoundedIntConfig(section.getDataJson(), "limit", 8, 1, 12);
        String source = readStringConfig(section.getDataJson(), "source", "new_arrival");
        Long categoryId = readLongConfig(section.getDataJson(), "category_id");
        String sort = normalizeProductSort(readStringConfig(section.getDataJson(), "sort", "newest"));
        try {
            List<StoreProductCard> cards = switch (source) {
                case "best_seller" -> recommendationService.recommend(
                        "best_sellers", null, null, limit, locale);
                case "recommend" -> productService.listPublishedCardsByIds(
                        readLongListConfig(section.getDataJson(), "product_ids"), limit, locale);
                case "category" -> categoryId == null
                        ? List.of()
                        : productService.listProducts(new StoreProductService.ListQuery(
                                locale, 1, limit, categoryId, null, null, null, null, null, sort, Map.of())).getData();
                default -> recommendationService.recommend(
                        "new_arrivals", null, null, limit, locale);
            };
            List<Map<String, Object>> products = cards.stream()
                    .map(p -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", p.id());
                        m.put("slug", p.slug());
                        m.put("name", p.name());
                        m.put("price", p.price());
                        m.put("image_url", p.imageUrl());
                        m.put("is_new", p.isNew());
                        m.put("is_best", p.isBest());
                        return m;
                    })
                    .collect(Collectors.toList());
            data.put("products", products);
            log.debug("[StoreContent] ProductRail derived {} products", products.size());
        } catch (Exception e) {
            log.warn("[StoreContent] ProductRail ProductService unavailable, degrade to empty (DG-03)", e);
            data.put("products", List.of());
        }
        return data;
    }

    /**
     * EditorialFeature 派生：调用 StoreWeddingService.page 取真实婚礼故事。
     * data_json config：{ limit: 3 }。底层接口唯一可靠语义是 wedding_date DESC，
     * 因此这里不接受也不模拟人气、随机或升序排序。
     * 失败降级空 stories + WARN（DG-04）。
     */
    private Map<String, Object> deriveEditorialFeatureData(HomePageSection section, String locale) {
        Map<String, Object> data = new HashMap<>();
        data.putAll(resolveI18n(section.getI18nJson(), locale, null));
        int limit = readBoundedIntConfig(section.getDataJson(), "limit", 3, 1, 6);
        try {
            Paginated<StoreRealWedding> page = weddingService.page(1, limit, locale);
            List<Map<String, Object>> stories = page.getData().stream()
                    .map(w -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", w.id());
                        m.put("couple", w.couple());
                        m.put("location", w.location());
                        m.put("theme", w.theme());
                        m.put("wedding_date", w.weddingDate());
                        m.put("cover", w.cover());
                        m.put("title", w.title());
                        return m;
                    })
                    .collect(Collectors.toList());
            data.put("stories", stories);
            log.debug("[StoreContent] EditorialFeature derived {} stories", stories.size());
        } catch (Exception e) {
            log.warn("[StoreContent] EditorialFeature WeddingService unavailable, degrade to empty (DG-04)", e);
            data.put("stories", List.of());
        }
        return data;
    }

    private int readIntConfig(String dataJson, String key, int defaultValue) {
        if (dataJson == null) return defaultValue;
        try {
            Integer value = parseInteger(objectMapper.readTree(dataJson).get(key));
            return value == null ? defaultValue : value;
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private int readBoundedIntConfig(String dataJson, String key, int defaultValue, int min, int max) {
        int value = readIntConfig(dataJson, key, defaultValue);
        return value >= min && value <= max ? value : defaultValue;
    }

    private Long readLongConfig(String dataJson, String key) {
        if (dataJson == null) return null;
        try {
            return parseLong(objectMapper.readTree(dataJson).get(key));
        } catch (Exception ignored) {
        }
        return null;
    }

    private String readStringConfig(String dataJson, String key, String defaultValue) {
        if (dataJson == null) return defaultValue;
        try {
            JsonNode node = objectMapper.readTree(dataJson);
            if (node.has(key) && node.get(key).isTextual()) {
                return node.get(key).asText();
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private List<Long> readLongListConfig(String dataJson, String key) {
        if (dataJson == null) return List.of();
        try {
            JsonNode node = objectMapper.readTree(dataJson).get(key);
            if (node == null || !node.isArray()) return List.of();
            LinkedHashSet<Long> result = new LinkedHashSet<>();
            for (JsonNode item : node) {
                Long value = parseLong(item);
                if (value != null && value > 0) {
                    result.add(value);
                }
            }
            return List.copyOf(result);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Integer parseInteger(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isIntegralNumber() && node.canConvertToInt()) {
            return node.intValue();
        }
        if (node.isTextual()) {
            try {
                return Integer.valueOf(node.textValue().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.valueOf(node.textValue().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeProductSort(String sort) {
        return switch (sort) {
            case "new" -> "newest";
            case "best" -> "recommended";
            case "newest", "price_asc", "price_desc", "recommended" -> sort;
            default -> "newest";
        };
    }

    public StoreNavigationDto getNavigation(String locale) {
        SiteBuilderCacheService.Lookup lookup = cache.lookup(SiteBuilderCacheService.Family.NAVIGATION, locale);
        if (lookup.value() instanceof StoreNavigationDto hit) return hit;
        List<NavigationItem> items = navigationRepository.findEnabledOrderBySort();
        List<StoreNavigationItemDto> dtos = items.stream().map(item -> {
            StoreNavigationItemDto dto = new StoreNavigationItemDto();
            dto.setId(item.getId());
            dto.setParentId(item.getParentId());
            dto.setLabel(resolveI18nField(item.getI18nJson(), locale, "label", item.getLabel()));
            dto.setUrl(item.getUrl());
            dto.setTarget(item.getTarget());
            dto.setLinkType(item.getLinkType());
            dto.setTaxonomyId(item.getTaxonomyId());
            dto.setSortOrder(item.getSortOrder());
            if (item.getMegaMenuJson() != null) {
                try {
                    dto.setMegaMenu(objectMapper.readTree(item.getMegaMenuJson()));
                } catch (Exception e) {
                    log.warn("[StoreContent] parse mega_menu_json failed item_id={}", item.getId());
                }
            }
            return dto;
        }).collect(Collectors.toList());
        StoreNavigationDto result = new StoreNavigationDto();
        result.setItems(dtos);
        cache.put(lookup, result);
        return result;
    }

    public StoreFooterDto getFooter(String locale) {
        SiteBuilderCacheService.Lookup lookup = cache.lookup(SiteBuilderCacheService.Family.FOOTER, locale);
        if (lookup.value() instanceof StoreFooterDto hit) return hit;
        List<FooterColumn> columns = footerRepository.findAllColumnsOrderBySort();
        List<FooterLink> links = footerRepository.findAllLinksOrderBySort();
        Map<Long, List<FooterLink>> linksByColumn = links.stream()
                .collect(Collectors.groupingBy(FooterLink::getColumnId));
        List<StoreFooterColumnDto> columnDtos = columns.stream().map(c -> {
            StoreFooterColumnDto cd = new StoreFooterColumnDto();
            cd.setId(c.getId());
            cd.setTitle(resolveI18nField(c.getI18nJson(), locale, "title", c.getTitle()));
            cd.setSortOrder(c.getSortOrder());
            List<StoreFooterLinkDto> linkDtos = linksByColumn.getOrDefault(c.getId(), List.of()).stream()
                    .map(l -> {
                        StoreFooterLinkDto ld = new StoreFooterLinkDto();
                        ld.setId(l.getId());
                        ld.setLabel(resolveI18nField(l.getI18nJson(), locale, "label", l.getLabel()));
                        ld.setUrl(l.getUrl());
                        ld.setTarget(l.getTarget());
                        ld.setSortOrder(l.getSortOrder());
                        return ld;
                    }).collect(Collectors.toList());
            cd.setLinks(linkDtos);
            return cd;
        }).collect(Collectors.toList());
        StoreFooterDto result = new StoreFooterDto();
        result.setColumns(columnDtos);
        cache.put(lookup, result);
        return result;
    }

    public StoreAnnouncementListDto getAnnouncements(String locale) {
        SiteBuilderCacheService.Lookup lookup = cache.lookup(SiteBuilderCacheService.Family.ANNOUNCEMENTS, locale);
        if (lookup.value() instanceof StoreAnnouncementListDto hit) return hit;
        List<Announcement> entities = announcementRepository.findActiveByTimeWindow(LocalDateTime.now(clock));
        List<StoreAnnouncementDto> dtos = entities.stream().map(a -> {
            StoreAnnouncementDto dto = new StoreAnnouncementDto();
            dto.setId(a.getId());
            dto.setPriority(a.getPriority());
            dto.setContent(resolveI18nField(a.getContentI18nJson(), locale, "content", a.getContent()));
            dto.setStartAt(a.getStartAt());
            dto.setEndAt(a.getEndAt());
            return dto;
        }).collect(Collectors.toList());
        StoreAnnouncementListDto result = new StoreAnnouncementListDto();
        result.setAnnouncements(dtos);
        cache.put(lookup, result);
        return result;
    }

    private Map<String, Object> resolveI18n(String i18nJson, String locale, String mainField) {
        if (i18nJson == null) return Map.of();
        try {
            JsonNode node = objectMapper.readTree(i18nJson);
            JsonNode localeNode = node.has(locale) ? node.get(locale) : null;
            if (localeNode == null || localeNode.isNull() || localeNode.size() == 0) {
                localeNode = node.has("en") ? node.get("en") : null;
            }
            if (localeNode != null && !localeNode.isNull()) {
                return objectMapper.convertValue(localeNode, Map.class);
            }
        } catch (Exception e) {
            log.warn("[StoreContent] parse i18n_json failed", e);
        }
        return Map.of();
    }

    private String resolveI18nField(String i18nJson, String locale, String field, String fallback) {
        if (i18nJson == null) return fallback != null ? fallback : "";
        try {
            JsonNode node = objectMapper.readTree(i18nJson);
            JsonNode localeNode = node.has(locale) ? node.get(locale) : null;
            if (localeNode != null && localeNode.has(field) && !localeNode.get(field).isNull()) {
                return localeNode.get(field).asText();
            }
            JsonNode enNode = node.has("en") ? node.get("en") : null;
            if (enNode != null && enNode.has(field) && !enNode.get(field).isNull()) {
                return enNode.get(field).asText();
            }
        } catch (Exception e) {
            log.warn("[StoreContent] parse i18n_json field failed field={}", field, e);
        }
        return fallback != null ? fallback : "";
    }
}
