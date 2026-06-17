package com.dreamy.domain.glossary.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.glossary.entity.AiTranslationGlossary;
import com.dreamy.domain.glossary.repository.AiTranslationGlossaryMapper;
import com.dreamy.dto.GlossaryDtos.GlossaryTermDto;
import com.dreamy.dto.GlossaryDtos.GlossaryTermUpsert;
import com.dreamy.error.GatewayErrorCode;
import com.dreamy.error.GatewayException;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 翻译术语表服务（CRUD + getById + list 过滤）。
 * L2 TRACE: i18n-backend-api-detail.md §3 / FUNC-022 / EDGE-022。
 * 约束：term_en 唯一（409401），不存在 → 404401。
 */
@Service
public class GlossaryService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AiTranslationGlossaryMapper mapper;

    public GlossaryService(AiTranslationGlossaryMapper mapper) {
        this.mapper = mapper;
    }

    /** §3.2 分页列表（category/enabled 过滤，按 updated_at 降序）。 */
    public Paginated<GlossaryTermDto> list(String category, Boolean enabled, Integer page, Integer pageSize) {
        int p = page != null && page > 0 ? page : 1;
        int ps = pageSize != null && pageSize > 0 && pageSize <= 100 ? pageSize : 20;
        LambdaQueryWrapper<AiTranslationGlossary> qw = new LambdaQueryWrapper<>();
        qw.isNull(AiTranslationGlossary::getDeletedAt);
        if (category != null && !category.isBlank()) {
            qw.eq(AiTranslationGlossary::getCategory, category);
        }
        if (enabled != null) {
            qw.eq(AiTranslationGlossary::getEnabled, enabled);
        }
        qw.orderByDesc(AiTranslationGlossary::getUpdatedAt);
        Page<AiTranslationGlossary> result = mapper.selectPage(new Page<>(p, ps), qw);
        List<GlossaryTermDto> items = result.getRecords().stream().map(this::toDto).toList();
        Paginated<GlossaryTermDto> paginated = new Paginated<>();
        paginated.setData(items);
        paginated.setTotalElements(result.getTotal());
        paginated.setPageNumber(p);
        paginated.setPageSize(ps);
        paginated.setNumberOfElements(items.size());
        paginated.setTotalPages(ps > 0 ? (int) Math.ceil((double) result.getTotal() / ps) : 0);
        return paginated;
    }

    /** §3.3 详情，不存在 → 404401。 */
    public GlossaryTermDto getById(Long id) {
        return toDto(loadOrThrow(id));
    }

    /** §3.1 新增。term_en 大小写不敏感唯一（409401）。 */
    @Transactional
    public GlossaryTermDto create(GlossaryTermUpsert req) {
        ensureTermEnUnique(req.termEn(), null);
        AiTranslationGlossary entity = new AiTranslationGlossary();
        entity.setTermEn(req.termEn());
        entity.setTermEs(req.termEs());
        entity.setTermFr(req.termFr());
        entity.setCategory(req.category());
        entity.setEnabled(req.enabled());
        mapper.insert(entity);
        return toDto(mapper.selectById(entity.getId()));
    }

    /** §3.4 更新。乐观锁 + term_en 唯一性（排除自身）。 */
    @Transactional
    public GlossaryTermDto update(Long id, GlossaryTermUpsert req) {
        AiTranslationGlossary entity = loadOrThrow(id);
        if (req.updatedAt() != null && !req.updatedAt().isBlank()) {
            String dbTs = entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().format(ISO);
            if (dbTs != null && !normalizeTs(req.updatedAt()).equals(normalizeTs(dbTs))) {
                throw new GatewayException(GatewayErrorCode.TERM_EDIT_CONFLICT);
            }
        }
        ensureTermEnUnique(req.termEn(), id);
        entity.setTermEn(req.termEn());
        entity.setTermEs(req.termEs());
        entity.setTermFr(req.termFr());
        entity.setCategory(req.category());
        entity.setEnabled(req.enabled());
        mapper.updateById(entity);
        return toDto(mapper.selectById(id));
    }

    /** §3.5 物理删除（无引用校验）。 */
    @Transactional
    public void delete(Long id) {
        loadOrThrow(id);
        // 逻辑删除：设置 deleted_at = now()
        AiTranslationGlossary patch = new AiTranslationGlossary();
        patch.setId(id);
        patch.setDeletedAt(LocalDateTime.now());
        mapper.updateById(patch);
    }

    private AiTranslationGlossary loadOrThrow(Long id) {
        AiTranslationGlossary entity = id == null ? null : mapper.selectById(id);
        if (entity == null || entity.getDeletedAt() != null) {
            throw new GatewayException(GatewayErrorCode.TERM_NOT_FOUND);
        }
        return entity;
    }

    /** term_en 大小写不敏感唯一（LOWER 比对，§3.1）。 */
    private void ensureTermEnUnique(String termEn, Long excludeId) {
        LambdaQueryWrapper<AiTranslationGlossary> qw = new LambdaQueryWrapper<>();
        qw.isNull(AiTranslationGlossary::getDeletedAt);
        qw.apply("LOWER(term_en) = LOWER({0})", termEn);
        if (excludeId != null) {
            qw.ne(AiTranslationGlossary::getId, excludeId);
        }
        if (mapper.selectCount(qw) > 0) {
            throw new GatewayException(GatewayErrorCode.TERM_EN_EXISTS);
        }
    }

    private GlossaryTermDto toDto(AiTranslationGlossary e) {
        return new GlossaryTermDto(
                e.getId(),
                e.getTermEn(),
                e.getTermEs(),
                e.getTermFr(),
                e.getCategory(),
                e.getEnabled(),
                e.getCreatedAt() == null ? null : e.getCreatedAt().format(ISO),
                e.getUpdatedAt() == null ? null : e.getUpdatedAt().format(ISO));
    }

    private String normalizeTs(String ts) {
        return ts == null ? "" : ts.replace("T", " ").trim();
    }
}
