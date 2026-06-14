package com.dreamy.domain.product.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.product.entity.CareInstructionDef;
import com.dreamy.enums.CareCategory;
import com.dreamy.enums.CareStatus;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 护理标签字典仓储（RM-FC-010~019）。
 * L2 TRACE: catalog-fabric-care-data-detail §2 CareInstructionDefRepository。
 */
@Repository
public class CareInstructionDefRepository {

    private final CareInstructionDefMapper mapper;

    public CareInstructionDefRepository(CareInstructionDefMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-FC-010 listAll —— ORDER BY category, sort_order */
    public List<CareInstructionDef> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<CareInstructionDef>()
                .orderByAsc(CareInstructionDef::getCategory)
                .orderByAsc(CareInstructionDef::getSortOrder));
    }

    /** RM-FC-011 listActive —— WHERE status=1 ORDER BY category, sort_order */
    public List<CareInstructionDef> listActive() {
        return mapper.selectList(new LambdaQueryWrapper<CareInstructionDef>()
                .eq(CareInstructionDef::getStatus, CareStatus.ACTIVE)
                .orderByAsc(CareInstructionDef::getCategory)
                .orderByAsc(CareInstructionDef::getSortOrder));
    }

    /** RM-FC-012 findById */
    public CareInstructionDef findById(Long id) {
        return mapper.selectById(id);
    }

    /** RM-FC-013 findByCode —— uk_care_instruction_def_code 点查 */
    public CareInstructionDef findByCode(String code) {
        return mapper.selectOne(new LambdaQueryWrapper<CareInstructionDef>()
                .eq(CareInstructionDef::getCode, code));
    }

    /** RM-FC-014 existsByCodeExcept —— 409 冲突检测（code 唯一性） */
    public boolean existsByCodeExcept(String code, Long exceptId) {
        LambdaQueryWrapper<CareInstructionDef> wrapper = new LambdaQueryWrapper<CareInstructionDef>()
                .eq(CareInstructionDef::getCode, code);
        if (exceptId != null) {
            wrapper.ne(CareInstructionDef::getId, exceptId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    /** RM-FC-015 listByCategory —— 按类别筛选 */
    public List<CareInstructionDef> listByCategory(CareCategory category) {
        return mapper.selectList(new LambdaQueryWrapper<CareInstructionDef>()
                .eq(CareInstructionDef::getCategory, category)
                .orderByAsc(CareInstructionDef::getSortOrder));
    }

    /** RM-FC-016 insert */
    public void insert(CareInstructionDef def) {
        mapper.insert(def);
    }

    /** RM-FC-017 update */
    public void update(CareInstructionDef def) {
        mapper.updateById(def);
    }

    /** RM-FC-018 deleteById */
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    /** RM-FC-019 updateStatus —— 启用/禁用切换 */
    public void updateStatus(Long id, CareStatus status) {
        CareInstructionDef def = new CareInstructionDef();
        def.setId(id);
        def.setStatus(status);
        mapper.updateById(def);
    }
}
