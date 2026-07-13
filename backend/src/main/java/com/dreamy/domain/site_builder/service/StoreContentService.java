package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.banner.service.StoreBannerService;
import com.dreamy.domain.category.service.StoreCategoryService;
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
import com.dreamy.dto.StoreCategoryNode;
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
    private final StoreCategoryService categoryService;
    private final StoreProductService productService;
    private final RecommendationService recommendationService;
    private final StoreWeddingService weddingService;

    public StoreContentService(HomePageSectionRepository homeSectionRepository,
                               NavigationItemRepository navigationRepository,
                               FooterRepository footerRepository,
                               AnnouncementRepository announcementRepository,
                               ObjectMapper objectMapper,
                               StoreBannerService bannerService,
                               StoreCategoryService categoryService,
                               StoreProductService productService,
                               RecommendationService recommendationService,
                               StoreWeddingService weddingService) {
        this.homeSectionRepository = homeSectionRepository;
        this.navigationRepository = navigationRepository;
        this.footerRepository = footerRepository;
        this.announcementRepository = announcementRepository;
        this.objectMapper = objectMapper;
        this.bannerService = bannerService;
        this.categoryService = categoryService;
        this.productService = productService;
        this.recommendationService = recommendationService;
        this.weddingService = weddingService;
    }

    public StoreHomePageDto getHome(String locale) {
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
     * Hero 派生：调用 StoreBannerService.list(HERO, locale) 取首个 banner。
     * KD-14：返回 title/subtitle/cta_text/cta_link/cta_text_secondary/cta_link_secondary/image_url。
     * 无有效 Banner 或调用失败时返回空数据，由首页聚合省略 Hero 区块。
     */
    private Map<String, Object> deriveHeroData(String locale) {
        Map<String, Object> hero = new HashMap<>();
        try {
            List<StoreBanner> banners = bannerService.list(BannerPosition.HERO, locale);
            if (!banners.isEmpty()) {
                StoreBanner banner = banners.get(0);
                hero.put("title", banner.title());
                hero.put("subtitle", banner.subtitle());
                hero.put("cta_text", banner.ctaText());
                hero.put("cta_link", banner.ctaLink());
                hero.put("cta_text_secondary", banner.ctaTextSecondary());
                hero.put("cta_link_secondary", banner.ctaLinkSecondary());
                hero.put("image_url", banner.imageUrl());
                log.debug("[StoreContent] Hero data derived from Banner id={}", banner.id());
            }
        } catch (Exception e) {
            log.warn("[StoreContent] Hero BannerService unavailable, omit section (DG-01)", e);
        }
        return hero;
    }

    /**
     * ThemeCards 派生：调用 StoreCategoryService.listTree(locale) 取根分类（level=0）。
     * data_json config：{ mode: "auto", count: 6 } 或
     * { mode: "manual", category_ids: [3, 1] }。手动模式严格保留运营选择顺序。
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
            List<StoreCategoryNode> tree = categoryService.listTree(locale);
            List<StoreCategoryNode> selected;
            if ("manual".equals(mode)) {
                Map<Long, StoreCategoryNode> byId = new HashMap<>();
                flattenCategories(tree).forEach(node -> byId.put(node.id(), node));
                selected = readLongListConfig(section.getDataJson(), "category_ids").stream()
                        .map(byId::get)
                        .filter(java.util.Objects::nonNull)
                        .toList();
            } else {
                selected = tree.stream().limit(count).toList();
            }
            List<Map<String, Object>> cards = new ArrayList<>();
            for (StoreCategoryNode node : selected) {
                Map<String, Object> card = new HashMap<>();
                card.put("id", node.id());
                card.put("name", node.name());
                card.put("product_count", node.productCount());
                cards.add(card);
            }
            data.put("cards", cards);
            log.debug("[StoreContent] ThemeCards derived {} categories", cards.size());
        } catch (Exception e) {
            log.warn("[StoreContent] ThemeCards CategoryService unavailable, degrade to empty (DG-02)", e);
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

    private List<StoreCategoryNode> flattenCategories(List<StoreCategoryNode> roots) {
        List<StoreCategoryNode> result = new ArrayList<>();
        if (roots == null) return result;
        for (StoreCategoryNode node : roots) {
            result.add(node);
            if (node.children() != null) {
                result.addAll(flattenCategories(node.children()));
            }
        }
        return result;
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
        return result;
    }

    public StoreFooterDto getFooter(String locale) {
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
        return result;
    }

    public StoreAnnouncementListDto getAnnouncements(String locale) {
        List<Announcement> entities = announcementRepository.findActiveByTimeWindow(LocalDateTime.now());
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
