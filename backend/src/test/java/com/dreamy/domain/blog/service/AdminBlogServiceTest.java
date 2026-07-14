package com.dreamy.domain.blog.service;

import com.dreamy.domain.blog.entity.BlogPost;
import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.enums.ContentStatus;
import com.dreamy.infra.MarketingAfterCommitRunner;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.mq.MarketingContentInvalidatedPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBlogServiceTest {

    @Mock
    BlogPostRepository blogPostRepository;
    @Mock
    MarketingCacheService cache;
    @Mock
    MarketingAuditRecorder audit;
    @Mock
    MarketingAfterCommitRunner afterCommit;
    @Mock
    MarketingContentInvalidatedPublisher publisher;
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
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommit).run(callback.capture());
        callback.getValue().run();
        verify(cache).invalidateFamily(Family.BLOGS);
        verify(cache).invalidateBlogSlug("wedding-guide");
        verify(publisher).publishBlog("wedding-guide", null);
    }
}
