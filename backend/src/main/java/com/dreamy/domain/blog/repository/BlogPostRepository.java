package com.dreamy.domain.blog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.blog.entity.BlogPost;
import com.dreamy.domain.blog.entity.BlogPostTranslation;
import com.dreamy.enums.ContentStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 博客仓储（RM-MKT-020~032）。
 * L2 TRACE: marketing-data-detail §2 BlogPostRepository / BlogPostTranslationRepository。
 */
@Repository
public class BlogPostRepository {

    private final BlogPostMapper blogPostMapper;
    private final BlogPostTranslationMapper translationMapper;

    public BlogPostRepository(BlogPostMapper blogPostMapper, BlogPostTranslationMapper translationMapper) {
        this.blogPostMapper = blogPostMapper;
        this.translationMapper = translationMapper;
    }

    /** RM-MKT-020 pageStorePublished —— status='published' ORDER BY published_at DESC, id DESC（IDX-MKT-005） */
    public Page<BlogPost> pageStorePublished(String category, int page, int pageSize) {
        LambdaQueryWrapper<BlogPost> qw = new LambdaQueryWrapper<BlogPost>()
                .isNull(BlogPost::getDeletedAt)
                .eq(BlogPost::getStatus, ContentStatus.PUBLISHED);
        if (category != null) {
            qw.eq(BlogPost::getCategory, category);
        }
        qw.orderByDesc(BlogPost::getPublishedAt).orderByDesc(BlogPost::getId);
        return blogPostMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** RM-MKT-021 findBySlugPublished —— uk_blog_slug 点查（E-MKT-03 热路径） */
    public BlogPost findBySlugPublished(String slug) {
        return blogPostMapper.selectOne(new LambdaQueryWrapper<BlogPost>()
                .isNull(BlogPost::getDeletedAt)
                .eq(BlogPost::getSlug, slug)
                .eq(BlogPost::getStatus, ContentStatus.PUBLISHED));
    }

    /** RM-MKT-022 pageAdmin —— title LIKE（E-MKT-26，ORDER BY COALESCE(published_at, created_at) DESC, id DESC） */
    public Page<BlogPost> pageAdmin(ContentStatus status, String search, int page, int pageSize) {
        LambdaQueryWrapper<BlogPost> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(BlogPost::getStatus, status);
        }
        if (search != null) {
            qw.like(BlogPost::getTitle, search);
        }
        qw.last("ORDER BY COALESCE(published_at, created_at) DESC, id DESC");
        return blogPostMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** RM-MKT-023 findById */
    public BlogPost findById(Long id) {
        BlogPost e = id == null ? null : blogPostMapper.selectById(id);
        return (e == null || e.getDeletedAt() != null) ? null : e;
    }

    /** RM-MKT-024 existsBySlugExcept —— 409702 */
    public boolean existsBySlugExcept(String slug, Long exceptId) {
        LambdaQueryWrapper<BlogPost> qw = new LambdaQueryWrapper<BlogPost>()
                .isNull(BlogPost::getDeletedAt)
                .eq(BlogPost::getSlug, slug);
        if (exceptId != null) {
            qw.ne(BlogPost::getId, exceptId);
        }
        return blogPostMapper.selectCount(qw) > 0;
    }

    /** RM-MKT-025 insert */
    public void insert(BlogPost post) {
        blogPostMapper.insert(post);
    }

    /** RM-MKT-026 update（SET 不含 views——V-MKT-056 只读列；published_at 由调用方按迁移语义传入） */
    public void update(BlogPost post) {
        blogPostMapper.update(null, new LambdaUpdateWrapper<BlogPost>()
                .eq(BlogPost::getId, post.getId())
                .set(BlogPost::getTitle, post.getTitle())
                .set(BlogPost::getCover, post.getCover())
                .set(BlogPost::getCategory, post.getCategory())
                .set(BlogPost::getAuthor, post.getAuthor())
                .set(BlogPost::getContent, post.getContent())
                .set(BlogPost::getSlug, post.getSlug())
                .set(BlogPost::getStatus, post.getStatus())
                .set(BlogPost::getPublishedAt, post.getPublishedAt()));
    }

    /** RM-MKT-027 deleteById */
    public void deleteById(Long id) {
        blogPostMapper.deleteById(id);
    }

    /** RM-MKT-028 updateStatus（publishedAt 仅 draft→published 首发时给定） */
    public void updateStatus(Long id, ContentStatus status, LocalDateTime publishedAt) {
        LambdaUpdateWrapper<BlogPost> uw = new LambdaUpdateWrapper<BlogPost>()
                .eq(BlogPost::getId, id)
                .set(BlogPost::getStatus, status);
        if (publishedAt != null) {
            uw.set(BlogPost::getPublishedAt, publishedAt);
        }
        blogPostMapper.update(null, uw);
    }

    /** RM-MKT-029 incrementViews —— views=views+delta（仅 SCHED-MKT-02，TX-MKT-030） */
    public void incrementViews(Long id, int delta) {
        blogPostMapper.update(null, new LambdaUpdateWrapper<BlogPost>()
                .eq(BlogPost::getId, id)
                .setSql("views = views + " + delta));
    }

    /** RM-MKT-030 listTranslationsByPostIds —— 批查防 N+1（NP-MKT-001） */
    public List<BlogPostTranslation> listTranslationsByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<BlogPostTranslation>()
                .in(BlogPostTranslation::getBlogPostId, postIds));
    }

    /** RM-MKT-031 replaceTranslations —— DELETE+批量 INSERT（整单覆盖） */
    public void replaceTranslations(Long postId, List<BlogPostTranslation> rows) {
        deleteTranslationsByPostId(postId);
        if (rows != null) {
            for (BlogPostTranslation row : rows) {
                row.setBlogPostId(postId);
                translationMapper.insert(row);
            }
        }
    }

    /** RM-MKT-032 deleteByPostId */
    public void deleteTranslationsByPostId(Long postId) {
        translationMapper.delete(new LambdaQueryWrapper<BlogPostTranslation>()
                .eq(BlogPostTranslation::getBlogPostId, postId));
    }
}
