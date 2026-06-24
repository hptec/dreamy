package com.dreamy.controller;

import com.dreamy.domain.site_builder.service.StoreContentService;
import com.dreamy.dto.SiteBuilderDtos.StoreAnnouncementListDto;
import com.dreamy.dto.SiteBuilderDtos.StoreFooterDto;
import com.dreamy.dto.SiteBuilderDtos.StoreHomePageDto;
import com.dreamy.dto.SiteBuilderDtos.StoreNavigationDto;
import com.dreamy.i18n.RequestLocaleContext;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * site_builder 消费端内容控制器（/api/store/content/*）。
 * 全部匿名公开（security: []）。
 * KD-5 消费端全量改造：首页/header/footer/公告动态渲染。
 */
@RestController
public class StoreSiteBuilderController {

    private final StoreContentService storeContentService;

    public StoreSiteBuilderController(StoreContentService storeContentService) {
        this.storeContentService = storeContentService;
    }

    @GetMapping("/api/store/content/home")
    public ResponseEntity<R<StoreHomePageDto>> getHome(@RequestParam(required = false) String locale) {
        String resolvedLocale = resolveLocale(locale);
        return ResponseEntity.ok(R.ok(storeContentService.getHome(resolvedLocale)));
    }

    @GetMapping("/api/store/content/navigation")
    public ResponseEntity<R<StoreNavigationDto>> getNavigation(@RequestParam(required = false) String locale) {
        String resolvedLocale = resolveLocale(locale);
        return ResponseEntity.ok(R.ok(storeContentService.getNavigation(resolvedLocale)));
    }

    @GetMapping("/api/store/content/footer")
    public ResponseEntity<R<StoreFooterDto>> getFooter(@RequestParam(required = false) String locale) {
        String resolvedLocale = resolveLocale(locale);
        return ResponseEntity.ok(R.ok(storeContentService.getFooter(resolvedLocale)));
    }

    @GetMapping("/api/store/content/announcements")
    public ResponseEntity<R<StoreAnnouncementListDto>> getAnnouncements(
            @RequestParam(required = false) String locale) {
        String resolvedLocale = resolveLocale(locale);
        return ResponseEntity.ok(R.ok(storeContentService.getAnnouncements(resolvedLocale)));
    }

    // 注：POST /api/store/newsletter 由基线 StoreLeadController 提供（E-MKT-11）
    // KD-13：site_builder 域首页 Newsletter 区块订阅复用基线端点，source=HOME_BLOCK(4)
    // 前端调用时传 source=4 即可，无需新增端点

    private String resolveLocale(String fromQuery) {
        if (fromQuery != null && !fromQuery.isEmpty()) {
            return fromQuery;
        }
        try {
            String ctxLocale = RequestLocaleContext.current();
            if (ctxLocale != null && !ctxLocale.isEmpty()) {
                return ctxLocale;
            }
        } catch (Exception ignored) {
        }
        return "en";
    }
}
