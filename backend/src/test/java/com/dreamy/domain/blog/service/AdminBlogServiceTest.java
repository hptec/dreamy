package com.dreamy.domain.blog.service;

import com.dreamy.domain.blog.entity.BlogPost;
import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.dto.AdminMarketingDtos.BlogPostUpsert;
import com.dreamy.enums.ContentStatus;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingAuditRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBlogServiceTest {

    @Mock
    BlogPostRepository blogPostRepository;
    @Mock
    MarketingAuditRecorder audit;
    @Mock
    com.dreamy.domain.cache.service.CacheInvalidationTaskService cacheTasks;
    @Mock
    BlogPreviewService previewService;
    @Mock
    BlogViewsCounter viewsCounter;
    @InjectMocks
    AdminBlogService service;

    @Test
    @DisplayName("TX-MKT-013: 文章删除先清译文再物理删除主表，并保留已发布失效链")
    void deleteCleansTranslationsBeforePhysicalDelete() {
        BlogPost post = new BlogPost();
        post.setId(2L);
        post.setTitle("Wedding Guide");
        post.setSlug("wedding-guide");
        post.setStatus(ContentStatus.PUBLISHED);
        when(blogPostRepository.findById(2L)).thenReturn(post);

        service.delete(2L);

        InOrder order = inOrder(blogPostRepository);
        order.verify(blogPostRepository).deleteTranslationsByPostId(2L);
        order.verify(blogPostRepository).deleteById(2L);
        verify(blogPostRepository, never()).update(any());
        verify(audit).record("删除文章", "Wedding Guide", null);
        verify(cacheTasks).enqueue(anyString(), eq("blog.delete"), eq("blog"), eq(2L),
                eq("Wedding Guide"), anyList(), nullable(java.time.LocalDateTime.class), anyMap(),
                nullable(String.class));
    }

    // ===== 2026-08-20 新增：乐观锁 + 状态前置条件 + slug 校验 + content 长度 + 字数统计 =====

    private BlogPostUpsert buildUpsert(String title, String slug, ContentStatus status, Long version) {
        return new BlogPostUpsert(title, null, null, null, "content",
                slug, status.getKey(), null, null, null, version, null, null);
    }

    @Test
    @DisplayName("2026-08-20: update version 不匹配抛 409 VERSION_CONFLICT")
    void updateVersionMismatchThrowsVersionConflict() {
        BlogPost existing = new BlogPost();
        existing.setId(1L);
        existing.setTitle("Old");
        existing.setSlug("old");
        existing.setStatus(ContentStatus.DRAFT);
        existing.setVersion(3L);
        when(blogPostRepository.findById(1L)).thenReturn(existing);

        BlogPostUpsert req = buildUpsert("New", "old", ContentStatus.DRAFT, 2L); // version=2, DB=3
        MarketingException ex = assertThrows(MarketingException.class, () -> service.update(1L, req));
        assertEquals(MarketingErrorCode.VERSION_CONFLICT.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("2026-08-20: update version 匹配但 DB 0 行受影响（并发被 +1）也抛 VERSION_CONFLICT")
    void updateConcurrentDbZeroRowsThrowsVersionConflict() {
        BlogPost existing = new BlogPost();
        existing.setId(1L);
        existing.setTitle("Old");
        existing.setSlug("old");
        existing.setStatus(ContentStatus.DRAFT);
        existing.setVersion(3L);
        when(blogPostRepository.findById(1L)).thenReturn(existing);
        when(blogPostRepository.update(any())).thenReturn(0); // 并发下 version 被 +1

        BlogPostUpsert req = buildUpsert("New", "old", ContentStatus.DRAFT, 3L);
        MarketingException ex = assertThrows(MarketingException.class, () -> service.update(1L, req));
        assertEquals(MarketingErrorCode.VERSION_CONFLICT.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("2026-08-20: patchStatus 并发下状态前置条件不满足,后者 409 STATE_CHANGED")
    void patchStatusConcurrentSecondFails() {
        BlogPost existing = new BlogPost();
        existing.setId(1L);
        existing.setTitle("Post");
        existing.setSlug("post");
        existing.setStatus(ContentStatus.DRAFT);
        when(blogPostRepository.findById(1L)).thenReturn(existing);
        // 并发:另一人抢先发布,此时 expectedFrom=DRAFT 但实际已 PUBLISHED,UPDATE 0 行
        when(blogPostRepository.updateStatusWithPrecondition(eq(1L), eq(ContentStatus.DRAFT),
                eq(ContentStatus.PUBLISHED), any())).thenReturn(0);

        MarketingException ex = assertThrows(MarketingException.class,
                () -> service.patchStatus(1L, ContentStatus.PUBLISHED.getKey()));
        assertEquals(MarketingErrorCode.STATE_CHANGED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("2026-08-20: slug 大写自动归一化为小写")
    void upsertSlugUppercaseNormalizedToLowercase() {
        BlogPost post = new BlogPost();
        post.setId(1L);
        post.setStatus(ContentStatus.DRAFT);
        post.setVersion(0L);
        when(blogPostRepository.findById(1L)).thenReturn(post);
        when(blogPostRepository.existsBySlugExcept("my-post", 1L)).thenReturn(false);
        when(blogPostRepository.update(any())).thenReturn(1);

        BlogPostUpsert req = buildUpsert("Title", "My-Post", ContentStatus.DRAFT, 0L);
        service.update(1L, req);

        org.mockito.ArgumentCaptor<BlogPost> captor = org.mockito.ArgumentCaptor.forClass(BlogPost.class);
        verify(blogPostRepository).update(captor.capture());
        assertEquals("my-post", captor.getValue().getSlug());
    }

    @Test
    @DisplayName("2026-08-20: slug 命中保留字抛 422 RESERVED_SLUG")
    void upsertSlugReservedThrows422() {
        BlogPost post = new BlogPost();
        post.setId(1L);
        post.setStatus(ContentStatus.DRAFT);
        post.setVersion(0L);
        when(blogPostRepository.findById(1L)).thenReturn(post);

        BlogPostUpsert req = buildUpsert("Title", "admin", ContentStatus.DRAFT, 0L);
        MarketingException ex = assertThrows(MarketingException.class, () -> service.update(1L, req));
        assertEquals(MarketingErrorCode.RESERVED_SLUG.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("2026-08-20: content 超 200_000 字符抛 422 CONTENT_TOO_LARGE")
    void upsertContentTooLargeThrows422() {
        BlogPost post = new BlogPost();
        post.setId(1L);
        post.setStatus(ContentStatus.DRAFT);
        post.setVersion(0L);
        when(blogPostRepository.findById(1L)).thenReturn(post);

        String hugeContent = "x".repeat(200_001);
        BlogPostUpsert req = new BlogPostUpsert("Title", null, null, null, hugeContent,
                "my-post", ContentStatus.DRAFT.getKey(), null, null, null, 0L, null, null);
        MarketingException ex = assertThrows(MarketingException.class, () -> service.update(1L, req));
        assertEquals(MarketingErrorCode.CONTENT_TOO_LARGE.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("2026-08-20: 中英混排字数统计（CJK 按字 + Latin 按词）")
    void wordCountMixedCjkLatinCorrectSegmentation() {
        BlogPost post = new BlogPost();
        post.setId(1L);
        post.setStatus(ContentStatus.DRAFT);
        post.setVersion(0L);
        when(blogPostRepository.findById(1L)).thenReturn(post);
        when(blogPostRepository.update(any())).thenReturn(1);

        // 中文 4 字 + 英文 3 词 = 7
        BlogPostUpsert req = new BlogPostUpsert("Title", null, null, null, "你好世界 hello world foo",
                "my-post", ContentStatus.DRAFT.getKey(), null, null, null, 0L, null, null);
        service.update(1L, req);

        org.mockito.ArgumentCaptor<BlogPost> captor = org.mockito.ArgumentCaptor.forClass(BlogPost.class);
        verify(blogPostRepository).update(captor.capture());
        assertEquals(7, captor.getValue().getWordCount());
        // 4/300 + 3/200 = 0.0133 + 0.015 = 0.0283 → 0.1 分钟
        assertEquals(new java.math.BigDecimal("0.1"), captor.getValue().getReadingMinutes());
    }

    @Test
    @DisplayName("2026-08-20: EN 三列 excerpt/seoTitle/seoDescription 写入主表")
    void upsertWritesEnSeoFieldsToMainTable() {
        BlogPost post = new BlogPost();
        post.setId(1L);
        post.setStatus(ContentStatus.DRAFT);
        post.setVersion(0L);
        when(blogPostRepository.findById(1L)).thenReturn(post);
        when(blogPostRepository.update(any())).thenReturn(1);

        BlogPostUpsert req = new BlogPostUpsert("Title", null, null, null, "content",
                "my-post", ContentStatus.DRAFT.getKey(),
                "EN excerpt", "EN SEO Title", "EN SEO Desc", 0L, null, null);
        service.update(1L, req);

        org.mockito.ArgumentCaptor<BlogPost> captor = org.mockito.ArgumentCaptor.forClass(BlogPost.class);
        verify(blogPostRepository).update(captor.capture());
        BlogPost saved = captor.getValue();
        assertEquals("EN excerpt", saved.getExcerpt());
        assertEquals("EN SEO Title", saved.getSeoTitle());
        assertEquals("EN SEO Desc", saved.getSeoDescription());
    }

    // ===== 2026-08-21 新增：publishedAt 可编辑（首次发布时间运营可改，null 维持自动语义） =====

    @Test
    @DisplayName("2026-08-21: update 显式带 publishedAt → 覆盖现有值（运营改首次发布时间）")
    void updateExplicitPublishedAtOverridesExisting() {
        BlogPost post = new BlogPost();
        post.setId(1L);
        post.setStatus(ContentStatus.PUBLISHED);
        post.setSlug("my-post");
        post.setVersion(0L);
        post.setPublishedAt(java.time.LocalDateTime.of(2026, 8, 1, 10, 0));
        when(blogPostRepository.findById(1L)).thenReturn(post);
        when(blogPostRepository.update(any())).thenReturn(1);

        java.time.LocalDateTime newTime = java.time.LocalDateTime.of(2026, 7, 15, 9, 30);
        BlogPostUpsert req = new BlogPostUpsert("Title", null, null, null, "content",
                "my-post", ContentStatus.PUBLISHED.getKey(), null, null, null, 0L, newTime, null);
        service.update(1L, req);

        org.mockito.ArgumentCaptor<BlogPost> captor = org.mockito.ArgumentCaptor.forClass(BlogPost.class);
        verify(blogPostRepository).update(captor.capture());
        assertEquals(newTime, captor.getValue().getPublishedAt());
    }

    @Test
    @DisplayName("2026-08-21: update publishedAt=null → 保留 DB 现值（不刷新不覆盖）")
    void updateNullPublishedAtKeepsExisting() {
        BlogPost post = new BlogPost();
        post.setId(1L);
        post.setStatus(ContentStatus.PUBLISHED);
        post.setSlug("my-post");
        post.setVersion(0L);
        java.time.LocalDateTime original = java.time.LocalDateTime.of(2026, 8, 1, 10, 0);
        post.setPublishedAt(original);
        when(blogPostRepository.findById(1L)).thenReturn(post);
        when(blogPostRepository.update(any())).thenReturn(1);

        BlogPostUpsert req = new BlogPostUpsert("Title", null, null, null, "content",
                "my-post", ContentStatus.PUBLISHED.getKey(), null, null, null, 0L, null, null);
        service.update(1L, req);

        org.mockito.ArgumentCaptor<BlogPost> captor = org.mockito.ArgumentCaptor.forClass(BlogPost.class);
        verify(blogPostRepository).update(captor.capture());
        assertEquals(original, captor.getValue().getPublishedAt());
    }

    @Test
    @DisplayName("2026-08-21: publishedAt 是未来时间 → 422 published_at future_not_allowed")
    void upsertFuturePublishedAtThrows422() {
        BlogPost post = new BlogPost();
        post.setId(1L);
        post.setStatus(ContentStatus.DRAFT);
        post.setVersion(0L);
        when(blogPostRepository.findById(1L)).thenReturn(post);

        java.time.LocalDateTime future = java.time.LocalDateTime.now().plusDays(1);
        BlogPostUpsert req = new BlogPostUpsert("Title", null, null, null, "content",
                "my-post", ContentStatus.DRAFT.getKey(), null, null, null, 0L, future, null);
        MarketingException ex = assertThrows(MarketingException.class, () -> service.update(1L, req));
        assertEquals(MarketingErrorCode.FIELD_VALIDATION_FAILED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("2026-08-21: create 显式带 publishedAt → 优先于 status=published 自动 now()")
    void createExplicitPublishedAtOverridesAuto() {
        when(blogPostRepository.existsBySlugExcept("my-post", null)).thenReturn(false);
        // create() 末尾 toDto(findById(...))——mock insert 后 findById 返回带 id 的对象
        BlogPost persisted = new BlogPost();
        persisted.setId(99L);
        persisted.setStatus(ContentStatus.PUBLISHED);
        persisted.setSlug("my-post");
        persisted.setViews(0);
        when(blogPostRepository.findById(any())).thenReturn(persisted);

        java.time.LocalDateTime explicit = java.time.LocalDateTime.of(2026, 6, 1, 12, 0);
        BlogPostUpsert req = new BlogPostUpsert("Title", null, null, null, "content",
                "my-post", ContentStatus.PUBLISHED.getKey(), null, null, null, null, explicit, null);
        service.create(req);

        org.mockito.ArgumentCaptor<BlogPost> captor = org.mockito.ArgumentCaptor.forClass(BlogPost.class);
        verify(blogPostRepository).insert(captor.capture());
        assertEquals(explicit, captor.getValue().getPublishedAt());
    }
}
