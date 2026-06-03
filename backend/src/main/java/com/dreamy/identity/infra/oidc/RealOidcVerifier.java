package com.dreamy.identity.infra.oidc;

import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.error.InfraException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OIDC 验证 real 实现（identity.oidc.mode=real）。
 * 约束: RT-002（超时 5s 重试 1 次）；CB-001（连续失败开启熔断窗口快速失败 50201）；
 * 超时→OIDC_TIMEOUT(50401)，不可达→OIDC_UNAVAILABLE(50201)（PATH-02 不泄漏堆栈）。
 *
 * BLOCKER-3（安全）：通过 JWKS 公钥**验证 id_token 签名**（RS256），并校验：
 *   - iss：google=https://accounts.google.com / apple=https://appleid.apple.com
 *   - aud：等于配置的 client_id（identity.oidc.{provider}.client-id）
 *   - exp：未过期（nimbus DefaultJWTProcessor 内置时间校验）
 *   - nonce：与请求传入 nonce 一致（防重放）
 * 验签/校验失败 → InfraException(OIDC_UNAVAILABLE 50201)（不泄漏 token 细节）。
 * nimbus RemoteJWKSet 内部按 kid 选公钥并缓存 JWKS，自动处理密钥轮换。
 */
@Component
@ConditionalOnProperty(name = "identity.oidc.mode", havingValue = "real")
public class RealOidcVerifier implements OidcVerifier {

    private static final Logger log = LoggerFactory.getLogger(RealOidcVerifier.class);
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String APPLE_JWKS = "https://appleid.apple.com/auth/keys";
    private static final String GOOGLE_ISS = "https://accounts.google.com";
    private static final String APPLE_ISS = "https://appleid.apple.com";
    private static final int CB_THRESHOLD = 5;
    private static final long CB_WINDOW_MS = 30_000L;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    /** 期望的 audience（client_id）；real 模式必须配置，否则 aud 校验拒绝全部 token */
    @Value("${identity.oidc.google.client-id:}")
    private String googleClientId;
    @Value("${identity.oidc.apple.client-id:}")
    private String appleClientId;

    /** 每 provider 复用一个 JWT 处理器（内含 RemoteJWKSet 缓存，避免每次请求重建 JWKS 连接） */
    private final ConcurrentHashMap<String, ConfigurableJWTProcessor<SecurityContext>> processors =
            new ConcurrentHashMap<>();

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long circuitOpenUntil = 0L;

    @Override
    public OidcResult verify(String provider, String idToken, String nonce) {
        // CB-001：熔断窗口内快速失败
        if (System.currentTimeMillis() < circuitOpenUntil) {
            log.warn("[OIDC] circuit open, fast-fail provider={}", provider);
            throw new InfraException(ErrorCode.OIDC_UNAVAILABLE);
        }
        Exception last = null;
        boolean timeout = false;
        for (int attempt = 0; attempt <= 1; attempt++) { // RT-002 重试 1 次
            try {
                OidcResult result = verifyAndDecode(provider, idToken, nonce);
                consecutiveFailures.set(0);
                return result;
            } catch (java.net.SocketTimeoutException te) {
                last = te;
                timeout = true;
                log.warn("[OIDC] JWKS timeout attempt={} provider={}", attempt, provider);
            } catch (com.nimbusds.jose.RemoteKeySourceException rke) {
                // JWKS 端点不可达 / 拉取失败 → 视为不可用，重试
                last = rke;
                log.warn("[OIDC] JWKS unavailable attempt={} provider={}", attempt, provider);
            } catch (java.text.ParseException | com.nimbusds.jose.proc.BadJOSEException
                     | com.nimbusds.jose.JOSEException ve) {
                // 验签失败 / claim 校验失败 / token 结构非法 → 安全拒绝，不重试（确定性失败）
                log.warn("[OIDC] id_token verification FAILED provider={} reason={}",
                        provider, ve.getClass().getSimpleName());
                throw new InfraException(ErrorCode.OIDC_UNAVAILABLE, ve);
            } catch (Exception ex) {
                last = ex;
                log.warn("[OIDC] unavailable attempt={} provider={}", attempt, provider);
            }
        }
        recordFailure();
        if (timeout) {
            throw new InfraException(ErrorCode.OIDC_TIMEOUT, last);
        }
        throw new InfraException(ErrorCode.OIDC_UNAVAILABLE, last);
    }

    /** 验签 + claim 校验 + 解码业务字段 */
    private OidcResult verifyAndDecode(String provider, String idToken, String nonce) throws Exception {
        ConfigurableJWTProcessor<SecurityContext> processor = processorFor(provider);
        // 1) 验签（RS256）+ exp（内置时间校验）→ 返回 claims
        JWTClaimsSet claims = processor.process(idToken, null);

        // 2) iss 校验
        String expectedIss = "apple".equals(provider) ? APPLE_ISS : GOOGLE_ISS;
        if (!expectedIss.equals(claims.getIssuer())) {
            throw new com.nimbusds.jose.proc.BadJOSEException("iss mismatch");
        }
        // 3) aud 校验（等于配置的 client_id）
        String expectedAud = "apple".equals(provider) ? appleClientId : googleClientId;
        if (expectedAud == null || expectedAud.isBlank()
                || claims.getAudience() == null || !claims.getAudience().contains(expectedAud)) {
            throw new com.nimbusds.jose.proc.BadJOSEException("aud mismatch");
        }
        // 4) exp 兜底校验（防 processor 未配置时间校验器的极端情况）
        Date exp = claims.getExpirationTime();
        if (exp == null || exp.before(new Date())) {
            throw new com.nimbusds.jose.proc.BadJOSEException("token expired");
        }
        // 5) nonce 校验（防重放）：请求携带 nonce 时必须与 token claim 一致
        if (nonce != null && !nonce.isBlank()) {
            Object tokenNonce = claims.getClaim("nonce");
            if (tokenNonce == null || !nonce.equals(tokenNonce.toString())) {
                throw new com.nimbusds.jose.proc.BadJOSEException("nonce mismatch");
            }
        }

        // 6) 解码业务字段
        String sub = claims.getSubject();
        String email = stringClaim(claims, "email");
        boolean emailVerified = booleanClaim(claims, "email_verified");
        boolean hidden = booleanClaim(claims, "is_private_email");
        String relay = hidden ? email : null;
        return new OidcResult(sub, email, emailVerified, hidden, relay);
    }

    private ConfigurableJWTProcessor<SecurityContext> processorFor(String provider) {
        return processors.computeIfAbsent(provider, p -> {
            try {
                String jwksUrl = "apple".equals(p) ? APPLE_JWKS : GOOGLE_JWKS;
                DefaultResourceRetriever retriever =
                        new DefaultResourceRetriever(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
                JWKSource<SecurityContext> jwkSource =
                        new RemoteJWKSet<>(new URL(jwksUrl), retriever);
                ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
                JWSKeySelector<SecurityContext> keySelector =
                        new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
                jwtProcessor.setJWSKeySelector(keySelector);
                return jwtProcessor;
            } catch (MalformedURLException ex) {
                throw new IllegalStateException("invalid JWKS url for provider=" + p, ex);
            }
        });
    }

    private String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean booleanClaim(JWTClaimsSet claims, String name) {
        try {
            Boolean v = claims.getBooleanClaim(name);
            return Boolean.TRUE.equals(v);
        } catch (Exception ex) {
            Object raw = claims.getClaim(name);
            return raw != null && Boolean.parseBoolean(raw.toString());
        }
    }

    private void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= CB_THRESHOLD) {
            circuitOpenUntil = System.currentTimeMillis() + CB_WINDOW_MS;
            consecutiveFailures.set(0);
            log.error("[OIDC] circuit breaker OPEN for {}ms (CB-001)", CB_WINDOW_MS);
        }
    }
}
