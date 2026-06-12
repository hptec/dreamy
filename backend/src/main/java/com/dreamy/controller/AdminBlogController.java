package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.blog.service.AdminBlogService;
import com.dreamy.dto.AdminMarketingDtos.BlogPostDto;
import com.dreamy.dto.AdminMarketingDtos.BlogPostUpsert;
import com.dreamy.dto.AdminMarketingDtos.StatusPatch;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台博客控制器（E-MKT-26~31；RBAC `/content/blog`；不缓存）。
 */
@RestController
public class AdminBlogController {

    private static final String PERMISSION = "/content/blog";

    private final AdminBlogService adminBlogService;

    public AdminBlogController(AdminBlogService adminBlogService) {
        this.adminBlogService = adminBlogService;
    }

    /** E-MKT-26 listAdminBlogs */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/content/blogs")
    public ResponseEntity<R<Paginated<BlogPostDto>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(R.ok(adminBlogService.page(page, pageSize, status, search)));
    }

    /** E-MKT-27 createAdminBlog（TX-MKT-011） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/content/blogs")
    public ResponseEntity<R<BlogPostDto>> create(@RequestBody BlogPostUpsert req) {
        return ResponseEntity.status(201).body(R.ok(adminBlogService.create(req)));
    }

    /** E-MKT-28 getAdminBlog（编辑详情，translations 三语 tab 原样） */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/content/blogs/{id}")
    public ResponseEntity<R<BlogPostDto>> get(@PathVariable String id) {
        return ResponseEntity.ok(R.ok(adminBlogService.get(parseId(id))));
    }

    /** E-MKT-29 updateAdminBlog（TX-MKT-012；已发布保存即失效链 s-758） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/content/blogs/{id}")
    public ResponseEntity<R<BlogPostDto>> update(@PathVariable String id, @RequestBody BlogPostUpsert req) {
        return ResponseEntity.ok(R.ok(adminBlogService.update(parseId(id), req)));
    }

    /** E-MKT-30 deleteAdminBlog（TX-MKT-013） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/content/blogs/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminBlogService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** E-MKT-31 patchAdminBlogStatus（TX-MKT-014；publish 缺 slug → 422704） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/content/blogs/{id}/status")
    public ResponseEntity<R<BlogPostDto>> patchStatus(@PathVariable String id, @RequestBody StatusPatch req) {
        return ResponseEntity.ok(R.ok(adminBlogService.patchStatus(parseId(id), req.status())));
    }

    /** V-MKT-055：id 非法视同不存在 → 404701 */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
    }
}
