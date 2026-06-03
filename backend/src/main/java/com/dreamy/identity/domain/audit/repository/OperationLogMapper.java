package com.dreamy.identity.domain.audit.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.audit.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.ResultHandler;

import java.time.LocalDateTime;

/**
 * OperationLogMapper —— RM-100~102（仅 insert + 查询，无 update/delete，EDGE-018）。表 operation_log。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {

    /**
     * RM-102 streamForExport（BLOCKER-5）：真流式导出，边查边写，不全量物化进堆。
     * 约束: operation_log 百万行/月，selectList 全量 load 进 List 会 OOM；改用 ResultHandler 逐行回调。
     * MySQL 通过 fetchSize=Integer.MIN_VALUE 开启游标流式拉取（行级），避免一次性拉全表进客户端。
     * 时间窗上限由 AuditService 强制（必传 from/to 且跨度≤92天）。
     */
    @Select("""
            <script>
            SELECT * FROM operation_log
            <where>
                <if test="action != null">AND action = #{action}</if>
                <if test="operatorId != null">AND operator_id = #{operatorId}</if>
                <if test="from != null">AND created_at &gt;= #{from}</if>
                <if test="to != null">AND created_at &lt;= #{to}</if>
            </where>
            ORDER BY created_at DESC
            </script>
            """)
    @Options(fetchSize = Integer.MIN_VALUE, resultSetType = org.apache.ibatis.mapping.ResultSetType.FORWARD_ONLY)
    void streamByFilter(@Param("action") String action,
                        @Param("operatorId") Long operatorId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        ResultHandler<OperationLogEntity> handler);
}
