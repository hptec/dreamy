package com.dreamy.domain.blog.service;

import com.dreamy.domain.blog.entity.BlogPost;
import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.enums.ContentStatus;
import com.dreamy.infra.MarketingAuditRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
