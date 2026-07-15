package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.site_builder.entity.FooterColumn;
import com.dreamy.domain.site_builder.entity.FooterLink;
import com.dreamy.domain.site_builder.repository.FooterRepository;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.dto.SiteBuilderDtos.FooterColumnDto;
import com.dreamy.dto.SiteBuilderDtos.FooterColumnUpsert;
import com.dreamy.dto.SiteBuilderDtos.FooterLinkDto;
import com.dreamy.dto.SiteBuilderDtos.FooterLinkUpsert;
import com.dreamy.dto.SiteBuilderDtos.FooterSaveRequest;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FooterService {

    private final FooterRepository repository;
    private final ObjectMapper objectMapper;
    private final CacheInvalidationTaskService cacheTasks;
    public FooterService(FooterRepository repository, ObjectMapper objectMapper,
                         CacheInvalidationTaskService cacheTasks) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cacheTasks = cacheTasks;
    }

    public List<FooterColumnDto> list() {
        List<FooterColumn> columns = repository.findAllColumnsOrderBySort();
        List<FooterLink> links = repository.findAllLinksOrderBySort();
        Map<Long, List<FooterLink>> linksByColumn = links.stream()
                .collect(Collectors.groupingBy(FooterLink::getColumnId));
        return columns.stream().map(c -> toDto(c, linksByColumn.getOrDefault(c.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<FooterColumnDto> save(FooterSaveRequest request) {
        validate(request);
        repository.deleteAllLinks();
        repository.deleteAllColumns();
        for (FooterColumnUpsert columnUpsert : request.getColumns()) {
            FooterColumn column = new FooterColumn();
            column.setTitle(columnUpsert.getTitle());
            column.setSortOrder(columnUpsert.getSortOrder() != null ? columnUpsert.getSortOrder() : 0);
            column.setEnabled(columnUpsert.getEnabled() != null ? columnUpsert.getEnabled() : true);
            column.setVersion(0);
            try {
                if (columnUpsert.getI18nJson() != null) {
                    column.setI18nJson(objectMapper.writeValueAsString(columnUpsert.getI18nJson()));
                }
            } catch (Exception e) {
                throw SiteBuilderException.of(SiteBuilderErrorCode.I18N_JSON_INVALID);
            }
            repository.insertColumn(column);
            if (columnUpsert.getLinks() != null) {
                for (FooterLinkUpsert linkUpsert : columnUpsert.getLinks()) {
                    FooterLink link = new FooterLink();
                    link.setColumnId(column.getId());
                    link.setLabel(linkUpsert.getLabel());
                    link.setUrl(linkUpsert.getUrl());
                    link.setTarget(linkUpsert.getTarget() != null ? linkUpsert.getTarget() : "self");
                    link.setSortOrder(linkUpsert.getSortOrder() != null ? linkUpsert.getSortOrder() : 0);
                    link.setVersion(0);
                    try {
                        if (linkUpsert.getI18nJson() != null) {
                            link.setI18nJson(objectMapper.writeValueAsString(linkUpsert.getI18nJson()));
                        }
                    } catch (Exception e) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.I18N_JSON_INVALID);
                    }
                    repository.insertLink(link);
                }
            }
        }
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, "site_footer.save",
                "site_footer", "footer", "页脚配置", CacheInvalidationPlans.SITE_FOOTER_PLAN,
                null, Map.of("column_count", request.getColumns().size()), null);
        return list();
    }

    private void validate(FooterSaveRequest request) {
        if (request.getColumns() == null) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "columns"));
        }
        Set<Long> columnIds = new HashSet<>();
        for (FooterColumnUpsert c : request.getColumns()) {
            if (c.getId() != null) columnIds.add(c.getId());
        }
        for (FooterColumnUpsert c : request.getColumns()) {
            if (c.getLinks() != null) {
                for (FooterLinkUpsert l : c.getLinks()) {
                    if (l.getColumnId() != null && !columnIds.contains(l.getColumnId()) &&
                            !l.getColumnId().equals(c.getId())) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.FOOTER_COLUMN_REF_INVALID);
                    }
                    if (l.getUrl() != null && !l.getUrl().matches("^https?://.*")) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.FOOTER_LINK_URL_INVALID);
                    }
                }
            }
        }
    }

    private FooterColumnDto toDto(FooterColumn column, List<FooterLink> links) {
        FooterColumnDto dto = new FooterColumnDto();
        dto.setId(column.getId());
        dto.setTitle(column.getTitle());
        dto.setI18nJson(column.getI18nJson());
        dto.setSortOrder(column.getSortOrder());
        dto.setEnabled(column.getEnabled());
        List<FooterLinkDto> linkDtos = links.stream().map(l -> {
            FooterLinkDto ld = new FooterLinkDto();
            ld.setId(l.getId());
            ld.setColumnId(l.getColumnId());
            ld.setLabel(l.getLabel());
            ld.setUrl(l.getUrl());
            ld.setTarget(l.getTarget());
            ld.setI18nJson(l.getI18nJson());
            ld.setSortOrder(l.getSortOrder());
            return ld;
        }).collect(Collectors.toList());
        dto.setLinks(linkDtos);
        return dto;
    }
}
