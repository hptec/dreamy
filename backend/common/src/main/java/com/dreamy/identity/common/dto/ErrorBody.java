package com.dreamy.identity.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统一错误响应体 {code,message,details}。
 * 约束: shared-contracts error_envelope.shape。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorBody {

    /** 数字业务码 */
    private Integer code;

    /** 当前语言文案（store en/es/fr；admin 中文） */
    private String message;

    /** 可选：字段级错误 / remaining_attempts / remaining_resend_seconds */
    private Map<String, Object> details;
}
