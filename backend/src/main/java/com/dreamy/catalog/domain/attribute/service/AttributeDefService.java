package com.dreamy.catalog.domain.attribute.service;

import com.dreamy.catalog.domain.attribute.entity.AttributeDef;
import com.dreamy.catalog.domain.attribute.entity.AttributeDefTranslation;
import com.dreamy.catalog.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.catalog.domain.attribute.repository.AttributeSetRepository;
import com.dreamy.catalog.domain.enums.AttributeType;
import com.dreamy.catalog.dto.AdminCatalogDtos.AttributeDefDto;
import com.dreamy.catalog.dto.AdminCatalogDtos.AttributeDefUpsert;
import com.dreamy.catalog.dto.TranslationDtos.AttributeDefTranslationDto;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.catalog.infra.CatalogAuditRecorder;
import com.dreamy.catalog.support.FieldErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 后台属性字典服务（E-CAT-23~26；TX-CAT-012~014）。
 * L2 TRACE: V-CAT-053~058 / CV-CAT-007 / MAP-CAT-009。
 */
@Service
public class AttributeDefService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Set<String> TRANSLATION_LOCALES = Set.of("es", "fr");

    private final AttributeDefRepository defRepository;
    private final AttributeSetRepository setRepository;
    private final CatalogAuditRecorder audit;

    public AttributeDefService(AttributeDefRepository defRepository, AttributeSetRepository setRepository,
                               CatalogAuditRecorder audit) {
        this.defRepository = defRepository;
        this.setRepository = setRepository;
        this.audit = audit;
    }

    /** E-CAT-23：属性字典列表（含三语 translations） */
    public List<AttributeDefDto> list() {
        List<AttributeDef> defs = defRepository.listAll();
        Map<Long, List<AttributeDefTranslationDto>> translations = translationsByDef(
                defs.stream().map(AttributeDef::getId).toList());
        return defs.stream().map(def -> toDto(def, translations.getOrDefault(def.getId(), List.of()))).toList();
    }

    /** E-CAT-24：新增属性定义（TX-CAT-012） */
    @Transactional
    public AttributeDefDto create(AttributeDefUpsert req) {
        validate(req, null);
        AttributeDef def = new AttributeDef();
        def.setKey(req.key().trim());
        def.setLabel(req.label().trim());
        def.setType(AttributeType.of(req.type()));
        def.setOptions(def.getType().optionsAllowed() ? req.options() : null);
        defRepository.insert(def);
        defRepository.replaceTranslations(def.getId(), toTranslationRows(req.translations()));
        audit.record("创建属性定义", def.getLabel(), null);
        return toDto(def, req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-25：编辑属性定义（TX-CAT-013；key/type 不可变更——V-CAT-057） */
    @Transactional
    public AttributeDefDto update(Long id, AttributeDefUpsert req) {
        AttributeDef existing = defRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.ATTRIBUTE_DEF_NOT_FOUND);
        }
        // V-CAT-057 key 不可变更
        if (req.key() != null && !req.key().equals(existing.getKey())) {
            throw CatalogException.fieldValidation("key", "immutable");
        }
        // V-CAT-057 type 不可变更（商品属性值已按 type 落库）
        if (req.type() != null && existing.getType() != null && !req.type().equals(existing.getType().getKey())) {
            throw CatalogException.fieldValidation("type", "immutable");
        }
        validate(new AttributeDefUpsert(existing.getKey(), req.label(), existing.getType().getKey(),
                req.options(), req.translations()), existing);
        existing.setLabel(req.label().trim());
        existing.setOptions(existing.getType().optionsAllowed() ? req.options() : null);
        defRepository.update(existing);
        defRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        audit.record("编辑属性定义", existing.getLabel(), null);
        return toDto(existing, req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-26：删除属性定义（TX-CAT-014；guard 409507 事务内复查） */
    @Transactional
    public void delete(Long id) {
        AttributeDef existing = defRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.ATTRIBUTE_DEF_NOT_FOUND);
        }
        long usage = setRepository.countItemsByAttributeId(id);
        if (usage > 0) {
            throw new CatalogException(CatalogErrorCode.ATTRIBUTE_DEF_IN_USE,
                    Map.of("attribute_set_count", usage));
        }
        defRepository.deleteById(id);
        defRepository.deleteTranslationsByDefId(id);
        audit.record("删除属性定义", existing.getLabel(), null);
    }

    /** V-CAT-053~056/058 校验（existing 非空=编辑场景，key 唯一性豁免自身） */
    private void validate(AttributeDefUpsert req, AttributeDef existing) {
        FieldErrors errors = new FieldErrors();
        // V-CAT-053 key 必填 pattern ≤64 全局唯一（重复 → 422501 fields.key=exists，契约无 409）
        if (existing == null) {
            if (req.key() == null || req.key().trim().isEmpty()) {
                errors.reject("key", "required");
            } else if (req.key().trim().length() > 64 || !KEY_PATTERN.matcher(req.key().trim()).matches()) {
                errors.reject("key", "pattern");
            } else if (defRepository.existsByKey(req.key().trim())) {
                errors.reject("key", "exists");
            }
        }
        // V-CAT-054 label 必填 trim 非空 ≤64
        if (req.label() == null || req.label().trim().isEmpty()) {
            errors.reject("label", "required");
        } else if (req.label().trim().length() > 64) {
            errors.reject("label", "too_long");
        }
        // V-CAT-055 type 必填枚举
        AttributeType type = AttributeType.of(req.type());
        if (type == null) {
            errors.reject("type", "invalid_enum");
            errors.throwIfAny();
            return;
        }
        // V-CAT-056 options js_guard：select/multiselect 必填非空去重；text/toggle 禁止提交
        if (type.optionsAllowed()) {
            if (req.options() == null || req.options().isEmpty()) {
                errors.reject("options", "required");
            } else if (new LinkedHashSet<>(req.options()).size() != req.options().size()) {
                errors.reject("options", "duplicated");
            }
        } else if (req.options() != null && !req.options().isEmpty()) {
            errors.reject("options", "not_allowed");
        }
        // V-CAT-058 translations.options 与主表 options 等长（CV-CAT-007）
        if (req.translations() != null) {
            Set<String> seen = new HashSet<>();
            for (AttributeDefTranslationDto t : req.translations()) {
                if (t.locale() == null || !TRANSLATION_LOCALES.contains(t.locale()) || !seen.add(t.locale())) {
                    errors.reject("translations", "invalid_locale");
                    break;
                }
                if (t.options() != null) {
                    int mainSize = req.options() == null ? 0 : req.options().size();
                    if (t.options().size() != mainSize) {
                        errors.reject("translations", "options_length_mismatch");
                        break;
                    }
                }
            }
        }
        errors.throwIfAny();
    }

    private List<AttributeDefTranslation> toTranslationRows(List<AttributeDefTranslationDto> dtos) {
        List<AttributeDefTranslation> rows = new ArrayList<>();
        if (dtos != null) {
            for (AttributeDefTranslationDto dto : dtos) {
                AttributeDefTranslation row = new AttributeDefTranslation();
                row.setLocale(dto.locale());
                row.setLabel(dto.label());
                row.setOptions(dto.options());
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<Long, List<AttributeDefTranslationDto>> translationsByDef(List<Long> defIds) {
        Map<Long, List<AttributeDefTranslationDto>> result = new HashMap<>();
        for (AttributeDefTranslation t : defRepository.listTranslationsByDefIds(defIds)) {
            result.computeIfAbsent(t.getAttributeDefId(), k -> new ArrayList<>())
                    .add(new AttributeDefTranslationDto(t.getLocale(), t.getLabel(), t.getOptions()));
        }
        return result;
    }

    private AttributeDefDto toDto(AttributeDef def, List<AttributeDefTranslationDto> translations) {
        return new AttributeDefDto(def.getId(), def.getKey(), def.getLabel(),
                def.getType() == null ? null : def.getType().getKey(), def.getOptions(), translations);
    }
}
