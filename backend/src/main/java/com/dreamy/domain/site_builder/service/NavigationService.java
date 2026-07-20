package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.domain.collection.repository.CollectionRepository;
import com.dreamy.domain.guide.repository.GuideRepository;
import com.dreamy.domain.lookbook.repository.LookbookRepository;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.site_builder.entity.NavigationItem;
import com.dreamy.domain.site_builder.repository.NavigationItemRepository;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.domain.wedding.repository.RealWeddingRepository;
import com.dreamy.dto.SiteBuilderDtos.LinkOptionDto;
import com.dreamy.dto.SiteBuilderDtos.NavigationItemDto;
import com.dreamy.dto.SiteBuilderDtos.NavigationItemUpsert;
import com.dreamy.dto.SiteBuilderDtos.NavigationSaveRequest;
import com.dreamy.enums.ContentStatus;
import com.dreamy.enums.LinkType;
import com.dreamy.enums.NavPageKey;
import com.dreamy.enums.ProductStatus;
import com.dreamy.enums.PublishStatus;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导航服务（FLOW-SB02）。KD-4 保存即发布（整体替换）。
 * link_type 扩展后按类型校验：custom→url 非空；page→page_key 合法；引用型→ref_id 跨域存在性。
 */
@Service
public class NavigationService {

    private final NavigationItemRepository repository;
    private final ObjectMapper objectMapper;
    private final CacheInvalidationTaskService cacheTasks;
    private final CategoryRepository categoryRepository;
    private final CollectionRepository collectionRepository;
    private final ProductRepository productRepository;
    private final BlogPostRepository blogPostRepository;
    private final RealWeddingRepository weddingRepository;
    private final LookbookRepository lookbookRepository;
    private final GuideRepository guideRepository;

    public NavigationService(NavigationItemRepository repository,
                             ObjectMapper objectMapper, CacheInvalidationTaskService cacheTasks,
                             CategoryRepository categoryRepository,
                             CollectionRepository collectionRepository,
                             ProductRepository productRepository,
                             BlogPostRepository blogPostRepository,
                             RealWeddingRepository weddingRepository,
                             LookbookRepository lookbookRepository,
                             GuideRepository guideRepository) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cacheTasks = cacheTasks;
        this.categoryRepository = categoryRepository;
        this.collectionRepository = collectionRepository;
        this.productRepository = productRepository;
        this.blogPostRepository = blogPostRepository;
        this.weddingRepository = weddingRepository;
        this.lookbookRepository = lookbookRepository;
        this.guideRepository = guideRepository;
    }

    public List<NavigationItemDto> list() {
        return repository.findAllOrderBySort().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** link-options 下拉数据源：引用型按域查已发布/启用记录，keyword 过滤（上限 50）。 */
    public List<LinkOptionDto> linkOptions(LinkType type, String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        return switch (type) {
            case CATEGORY -> categoryRepository.listAll().stream()
                    .filter(c -> kw.isEmpty() || (c.getName() != null && c.getName().toLowerCase().contains(kw.toLowerCase())))
                    .limit(50)
                    .map(c -> new LinkOptionDto(c.getId(), c.getName(), "L" + c.getLevel()))
                    .collect(Collectors.toList());
            case COLLECTION -> collectionRepository.listEnabled(null).stream()
                    .filter(c -> kw.isEmpty() || (c.getName() != null && c.getName().toLowerCase().contains(kw.toLowerCase())))
                    .limit(50)
                    .map(c -> new LinkOptionDto(c.getId(), c.getName(), null))
                    .collect(Collectors.toList());
            case PRODUCT -> productRepository.pageAdminList(
                            new ProductRepository.AdminFilter(ProductStatus.PUBLISHED, null, kw.isEmpty() ? null : kw), 1, 50)
                    .getRecords().stream()
                    .map(p -> new LinkOptionDto(p.getId(), p.getName(), p.getSlug()))
                    .collect(Collectors.toList());
            case BLOG_POST -> blogPostRepository.pageAdmin(ContentStatus.PUBLISHED, kw.isEmpty() ? null : kw, 1, 50)
                    .getRecords().stream()
                    .map(b -> new LinkOptionDto(b.getId(), b.getTitle(), b.getSlug()))
                    .collect(Collectors.toList());
            case REAL_WEDDING -> weddingRepository.pageAdmin(PublishStatus.PUBLISHED, 1, 50)
                    .getRecords().stream()
                    .map(w -> new LinkOptionDto(w.getId(), w.getCouple(), w.getTitle()))
                    .collect(Collectors.toList());
            case LOOKBOOK -> lookbookRepository.listAdmin(PublishStatus.PUBLISHED).stream()
                    .filter(l -> kw.isEmpty() || (l.getTitle() != null && l.getTitle().toLowerCase().contains(kw.toLowerCase())))
                    .limit(50)
                    .map(l -> new LinkOptionDto(l.getId(), l.getTitle(), l.getTheme()))
                    .collect(Collectors.toList());
            case GUIDE -> guideRepository.listAdmin(PublishStatus.PUBLISHED).stream()
                    .filter(g -> kw.isEmpty() || (g.getTitle() != null && g.getTitle().toLowerCase().contains(kw.toLowerCase())))
                    .limit(50)
                    .map(g -> new LinkOptionDto(g.getId(), g.getTitle(), g.getPhase()))
                    .collect(Collectors.toList());
            default -> List.of();
        };
    }

    @Transactional
    public List<NavigationItemDto> save(NavigationSaveRequest request) {
        if (request.getItems() == null) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "items"));
        }
        validateCycle(request.getItems());
        validateRefs(request.getItems());

        List<Long> upsertedIds = request.getItems().stream()
                .map(NavigationItemUpsert::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        repository.deleteByIdsNotIn(upsertedIds);

        for (NavigationItemUpsert upsert : request.getItems()) {
            NavigationItem entity;
            if (upsert.getId() != null) {
                entity = repository.findAllOrderBySort().stream()
                        .filter(e -> e.getId().equals(upsert.getId()))
                        .findFirst()
                        .orElse(new NavigationItem());
            } else {
                entity = new NavigationItem();
                entity.setVersion(0);
            }
            applyUpsert(entity, upsert);
            if (upsert.getId() != null) {
                repository.updateById(entity);
            } else {
                repository.insert(entity);
            }
        }
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, "site_navigation.save",
                "site_navigation", "navigation", "导航配置", CacheInvalidationPlans.SITE_NAVIGATION_PLAN,
                null, Map.of("item_count", request.getItems().size()), null);
        return list();
    }

    private void validateCycle(List<NavigationItemUpsert> items) {
        Map<Long, Long> parentMap = new HashMap<>();
        for (NavigationItemUpsert item : items) {
            if (item.getId() != null && item.getParentId() != null) {
                parentMap.put(item.getId(), item.getParentId());
            }
        }
        for (Long id : parentMap.keySet()) {
            Set<Long> visited = new HashSet<>();
            Long current = id;
            while (current != null && parentMap.containsKey(current)) {
                if (!visited.add(current)) {
                    throw SiteBuilderException.of(SiteBuilderErrorCode.NAVIGATION_ITEM_CYCLE_DETECTED,
                            Map.of("chain_start", id));
                }
                current = parentMap.get(current);
                if (current == null) break;
                if (current.equals(id)) {
                    throw SiteBuilderException.of(SiteBuilderErrorCode.NAVIGATION_ITEM_CYCLE_DETECTED,
                            Map.of("chain_start", id));
                }
            }
        }
    }

    /** 按 link_type 校验：custom→url 非空；page→page_key 合法；引用型→ref_id 非空且目标存在 */
    private void validateRefs(List<NavigationItemUpsert> items) {
        for (NavigationItemUpsert item : items) {
            LinkType type = LinkType.of(item.getLinkType());
            if (type == null) {
                type = LinkType.CUSTOM;
            }
            switch (type) {
                case CUSTOM -> {
                    if (item.getUrl() == null || item.getUrl().trim().isEmpty()) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.NAVIGATION_URL_REQUIRED,
                                Map.of("label", String.valueOf(item.getLabel())));
                    }
                }
                case PAGE -> {
                    if (NavPageKey.of(item.getPageKey()) == null) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.NAVIGATION_PAGE_KEY_INVALID,
                                Map.of("page_key", String.valueOf(item.getPageKey())));
                    }
                }
                default -> {
                    if (item.getRefId() == null || !refExists(type, item.getRefId())) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.NAVIGATION_REF_NOT_FOUND,
                                Map.of("link_type", String.valueOf(type.getKey()),
                                        "ref_id", String.valueOf(item.getRefId())));
                    }
                }
            }
        }
    }

    private boolean refExists(LinkType type, Long refId) {
        return switch (type) {
            case CATEGORY -> categoryRepository.findById(refId) != null;
            case COLLECTION -> collectionRepository.findById(refId) != null;
            case PRODUCT -> productRepository.findById(refId) != null;
            case BLOG_POST -> blogPostRepository.findById(refId) != null;
            case REAL_WEDDING -> weddingRepository.findById(refId) != null;
            case LOOKBOOK -> lookbookRepository.findById(refId) != null;
            case GUIDE -> guideRepository.findById(refId) != null;
            default -> false;
        };
    }

    private void applyUpsert(NavigationItem entity, NavigationItemUpsert upsert) {
        LinkType type = LinkType.of(upsert.getLinkType());
        if (type == null) {
            type = LinkType.CUSTOM;
        }
        entity.setParentId(upsert.getParentId());
        entity.setLabel(upsert.getLabel());
        entity.setLabelI18nKey(upsert.getLabelI18nKey());
        entity.setTarget(upsert.getTarget() != null ? upsert.getTarget() : "self");
        entity.setLinkType(type);
        // 按类型清理不适用字段，避免脏数据残留
        entity.setUrl(type == LinkType.CUSTOM ? upsert.getUrl() : null);
        entity.setRefId(type.requiresRef() ? upsert.getRefId() : null);
        entity.setPageKey(type == LinkType.PAGE ? upsert.getPageKey() : null);
        entity.setSortOrder(upsert.getSortOrder() != null ? upsert.getSortOrder() : 0);
        entity.setEnabled(upsert.getEnabled() != null ? upsert.getEnabled() : true);
        try {
            if (upsert.getMegaMenuJson() != null) {
                entity.setMegaMenuJson(objectMapper.writeValueAsString(upsert.getMegaMenuJson()));
            }
            if (upsert.getI18nJson() != null) {
                entity.setI18nJson(objectMapper.writeValueAsString(upsert.getI18nJson()));
            }
        } catch (Exception e) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.I18N_JSON_INVALID);
        }
    }

    private NavigationItemDto toDto(NavigationItem entity) {
        NavigationItemDto dto = new NavigationItemDto();
        dto.setId(entity.getId());
        dto.setParentId(entity.getParentId());
        dto.setLabel(entity.getLabel());
        dto.setLabelI18nKey(entity.getLabelI18nKey());
        dto.setUrl(entity.getUrl());
        dto.setTarget(entity.getTarget());
        dto.setLinkType(entity.getLinkType() != null ? entity.getLinkType().getKey() : LinkType.CUSTOM.getKey());
        dto.setRefId(entity.getRefId());
        dto.setPageKey(entity.getPageKey());
        dto.setMegaMenuJson(entity.getMegaMenuJson());
        dto.setI18nJson(entity.getI18nJson());
        dto.setSortOrder(entity.getSortOrder());
        dto.setEnabled(entity.getEnabled());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
