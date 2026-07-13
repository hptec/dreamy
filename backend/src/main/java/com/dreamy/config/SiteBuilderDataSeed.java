package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.site_builder.entity.Announcement;
import com.dreamy.domain.site_builder.entity.FooterColumn;
import com.dreamy.domain.site_builder.entity.FooterLink;
import com.dreamy.domain.site_builder.entity.HomePageSection;
import com.dreamy.domain.site_builder.entity.NavigationItem;
import com.dreamy.domain.site_builder.repository.AnnouncementRepository;
import com.dreamy.domain.site_builder.repository.FooterRepository;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.domain.site_builder.repository.NavigationItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * site_builder 域演示数据 seed（幂等）。
 * SF-L4-02：HomeBuilder.vue + NavigationConfig.vue UI 预览需要真实 API 数据。
 * 监听 ApplicationReadyEvent，在 DataInitializer（默认 @Order 0）之后执行。
 * 首页区块按 section_type 补齐，不覆盖已有运营数据（幂等）。
 */
@Component
public class SiteBuilderDataSeed {

    private static final Logger log = LoggerFactory.getLogger(SiteBuilderDataSeed.class);

    private final HomePageSectionRepository homeSectionRepository;
    private final NavigationItemRepository navigationRepository;
    private final FooterRepository footerRepository;
    private final AnnouncementRepository announcementRepository;

    public SiteBuilderDataSeed(HomePageSectionRepository homeSectionRepository,
                               NavigationItemRepository navigationRepository,
                               FooterRepository footerRepository,
                               AnnouncementRepository announcementRepository) {
        this.homeSectionRepository = homeSectionRepository;
        this.navigationRepository = navigationRepository;
        this.footerRepository = footerRepository;
        this.announcementRepository = announcementRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    @Transactional
    public void init() {
        seedHomeSections();
        seedNavigation();
        seedFooter();
        seedAnnouncement();
        log.info("[SiteBuilderDataSeed] site_builder 域演示数据初始化完成");
    }

    /** 5 个首页区块：Hero / ThemeCards / ProductRail / EditorialFeature / Newsletter */
    private void seedHomeSections() {
        Set<String> existingTypes = new HashSet<>();
        homeSectionRepository.findAllOrderBySort().stream()
                .map(HomePageSection::getSectionType)
                .forEach(existingTypes::add);

        seedHomeSection(existingTypes, "hero", 1, true, null, null, "Hero 主视觉");
        seedHomeSection(existingTypes, "theme_cards", 2, true, "{\"mode\":\"auto\",\"limit\":6}",
                "{\"en\":{\"eyebrow\":\"Explore\",\"heading\":\"Shop by Theme\",\"description\":\"Find the setting that feels like your story.\"},\"es\":{\"eyebrow\":\"Explorar\",\"heading\":\"Comprar por Tema\",\"description\":\"Encuentra el escenario que se parece a tu historia.\"},\"fr\":{\"eyebrow\":\"Explorer\",\"heading\":\"Acheter par Thème\",\"description\":\"Trouvez le décor qui ressemble à votre histoire.\"}}",
                "主题分类卡片");
        seedHomeSection(existingTypes, "product_rail", 3, true, "{\"source\":\"new_arrival\",\"limit\":4,\"sort\":\"newest\"}",
                "{\"en\":{\"eyebrow\":\"Just in\",\"heading\":\"New Arrivals\",\"description\":\"Fresh silhouettes for celebrations under open skies.\"},\"es\":{\"eyebrow\":\"Novedades\",\"heading\":\"Recién Llegados\",\"description\":\"Nuevas siluetas para celebraciones al aire libre.\"},\"fr\":{\"eyebrow\":\"Nouveautés\",\"heading\":\"Nouveaux Arrivages\",\"description\":\"De nouvelles silhouettes pour célébrer à ciel ouvert.\"}}",
                "新品推荐");
        seedHomeSection(existingTypes, "editorial_feature", 4, true, "{\"limit\":3}",
                "{\"en\":{\"eyebrow\":\"Real love stories\",\"heading\":\"Real Outdoor Weddings\",\"description\":\"Celebrations, details, and dresses from real Dreamy couples.\"},\"es\":{\"eyebrow\":\"Historias de amor reales\",\"heading\":\"Bodas Reales al Aire Libre\",\"description\":\"Celebraciones, detalles y vestidos de parejas Dreamy reales.\"},\"fr\":{\"eyebrow\":\"Vraies histoires d'amour\",\"heading\":\"Vrais Mariages en Plein Air\",\"description\":\"Célébrations, détails et robes de vrais couples Dreamy.\"}}",
                "真实婚礼故事");
        seedHomeSection(existingTypes, "newsletter", 5, true, null,
                "{\"en\":{\"eyebrow\":\"Stay in touch\",\"heading\":\"Join the Dreamy List\",\"description\":\"New collections, planning inspiration, and private offers—sent thoughtfully.\",\"placeholder\":\"Your email\",\"cta\":\"Subscribe\"},\"es\":{\"eyebrow\":\"Sigamos en contacto\",\"heading\":\"Únete a la Lista Dreamy\",\"description\":\"Nuevas colecciones, inspiración y ofertas privadas.\",\"placeholder\":\"Tu correo\",\"cta\":\"Suscribirse\"},\"fr\":{\"eyebrow\":\"Restons en contact\",\"heading\":\"Rejoindre la Liste Dreamy\",\"description\":\"Nouvelles collections, inspirations et offres privées.\",\"placeholder\":\"Votre e-mail\",\"cta\":\"S'abonner\"}}",
                "邮件订阅");
    }

    private void seedHomeSection(Set<String> existingTypes, String type, int sort, boolean enabled,
                                 String dataJson, String i18nJson, String label) {
        if (!existingTypes.add(type)) {
            return;
        }
        homeSectionRepository.insert(buildSection(type, sort, enabled, dataJson, i18nJson, label));
        log.info("[SiteBuilderDataSeed] 首页区块 {} 已补齐", type);
    }

    private HomePageSection buildSection(String type, int sort, boolean enabled,
                                         String dataJson, String i18nJson, String label) {
        HomePageSection s = new HomePageSection();
        s.setSectionType(type);
        s.setSortOrder(sort);
        s.setEnabled(enabled);
        s.setDataJson(dataJson);
        s.setI18nJson(i18nJson);
        s.setLabel(label);
        s.setVersion(0);
        return s;
    }

    /** 3 个顶级导航项：Home / Shop / Real Weddings */
    private void seedNavigation() {
        if (navigationRepository.findAllOrderBySort().size() > 0) {
            return;
        }
        navigationRepository.insert(buildNav("Home", "/", 1, "custom", null));
        navigationRepository.insert(buildNav("Shop", "/products", 2, "custom", null));
        navigationRepository.insert(buildNav("Real Weddings", "/real-weddings", 3, "custom", null));
        log.info("[SiteBuilderDataSeed] navigation_items 3 条演示数据已初始化");
    }

    private NavigationItem buildNav(String label, String url, int sort, String linkType, Long taxonomyId) {
        NavigationItem n = new NavigationItem();
        n.setLabel(label);
        n.setUrl(url);
        n.setSortOrder(sort);
        n.setEnabled(true);
        n.setTarget("self");
        n.setLinkType(linkType);
        n.setTaxonomyId(taxonomyId);
        n.setVersion(0);
        return n;
    }

    /** 2 个页脚栏目 + 5 个链接 */
    private void seedFooter() {
        if (footerRepository.findAllColumnsOrderBySort().size() > 0) {
            return;
        }
        FooterColumn c1 = new FooterColumn();
        c1.setTitle("Shop");
        c1.setSortOrder(1);
        c1.setEnabled(true);
        c1.setVersion(0);
        footerRepository.insertColumn(c1);

        FooterColumn c2 = new FooterColumn();
        c2.setTitle("Company");
        c2.setSortOrder(2);
        c2.setEnabled(true);
        c2.setVersion(0);
        footerRepository.insertColumn(c2);

        footerRepository.insertLink(buildLink(c1.getId(), "All Dresses", "/products", 1));
        footerRepository.insertLink(buildLink(c1.getId(), "New Arrivals", "/products?sort=new", 2));
        footerRepository.insertLink(buildLink(c1.getId(), "Best Sellers", "/products?sort=best", 3));
        footerRepository.insertLink(buildLink(c2.getId(), "About Us", "/about", 1));
        footerRepository.insertLink(buildLink(c2.getId(), "Contact", "/contact", 2));
        log.info("[SiteBuilderDataSeed] footer 2 栏目 + 5 链接演示数据已初始化");
    }

    private FooterLink buildLink(Long columnId, String label, String url, int sort) {
        FooterLink l = new FooterLink();
        l.setColumnId(columnId);
        l.setLabel(label);
        l.setUrl(url);
        l.setSortOrder(sort);
        l.setTarget("self");
        l.setVersion(0);
        return l;
    }

    /** 1 条公告（高优先级，长期有效） */
    private void seedAnnouncement() {
        if (announcementRepository.findAllOrderByPriorityId(1, 1, null).getTotal() > 0) {
            return;
        }
        Announcement a = new Announcement();
        a.setEnabled(true);
        a.setPriority(100);
        a.setStartAt(LocalDateTime.now().minusDays(1));
        a.setEndAt(LocalDateTime.now().plusDays(365));
        a.setContent("Free shipping on all orders over $500 — limited time only.");
        a.setContentI18nJson("{\"en\":{\"content\":\"Free shipping on all orders over $500 — limited time only.\"},"
                + "\"es\":{\"content\":\"Envío gratis en todos los pedidos superiores a $500 — tiempo limitado.\"},"
                + "\"fr\":{\"content\":\"Livraison gratuite sur toutes les commandes supérieures à 500 $ — durée limitée.\"}}");
        a.setVersion(0);
        announcementRepository.insert(a);
        log.info("[SiteBuilderDataSeed] announcements 1 条演示数据已初始化");
    }
}
