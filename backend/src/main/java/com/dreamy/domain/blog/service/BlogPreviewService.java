package com.dreamy.domain.blog.service;

import com.dreamy.domain.blog.entity.BlogPost;
import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Blog 预览 token 服务（2026-08-20）。
 * 设计：
 *  - token = UUID v4（128bit 不可枚举）
 *  - Redis key `blog:preview:{token}` → postId（String）
 *  - TTL 4 小时（一篇长文编辑 + 预览 + 调整的典型工作流）
 *  - 不绑 IP/UA（管理员可能切换网络/手机预览）
 *  - 已删除文章 (postId 不存在) → 401 同口径
 */
@Service
public class BlogPreviewService {

    private static final String KEY_PREFIX = "blog:preview:";
    private static final Duration TTL = Duration.ofHours(4);

    private final StringRedisTemplate redis;
    private final BlogPostRepository blogPostRepository;

    public BlogPreviewService(StringRedisTemplate redis, BlogPostRepository blogPostRepository) {
        this.redis = redis;
        this.blogPostRepository = blogPostRepository;
    }

    /** 生成预览 token；过期时间返回给前端用于倒计时显示 */
    public PreviewToken issue(Long postId) {
        BlogPost post = blogPostRepository.findById(postId);
        if (post == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(KEY_PREFIX + token, String.valueOf(postId), TTL);
        return new PreviewToken(token, "/blog/preview/" + token, Instant.now().plus(TTL));
    }

    /** 校验 token 并返回 postId；过期/伪造/已删除统一 401（不区分原因，防探测） */
    public Long resolvePostId(String token) {
        if (token == null || token.isBlank()) {
            throw new MarketingException(MarketingErrorCode.PREVIEW_TOKEN_INVALID);
        }
        String postIdRaw = redis.opsForValue().get(KEY_PREFIX + token);
        if (postIdRaw == null) {
            throw new MarketingException(MarketingErrorCode.PREVIEW_TOKEN_INVALID);
        }
        Long postId;
        try {
            postId = Long.parseLong(postIdRaw);
        } catch (NumberFormatException ex) {
            throw new MarketingException(MarketingErrorCode.PREVIEW_TOKEN_INVALID);
        }
        // 已删除文章 → 401（与 token 无效同口径）
        BlogPost post = blogPostRepository.findById(postId);
        if (post == null) {
            throw new MarketingException(MarketingErrorCode.PREVIEW_TOKEN_INVALID);
        }
        return postId;
    }

    public record PreviewToken(String token, String previewUrl, Instant expiresAt) {
    }
}
