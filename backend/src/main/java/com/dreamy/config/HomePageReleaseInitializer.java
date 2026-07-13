package com.dreamy.config;

import com.dreamy.domain.site_builder.service.HomePagePublicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class HomePageReleaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(HomePageReleaseInitializer.class);
    private final HomePagePublicationService publicationService;

    public HomePageReleaseInitializer(HomePagePublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(110)
    public void init() {
        publicationService.ensureInitialRelease();
        log.info("[HomePageRelease] 初始线上快照已就绪");
    }
}
