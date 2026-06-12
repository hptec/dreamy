package com.dreamy.domain.audit.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.audit.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * OperationLogMapper —— RM-100~102（仅 insert + 查询，无 update/delete，EDGE-018）。表 operation_log。
 *
 * DEC-002（ORM 合规重构）：原 RM-102 streamByFilter（{@code @Select(<script>)} + fetchSize=Integer.MIN_VALUE
 * + ResultHandler 游标流式）已删除，消除 native SQL。导出改由 AuditService 用 LambdaQueryWrapper
 * 分页轮询（每页 1000 条、id 游标递减）逐页回调，时间窗上限（≤92 天）保证轮询有界。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
