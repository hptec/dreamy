package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.site_builder.entity.NavigationItem;
import com.dreamy.domain.site_builder.repository.NavigationItemRepository;
import com.dreamy.dto.SiteBuilderDtos.NavigationItemDto;
import com.dreamy.dto.SiteBuilderDtos.NavigationItemUpsert;
import com.dreamy.dto.SiteBuilderDtos.NavigationSaveRequest;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导航服务（FLOW-SB02）。KD-4 保存即发布（整体替换）。
 */
@Service
public class NavigationService {

    private final NavigationItemRepository repository;
    private final ObjectMapper objectMapper;
    private final SiteBuilderCacheService cacheService;

    public NavigationService(NavigationItemRepository repository,
                             ObjectMapper objectMapper,
                             SiteBuilderCacheService cacheService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    public List<NavigationItemDto> list() {
        return repository.findAllOrderBySort().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<NavigationItemDto> save(NavigationSaveRequest request) {
        if (request.getItems() == null) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "items"));
        }
        validateCycle(request.getItems());
        validateTaxonomyRefs(request.getItems());

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
        cacheService.invalidateNavigationFamily();
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

    private void validateTaxonomyRefs(List<NavigationItemUpsert> items) {
        // 跨域校验 taxonomy_id 存在性（调用 TaxonomyService.findById）
        // 这里简化：只校验 link_type=taxonomy 时 taxonomyId 非空
        for (NavigationItemUpsert item : items) {
            if ("taxonomy".equals(item.getLinkType()) && item.getTaxonomyId() == null) {
                throw SiteBuilderException.of(SiteBuilderErrorCode.TAXONOMY_NOT_FOUND);
            }
        }
    }

    private void applyUpsert(NavigationItem entity, NavigationItemUpsert upsert) {
        entity.setParentId(upsert.getParentId());
        entity.setLabel(upsert.getLabel());
        entity.setLabelI18nKey(upsert.getLabelI18nKey());
        entity.setUrl(upsert.getUrl());
        entity.setTarget(upsert.getTarget() != null ? upsert.getTarget() : "self");
        entity.setLinkType(upsert.getLinkType() != null ? upsert.getLinkType() : "custom");
        entity.setTaxonomyId(upsert.getTaxonomyId());
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
        dto.setLinkType(entity.getLinkType());
        dto.setTaxonomyId(entity.getTaxonomyId());
        dto.setMegaMenuJson(entity.getMegaMenuJson());
        dto.setI18nJson(entity.getI18nJson());
        dto.setSortOrder(entity.getSortOrder());
        dto.setEnabled(entity.getEnabled());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
