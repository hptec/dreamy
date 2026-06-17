package com.dreamy.domain.glossary.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.glossary.entity.AiTranslationGlossary;
import org.apache.ibatis.annotations.Mapper;

/**
 * ai_translation_glossary 表 Mapper。
 * L2 TRACE: i18n-backend-data-detail.md §2 Repository RM-NNN。
 */
@Mapper
public interface AiTranslationGlossaryMapper extends BaseMapper<AiTranslationGlossary> {
}
