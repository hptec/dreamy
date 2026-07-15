package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.role.entity.Permission;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.role.entity.RolePermission;
import com.dreamy.domain.role.repository.PermissionMapper;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.repository.RolePermissionMapper;
import com.dreamy.domain.banner.entity.Banner;
import com.dreamy.domain.banner.entity.BannerTranslation;
import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.domain.blog.entity.BlogPost;
import com.dreamy.domain.blog.entity.BlogPostTranslation;
import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.domain.coupon.entity.Coupon;
import com.dreamy.domain.coupon.entity.CouponTranslation;
import com.dreamy.domain.coupon.repository.CouponMapper;
import com.dreamy.domain.coupon.repository.CouponRepository;
import com.dreamy.enums.BannerPosition;
import com.dreamy.enums.ContentStatus;
import com.dreamy.enums.CouponStatus;
import com.dreamy.enums.CouponType;
import com.dreamy.enums.FlashSaleStatus;
import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.flashsale.entity.FlashSale;
import com.dreamy.domain.flashsale.entity.FlashSaleTranslation;
import com.dreamy.domain.flashsale.repository.FlashSaleRepository;
import com.dreamy.domain.guide.entity.Guide;
import com.dreamy.domain.guide.entity.GuideTranslation;
import com.dreamy.domain.guide.repository.GuideRepository;
import com.dreamy.domain.lookbook.entity.Lookbook;
import com.dreamy.domain.lookbook.entity.LookbookTranslation;
import com.dreamy.domain.lookbook.repository.LookbookRepository;
import com.dreamy.domain.wedding.entity.RealWedding;
import com.dreamy.domain.wedding.entity.RealWeddingTranslation;
import com.dreamy.domain.wedding.repository.RealWeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * marketing 域种子数据初始化（决策 21：从 frontend portal-admin mock.js（coupons/flashSales/banners/
 * blogPosts/lookbooks/guides/realWeddings）+ portal-store data/content.ts 提炼，含三语 translation；
 * newsletter/contact 纯收集表空表起步——marketing-data-detail §11 备注②）。
 * 幂等：Banner 按内部名称独立补齐；其余 marketing 数据在 coupon 表非空时跳过。
 * 同时登记 RBAC 权限点 /promotions、/banners（marketing-api-detail §0 菜单权限 key；
 * /content/blog、/content/weddings、/content/lookbook 已在 identity 种子）。
 */
@Component
@Order(30)
@ConditionalOnProperty(prefix = "dreamy.seed", name = "demo-enabled", havingValue = "true")
public class MarketingSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(MarketingSeedInitializer.class);
    private static final String REF = "/competitor-refs";

    private final CouponMapper couponMapper;
    private final CouponRepository couponRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final BannerRepository bannerRepository;
    private final BlogPostRepository blogPostRepository;
    private final RealWeddingRepository weddingRepository;
    private final LookbookRepository lookbookRepository;
    private final GuideRepository guideRepository;
    private final ProductRepository catalogProductRepository;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public MarketingSeedInitializer(CouponMapper couponMapper, CouponRepository couponRepository,
                                    FlashSaleRepository flashSaleRepository, BannerRepository bannerRepository,
                                    BlogPostRepository blogPostRepository, RealWeddingRepository weddingRepository,
                                    LookbookRepository lookbookRepository, GuideRepository guideRepository,
                                    ProductRepository catalogProductRepository, PermissionMapper permissionMapper,
                                    RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper) {
        this.couponMapper = couponMapper;
        this.couponRepository = couponRepository;
        this.flashSaleRepository = flashSaleRepository;
        this.bannerRepository = bannerRepository;
        this.blogPostRepository = blogPostRepository;
        this.weddingRepository = weddingRepository;
        this.lookbookRepository = lookbookRepository;
        this.guideRepository = guideRepository;
        this.catalogProductRepository = catalogProductRepository;
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        ensurePermission("/promotions", "营销活动", "促销管理（券与闪购）");
        ensurePermission("/banners", "站点装修", "Banner 投放");
        seedBanners();
        if (couponMapper.selectCount(null) > 0) {
            return;
        }
        List<Long> productIds = publishedProductIds(12);
        seedCoupons();
        seedFlashSales(productIds);
        seedBlogPosts();
        seedRealWeddings(productIds);
        seedLookbooks(productIds);
        seedGuides();
        log.info("[MarketingSeed] marketing 种子数据初始化完成（newsletter/contact 空表起步，决策 26/30）");
    }

    /** RBAC 权限点登记（幂等按 perm_code）+ 绑定超管角色（与 CatalogSeedInitializer 同惯例） */
    private void ensurePermission(String permCode, String group, String label) {
        Permission permission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermCode, permCode));
        if (permission == null) {
            permission = new Permission();
            permission.setPermCode(permCode);
            permission.setGroup(group);
            permission.setLabel(label);
            permissionMapper.insert(permission);
            log.info("[MarketingSeed] 权限点 {} 已登记", permCode);
        }
        Role superRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getName, "超级管理员"));
        if (superRole != null) {
            Long bound = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                    .eq(RolePermission::getRoleId, superRole.getId())
                    .eq(RolePermission::getPermissionId, permission.getId()));
            if (bound == null || bound == 0) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(superRole.getId());
                rp.setPermissionId(permission.getId());
                rolePermissionMapper.insert(rp);
            }
        }
    }

    private List<Long> publishedProductIds(int limit) {
        try {
            return catalogProductRepository.listRecoNewArrivals(limit).stream().map(Product::getId).toList();
        } catch (Exception ex) {
            log.warn("[MarketingSeed] catalog 商品未就绪，内容商品挂载留空");
            return List.of();
        }
    }

    /** mock.js coupons ×4（DEC-MKT-5：99999>9999 归一为缺省 100000=不限） */
    private void seedCoupons() {
        seedCoupon("WELCOME15", "Welcome 15% Off", CouponType.DISCOUNT, "15% OFF", BigDecimal.ZERO, 5000, 1842,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59), CouponStatus.ACTIVE,
                "15% off your first order",
                "15% de descuento en tu primer pedido", "15% de réduction sur votre première commande",
                "Bienvenida 15%", "Bienvenue 15%");
        seedCoupon("SHIP200", "Free Shipping over $200", CouponType.FREE_SHIPPING, "Free Shipping",
                new BigDecimal("200"), 100000, 6240,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59), CouponStatus.ACTIVE,
                "Free shipping on orders over $200",
                "Envío gratis en pedidos superiores a $200", "Livraison gratuite dès 200 $",
                "Envío gratis +$200", "Livraison offerte +200 $");
        seedCoupon("SPRING50", "$50 Off over $500", CouponType.FIXED_AMOUNT, "$50 OFF",
                new BigDecimal("500"), 2000, 980,
                LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 5, 31, 23, 59), CouponStatus.EXPIRED,
                "Spring sale: $50 off orders over $500",
                "$50 de descuento en pedidos de más de $500", "50 $ de réduction dès 500 $",
                "Primavera -$50", "Printemps -50 $");
        seedCoupon("BRIDE10", "Bridesmaid 10% Off", CouponType.DISCOUNT, "10% OFF",
                new BigDecimal("300"), 1000, 0, null, null, CouponStatus.DRAFT,
                "10% off bridesmaid party orders",
                "10% de descuento en pedidos de damas de honor", "10% de réduction demoiselles d'honneur",
                "Damas 10%", "Demoiselles 10%");
    }

    @SuppressWarnings("java:S107")
    private void seedCoupon(String code, String name, CouponType type, String value, BigDecimal minAmount,
                            int totalLimit, int usedCount, LocalDateTime startAt, LocalDateTime endAt,
                            CouponStatus status, String description, String descEs, String descFr,
                            String nameEs, String nameFr) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setName(name);
        coupon.setType(type);
        coupon.setValue(value);
        coupon.setMinAmount(minAmount);
        coupon.setTotalLimit(totalLimit);
        coupon.setUsedCount(usedCount);
        coupon.setStartAt(startAt);
        coupon.setEndAt(endAt);
        coupon.setStatus(status);
        coupon.setDescription(description);
        couponRepository.insert(coupon);
        couponRepository.replaceTranslations(coupon.getId(), List.of(
                couponTranslation("es", nameEs, descEs),
                couponTranslation("fr", nameFr, descFr)));
    }

    private CouponTranslation couponTranslation(String locale, String name, String description) {
        CouponTranslation t = new CouponTranslation();
        t.setLocale(locale);
        t.setName(name);
        t.setDescription(description);
        return t;
    }

    /** mock.js flashSales ×2（窗口取相对时间，保证 dev 起步即有 active 闪购可联调；状态与窗口一致 CV-MKT-011） */
    private void seedFlashSales(List<Long> productIds) {
        LocalDateTime now = LocalDateTime.now();
        FlashSale active = new FlashSale();
        active.setName("Memorial Day Flash");
        active.setDiscount("Up to 40% OFF");
        active.setStartAt(now.minusDays(1));
        active.setEndAt(now.plusDays(3));
        active.setStatus(FlashSaleStatus.ACTIVE);
        flashSaleRepository.insert(active);
        flashSaleRepository.replaceProducts(active.getId(), head(productIds, 12));
        flashSaleRepository.replaceTranslations(active.getId(), List.of(
                flashTranslation("es", "Flash del Día de los Caídos"),
                flashTranslation("fr", "Vente flash Memorial Day")));

        FlashSale scheduled = new FlashSale();
        scheduled.setName("Summer Kickoff");
        scheduled.setDiscount("Up to 30% OFF");
        scheduled.setStartAt(now.plusDays(7));
        scheduled.setEndAt(now.plusDays(10));
        scheduled.setStatus(FlashSaleStatus.SCHEDULED);
        flashSaleRepository.insert(scheduled);
        flashSaleRepository.replaceProducts(scheduled.getId(), head(productIds, 8));
        flashSaleRepository.replaceTranslations(scheduled.getId(), List.of(
                flashTranslation("es", "Arranque de verano"),
                flashTranslation("fr", "Lancement de l'été")));
    }

    private FlashSaleTranslation flashTranslation(String locale, String name) {
        FlashSaleTranslation t = new FlashSaleTranslation();
        t.setLocale(locale);
        t.setName(name);
        return t;
    }

    /** 原型 Banner ×4 + Hero 草稿示例 ×1；按内部名称补齐，不覆盖已有运营数据。 */
    private void seedBanners() {
        Set<String> existingNames = new HashSet<>();
        bannerRepository.listAdmin(null).stream()
                .map(Banner::getName)
                .filter(Objects::nonNull)
                .forEach(existingNames::add);

        seedBanner(existingNames, "Outdoor Edit 2026 主 Banner", REF + "/kissprom/wedding-aline-tulle-01.jpg",
                BannerPosition.HERO, LocalDateTime.of(2026, 5, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59),
                ContentStatus.PUBLISHED, 100, "/wedding-dresses",
                "Dresses made for golden hour", "The Outdoor Wedding Edit · 2026", "Shop the Collection",
                "Vestidos para la hora dorada", "La Edición de Bodas al Aire Libre · 2026", "Ver la colección",
                "Des robes pour l'heure dorée", "L'Édition Mariage en Plein Air · 2026", "Voir la collection");
        seedBanner(existingNames, "Garden Romance Hero（草稿示例）", REF + "/davidsbridal/bridesmaid-sage-01.jpg",
                BannerPosition.HERO, null, null, ContentStatus.DRAFT, 10, "/outdoor-weddings",
                "Where will you say I do?", "Garden Wedding Edit · Draft", "Preview the Edit",
                "¿Dónde darás el sí?", "Edición Boda en el Jardín · Borrador", "Vista previa",
                "Où allez-vous dire oui ?", "Édition Mariage au Jardin · Brouillon", "Prévisualiser");
        seedBanner(existingNames, "Bridesmaid Color 推广", REF + "/birdygrey/bridesmaid-pink-bella-01.jpg",
                BannerPosition.FEATURED, LocalDateTime.of(2026, 5, 10, 0, 0), LocalDateTime.of(2026, 7, 10, 23, 59),
                ContentStatus.PUBLISHED, 90, "/special-occasion",
                "Bridesmaid Colors for 2026", "Sage, dusty blue and terracotta", "Explore Colors",
                "Colores de damas 2026", "Salvia, azul polvo y terracota", "Explorar colores",
                "Couleurs demoiselles 2026", "Sauge, bleu poudré et terracotta", "Explorer");
        seedBanner(existingNames, "Klarna 分期付款条", REF + "/davidsbridal/wedding-dress-04.jpg",
                BannerPosition.TOPBAR, LocalDateTime.of(2026, 4, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59),
                ContentStatus.PUBLISHED, 80, null,
                "Pay in 4 with Klarna", "Interest-free installments at checkout", "Learn More",
                "Paga en 4 con Klarna", "Cuotas sin interés al pagar", "Saber más",
                "Payez en 4 avec Klarna", "Mensualités sans frais au paiement", "En savoir plus");
        seedBanner(existingNames, "Spring Sale 春季促销", REF + "/kissprom/prom-champagne-lace-05.jpg",
                BannerPosition.FEATURED, LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 5, 30, 23, 59),
                ContentStatus.ARCHIVED, 60, "/special-occasion",
                "Spring Sale", "Up to 30% off selected styles", "Shop Sale",
                "Rebajas de primavera", "Hasta 30% en estilos seleccionados", "Comprar",
                "Soldes de printemps", "Jusqu'à -30% sur une sélection", "Voir les soldes");
    }

    @SuppressWarnings("java:S107")
    private void seedBanner(Set<String> existingNames, String name, String imageUrl,
                            BannerPosition position, LocalDateTime startTime,
                            LocalDateTime endTime, ContentStatus status, int sort,
                            String ctaLink, String title, String subtitle, String cta,
                            String titleEs, String subtitleEs, String ctaEs,
                            String titleFr, String subtitleFr, String ctaFr) {
        if (!existingNames.add(name)) {
            return;
        }
        Banner banner = new Banner();
        banner.setName(name);
        banner.setImageUrl(imageUrl);
        banner.setPosition(position);
        banner.setStartTime(startTime);
        banner.setEndTime(endTime);
        banner.setStatus(status);
        banner.setSort(sort);
        banner.setTitle(title);
        banner.setSubtitle(subtitle);
        banner.setCtaText(cta);
        banner.setCtaLink(ctaLink);
        bannerRepository.insert(banner);
        bannerRepository.replaceTranslations(banner.getId(), List.of(
                bannerTranslation("es", titleEs, subtitleEs, ctaEs),
                bannerTranslation("fr", titleFr, subtitleFr, ctaFr)));
        log.info("[MarketingSeed] Banner {} 已补齐", name);
    }

    private BannerTranslation bannerTranslation(String locale, String title, String subtitle, String cta) {
        BannerTranslation t = new BannerTranslation();
        t.setLocale(locale);
        t.setTitle(title);
        t.setSubtitle(subtitle);
        t.setCtaText(cta);
        return t;
    }

    /** portal-store content.ts blogPosts ×3（published）+ mock.js b-4（draft 无 slug） */
    private void seedBlogPosts() {
        seedBlog("How to Choose the Perfect Outdoor Wedding Dress", "outdoor-wedding-guide", "Planning",
                REF + "/davidsbridal/wedding-dress-04.jpg",
                String.join("\n\n",
                        "An outdoor wedding is a celebration of nature — but it also comes with practical considerations your dress needs to handle gracefully.",
                        "For beach ceremonies, opt for lightweight fabrics like chiffon and tulle that move with the breeze. Skip heavy satin trains that drag through sand.",
                        "Garden and vineyard weddings call for shoes that wont sink into grass — block heels or embellished flats are your best friend.",
                        "Forest weddings tend to be cooler, so consider long sleeves or a beautiful wrap. Sheer illusion sleeves add romance without the weight.",
                        "Whatever your venue, choose a silhouette that lets you move, dance, and breathe. Your wedding day should feel as effortless as it looks."),
                ContentStatus.PUBLISHED, LocalDateTime.of(2026, 4, 12, 10, 0), 8420,
                "Cómo elegir el vestido de novia perfecto para exteriores",
                "De la brisa de la playa al suelo del bosque: cómo elegir un vestido que acompañe a tu lugar de celebración.",
                "Comment choisir la robe de mariée parfaite en plein air",
                null);
        seedBlog("8 Outdoor Bridesmaid Color Palettes for 2026", "bridesmaid-color-palettes", "Inspiration",
                REF + "/birdygrey/bridesmaid-pink-bryten-02.jpg",
                String.join("\n\n",
                        "The right bridesmaid palette ties your entire celebration together. For 2026, earthy and muted tones continue to dominate outdoor weddings.",
                        "Sage green remains the reigning favorite — it photographs beautifully against greenery and pairs with nearly any floral.",
                        "For beachfront affairs, dusty blue evokes the ocean without feeling nautical.",
                        "Vineyard and autumn weddings shine with terracotta, coral, and champagne — warm tones that glow at golden hour.",
                        "Mix two complementary shades for depth, or let each bridesmaid choose her own within a curated family."),
                ContentStatus.PUBLISHED, LocalDateTime.of(2026, 3, 28, 10, 0), 12600,
                "8 paletas de color para damas de honor al aire libre 2026",
                "Salvia, azul polvo, terracota y más: las paletas de esta temporada.",
                "8 palettes de couleurs pour demoiselles d'honneur 2026",
                "Sauge, bleu poudré, terracotta et plus encore.");
        seedBlog("A Bride's Guide to Wedding Dress Fabrics", "fabric-guide", "Education",
                REF + "/kissprom/wedding-mermaid-chiffon-03.jpg",
                String.join("\n\n",
                        "Understanding fabric is the secret to choosing a dress that matches your vision and your venue.",
                        "Tulle is light, airy, and romantic — ideal for ballgowns and A-lines with volume.",
                        "Chiffon flows and drapes, making it perfect for outdoor and destination weddings.",
                        "Satin has a luxurious sheen and structure, great for sculpted silhouettes like the mermaid.",
                        "Lace adds texture and timeless romance, whether as an overlay or delicate appliqué."),
                ContentStatus.PUBLISHED, LocalDateTime.of(2026, 3, 10, 10, 0), 6240,
                "Guía de tejidos para vestidos de novia",
                "Tul, satén, gasa, encaje: entiende cada tejido antes de enamorarte.",
                "Guide des tissus de robes de mariée",
                "Tulle, satin, mousseline, dentelle — comprendre chaque tissu.");
        seedBlog("Vineyard Wedding Styling Tips", null, "Planning",
                REF + "/kissprom/prom-champagne-lace-05.jpg",
                "Golden hour among the vines calls for warm champagne tones and breathable lace.",
                ContentStatus.DRAFT, null, 0, null, null, null, null);
    }

    @SuppressWarnings("java:S107")
    private void seedBlog(String title, String slug, String category, String cover, String content,
                          ContentStatus status, LocalDateTime publishedAt, int views,
                          String titleEs, String excerptEs, String titleFr, String excerptFr) {
        BlogPost post = new BlogPost();
        post.setTitle(title);
        post.setSlug(slug);
        post.setCategory(category);
        post.setAuthor("Dreamy Editorial");
        post.setCover(cover);
        post.setContent(content);
        post.setStatus(status);
        post.setPublishedAt(publishedAt);
        post.setViews(views);
        blogPostRepository.insert(post);
        List<BlogPostTranslation> rows = new ArrayList<>();
        if (titleEs != null) {
            rows.add(blogTranslation("es", titleEs, excerptEs));
        }
        if (titleFr != null) {
            rows.add(blogTranslation("fr", titleFr, excerptFr));
        }
        blogPostRepository.replaceTranslations(post.getId(), rows);
    }

    private BlogPostTranslation blogTranslation(String locale, String title, String excerpt) {
        BlogPostTranslation t = new BlogPostTranslation();
        t.setLocale(locale);
        t.setTitle(title);
        t.setExcerpt(excerpt);
        return t;
    }

    /** content.ts realWeddings ×3（published，Shop the Look 各挂 3 商品）+ mock.js rw-4（draft） */
    private void seedRealWeddings(List<Long> productIds) {
        seedWedding("Emma & James", "Big Sur, California", "Beach", "2025-06",
                REF + "/davidsbridal/wedding-dress-04.jpg", PublishStatus.PUBLISHED,
                "A Cliffside Ceremony Above the Pacific",
                String.join("\n\n",
                        "When Emma and James decided to marry on the cliffs of Big Sur, they wanted everything to feel light, natural, and unforced — like the fog rolling in off the water.",
                        "Emma chose the Aurelia A-Line Tulle Gown for its movement. \"I wanted something that would float when the wind picked up, and it did exactly that,\" she said.",
                        "Her bridesmaids wore the Meadow dress in Sage and Dusty Blue, echoing the eucalyptus and ocean tones of the coastline."),
                head(productIds, 3),
                "Una ceremonia sobre el Pacífico", "Una boda en los acantilados de Big Sur.",
                "Une cérémonie au-dessus du Pacifique", "Un mariage sur les falaises de Big Sur.");
        seedWedding("Sofia & Marco", "Sonoma Valley", "Vineyard", "2025-09",
                REF + "/kissprom/prom-champagne-lace-05.jpg", PublishStatus.PUBLISHED,
                "Golden Hour Among the Grapevines",
                String.join("\n\n",
                        "Sofia and Marco said their vows beneath an oak tree in the heart of Sonoma wine country, surrounded by rows of golden vines.",
                        "The Celeste Lace Gown was a natural choice — its floral appliqué mirrored the botanical details woven throughout the celebration.",
                        "Warm coral and petal bridesmaid dresses caught the last of the September sun, creating a palette that felt straight out of a painting."),
                slice(productIds, 3, 3),
                "Hora dorada entre los viñedos", "Una boda en el valle de Sonoma.",
                "L'heure dorée parmi les vignes", "Un mariage au cœur de Sonoma.");
        seedWedding("Ava & Noah", "Redwood Forest, Oregon", "Forest", "2025-05",
                REF + "/birdygrey/bridesmaid-pink-bryten-02.jpg", PublishStatus.PUBLISHED,
                "An Intimate Woodland Ceremony",
                String.join("\n\n",
                        "Ava and Noah wanted their forest wedding to feel like a fairytale, and the towering redwoods of Oregon delivered.",
                        "The Willow Long-Sleeve Gown brought the romance, with sheer chiffon sleeves that felt perfectly suited to the cool morning air.",
                        "Their bridesmaids in sage and olive blended beautifully with the mossy forest floor."),
                slice(productIds, 6, 3),
                "Una ceremonia íntima en el bosque", "Una boda entre secuoyas en Oregón.",
                "Une cérémonie intime en forêt", "Un mariage parmi les séquoias de l'Oregon.");
        seedWedding("Mia & Liam", "Malibu Beach", "Beach", "2026-04",
                REF + "/kissprom/wedding-beach-short-05.jpg", PublishStatus.DRAFT,
                null, null, List.of(), null, null, null, null);
    }

    @SuppressWarnings("java:S107")
    private void seedWedding(String couple, String location, String theme, String weddingDate, String cover,
                             PublishStatus status, String title, String story, List<Long> productIds,
                             String titleEs, String storyEs, String titleFr, String storyFr) {
        RealWedding wedding = new RealWedding();
        wedding.setCouple(couple);
        wedding.setLocation(location);
        wedding.setTheme(theme);
        wedding.setWeddingDate(weddingDate);
        wedding.setCover(cover);
        wedding.setStatus(status);
        wedding.setTitle(title);
        wedding.setStory(story);
        weddingRepository.insert(wedding);
        weddingRepository.replaceProducts(wedding.getId(), productIds);
        List<RealWeddingTranslation> rows = new ArrayList<>();
        if (titleEs != null) {
            rows.add(weddingTranslation("es", titleEs, storyEs));
        }
        if (titleFr != null) {
            rows.add(weddingTranslation("fr", titleFr, storyFr));
        }
        weddingRepository.replaceTranslations(wedding.getId(), rows);
    }

    private RealWeddingTranslation weddingTranslation(String locale, String title, String story) {
        RealWeddingTranslation t = new RealWeddingTranslation();
        t.setLocale(locale);
        t.setTitle(title);
        t.setStory(story);
        return t;
    }

    /** mock.js lookbooks ×3 */
    private void seedLookbooks(List<Long> productIds) {
        seedLookbook("Golden Hour Collection", "Vineyard", PublishStatus.PUBLISHED,
                "Champagne and terracotta looks that glow at golden hour.", head(productIds, 6),
                "Colección Hora Dorada", "Looks champán y terracota que brillan al atardecer.",
                "Collection Heure Dorée", "Des looks champagne et terracotta au coucher du soleil.");
        seedLookbook("Coastal Romance", "Beach", PublishStatus.PUBLISHED,
                "Airy chiffon and ocean-inspired hues for seaside ceremonies.", slice(productIds, 6, 5),
                "Romance Costero", "Gasa ligera y tonos del océano para bodas junto al mar.",
                "Romance Côtière", "Mousseline aérienne et teintes océanes pour les cérémonies en bord de mer.");
        seedLookbook("Woodland Whisper", "Forest", PublishStatus.DRAFT,
                "Mossy greens and long sleeves for forest celebrations.", List.of(),
                null, null, null, null);
    }

    @SuppressWarnings("java:S107")
    private void seedLookbook(String title, String theme, PublishStatus status, String description,
                              List<Long> productIds, String titleEs, String descEs, String titleFr, String descFr) {
        Lookbook lookbook = new Lookbook();
        lookbook.setTitle(title);
        lookbook.setTheme(theme);
        lookbook.setStatus(status);
        lookbook.setDescription(description);
        lookbookRepository.insert(lookbook);
        lookbookRepository.replaceProducts(lookbook.getId(), productIds);
        List<LookbookTranslation> rows = new ArrayList<>();
        if (titleEs != null) {
            rows.add(lookbookTranslation("es", titleEs, descEs));
        }
        if (titleFr != null) {
            rows.add(lookbookTranslation("fr", titleFr, descFr));
        }
        lookbookRepository.replaceTranslations(lookbook.getId(), rows);
    }

    private LookbookTranslation lookbookTranslation(String locale, String title, String description) {
        LookbookTranslation t = new LookbookTranslation();
        t.setLocale(locale);
        t.setTitle(title);
        t.setDescription(description);
        return t;
    }

    /** content.ts weddingGuides ×5（g-5 draft——mock.js 状态） */
    private void seedGuides() {
        seedGuide("Phase 1", "12+ months out", "Dream & Discover", 4, PublishStatus.PUBLISHED,
                "Set your vision, budget, and date. Tasks: Define your wedding vibe & venue type; "
                        + "Set your dress budget; Start a moodboard; Book a Color Palette consultation.",
                "Soñar y Descubrir", "Rêver et Découvrir");
        seedGuide("Phase 2", "9-12 months out", "Find Your Gown", 4, PublishStatus.PUBLISHED,
                "The fun part — finding the one. Tasks: Browse silhouettes by venue; Order fabric swatches; "
                        + "Try styles at home; Place your gown order (allow custom time).",
                "Encuentra tu vestido", "Trouvez votre robe");
        seedGuide("Phase 3", "6-9 months out", "Style Your Party", 4, PublishStatus.PUBLISHED,
                "Dress your bridesmaids and family. Tasks: Choose your bridesmaid palette; "
                        + "Share the group link with your party; Order mother-of-the-bride dress; Select flower girl looks.",
                "Viste a tu cortejo", "Habillez votre cortège");
        seedGuide("Phase 4", "3-6 months out", "Accessorize", 4, PublishStatus.PUBLISHED,
                "Complete every look. Tasks: Choose your veil & headpiece; Pick wedding shoes; "
                        + "Add jewelry & finishing touches; Plan a second reception look.",
                "Accesorios", "Accessoirisez");
        seedGuide("Phase 5", "1-3 months out", "Final Fittings", 4, PublishStatus.DRAFT,
                "Perfect the fit. Tasks: Schedule alterations; Break in your shoes; "
                        + "Final accessory check; Confirm delivery dates.",
                null, null);
    }

    @SuppressWarnings("java:S107")
    private void seedGuide(String phase, String timeframe, String title, int tasksCount, PublishStatus status,
                           String body, String titleEs, String titleFr) {
        Guide guide = new Guide();
        guide.setPhase(phase);
        guide.setTimeframe(timeframe);
        guide.setTitle(title);
        guide.setTasksCount(tasksCount);
        guide.setStatus(status);
        guide.setBody(body);
        guideRepository.insert(guide);
        List<GuideTranslation> rows = new ArrayList<>();
        if (titleEs != null) {
            rows.add(guideTranslation("es", titleEs));
        }
        if (titleFr != null) {
            rows.add(guideTranslation("fr", titleFr));
        }
        guideRepository.replaceTranslations(guide.getId(), rows);
    }

    private GuideTranslation guideTranslation(String locale, String title) {
        GuideTranslation t = new GuideTranslation();
        t.setLocale(locale);
        t.setTitle(title);
        return t;
    }

    private List<Long> head(List<Long> ids, int n) {
        return ids.size() <= n ? ids : ids.subList(0, n);
    }

    private List<Long> slice(List<Long> ids, int from, int n) {
        if (ids.size() <= from) {
            return head(ids, Math.min(n, ids.size()));
        }
        return ids.subList(from, Math.min(from + n, ids.size()));
    }
}
