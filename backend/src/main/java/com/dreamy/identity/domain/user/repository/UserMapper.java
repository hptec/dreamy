package com.dreamy.identity.domain.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.domain.user.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * UserMapper —— RM-001~007。表 user。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /** RM-002 findByEmailActive：命中 uk_user_email；status≠anonymized */
    @Select("SELECT * FROM `user` WHERE email = #{email} AND status <> 'anonymized' LIMIT 1")
    UserEntity findByEmailActive(@Param("email") String email);

    /** RM-005 findDeletedBefore：idx_user_status_deleted_at（FLOW-16 匿名化） */
    @Select("SELECT * FROM `user` WHERE status = 'deleted' AND deleted_at < #{cutoff} LIMIT #{limit}")
    List<UserEntity> findDeletedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
