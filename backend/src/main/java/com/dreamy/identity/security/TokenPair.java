package com.dreamy.identity.security;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 签发的 TokenPair（store 端 access 2h + refresh 30d）。
 * 约束: shared-contracts jwt_isolation.store；MAP-007 时间 ISO8601 UTC。
 */
@Data
public class TokenPair {

    private String accessToken;
    private String refreshToken;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime refreshExpiresAt;
    /** access JWT jti（token_id） */
    private String tokenId;
    /** refresh JWT jti（refresh_token_id） */
    private String refreshTokenId;
}
