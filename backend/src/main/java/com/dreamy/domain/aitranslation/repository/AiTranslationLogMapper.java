package com.dreamy.domain.aitranslation.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.aitranslation.entity.AiTranslationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * ai_translation_log 表 Mapper。
 * L2 TRACE: i18n-backend-data-detail.md §2 Repository RM-NNN。
 */
@Mapper
public interface AiTranslationLogMapper extends BaseMapper<AiTranslationLog> {
}
