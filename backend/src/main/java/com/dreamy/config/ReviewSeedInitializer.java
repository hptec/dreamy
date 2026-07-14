package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.repository.ProductMapper;
import com.dreamy.enums.UserStatus;
import com.dreamy.enums.UserTier;
import com.dreamy.domain.role.entity.Permission;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.role.entity.RolePermission;
import com.dreamy.domain.role.repository.PermissionMapper;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.repository.RolePermissionMapper;
import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.user.repository.UserMapper;
import com.dreamy.enums.QuestionVisibility;
import com.dreamy.enums.ReviewStatus;
import com.dreamy.domain.question.entity.ProductQuestion;
import com.dreamy.domain.question.repository.ProductQuestionRepository;
import com.dreamy.domain.review.entity.Review;
import com.dreamy.domain.review.entity.ReviewImage;
import com.dreamy.domain.review.repository.ReviewImageRepository;
import com.dreamy.domain.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * review 域种子数据初始化（决策 21：portal-admin 原型 mock.js reviews/productQuestions 提炼为
 * 3 表种子行——评价含三态/精选/带图/带回复变体、提问含已答/未答/hidden/visible-未答四象限变体；
 * user_id 关联 identity 种子用户（缺则按 email 幂等补建）、product_id 关联 catalog 种子商品（slug 解析）；
 * submitted_at/asked_at 保留 mock 相对时间）。幂等：review/product_question 表非空即跳过。
 * 同时登记 RBAC 权限点 /reviews（BE-DIM-6 本域新增权限点，绑定超管角色——catalog /attribute-sets 同惯例）。
 * @Order(30)：在 catalog 种子（@Order(20)）之后执行，商品 slug 可解析。
 */
@Component
@Order(30)
@ConditionalOnProperty(prefix = "dreamy.seed", name = "demo-enabled", havingValue = "true")
public class ReviewSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(ReviewSeedInitializer.class);
    private static final String REF = "/competitor-refs";
    private static final DateTimeFormatter MOCK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String REPLY_AUTHOR = "Dreamy Team";

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository imageRepository;
    private final ProductQuestionRepository questionRepository;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    private final Map<String, Long> productIdBySlug = new HashMap<>();
    private final Map<String, Long> userIdByName = new HashMap<>();

    public ReviewSeedInitializer(ReviewRepository reviewRepository, ReviewImageRepository imageRepository,
                                 ProductQuestionRepository questionRepository, ProductMapper productMapper,
                                 UserMapper userMapper, PermissionMapper permissionMapper,
                                 RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper) {
        this.reviewRepository = reviewRepository;
        this.imageRepository = imageRepository;
        this.questionRepository = questionRepository;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        ensureReviewsPermission();
        if (reviewRepository.countAll() > 0 || questionRepository.countAll() > 0) {
            return;
        }
        seedReviews();
        seedQuestions();
        log.info("[ReviewSeed] review 种子数据初始化完成");
    }

    /** RBAC 权限点 /reviews（幂等按 perm_code）+ 绑定超管角色（catalog ensureAttributeSetsPermission 同惯例） */
    private void ensureReviewsPermission() {
        Permission permission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermCode, "/reviews"));
        if (permission == null) {
            permission = new Permission();
            permission.setPermCode("/reviews");
            permission.setGroup("内容管理");
            permission.setLabel("评价与 Q&A");
            permissionMapper.insert(permission);
            log.info("[ReviewSeed] 权限点 /reviews 已登记");
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

    // ==================== 评价（mock.js reviews 13 行全量） ====================

    private void seedReviews() {
        // rv-2412 pending + 3 图
        insertReview("aurelia-gown", "Emma Johnson", 5, "2026-06-09 21:14",
                "Wore this for our cliffside ceremony in Big Sur and the tulle caught the golden-hour light "
                        + "beautifully. The bodice support held up through six hours of dancing — zero adjustments "
                        + "needed. True to size, order your usual.",
                ReviewStatus.PENDING, false, null, null,
                List.of(img("/kissprom/wedding-aline-tulle-01.jpg", false),
                        img("/davidsbridal/wedding-dress-04.jpg", false),
                        img("/kissprom/wedding-aline-lace-02.jpg", false)));
        // rv-2411 pending 无图
        insertReview("meadow-bridesmaid", "Sofia Marco", 4, "2026-06-09 16:40",
                "Ordered six of these in Sage for my bridal party. The chiffon photographs like a dream outdoors. "
                        + "One dress ran slightly long but free swatches beforehand made color matching painless.",
                ReviewStatus.PENDING, false, null, null, List.of());
        // rv-2410 approved + featured + reply + 2 图
        insertReview("celeste-lace-gown", "Ava Chen", 5, "2026-06-08 19:25",
                "The lace detail is even more delicate in person. I had it hemmed locally and the seamstress kept "
                        + "complimenting the construction. Got married in a vineyard at sunset — the champagne "
                        + "undertone glowed.",
                ReviewStatus.APPROVED, true,
                "Thank you Ava! Your vineyard photos are stunning — we are so honored to be part of your golden "
                        + "hour. Congratulations from all of us at Dreamy!", "2026-06-09 10:20",
                List.of(img("/kissprom/wedding-aline-lace-02.jpg", false),
                        img("/kissprom/prom-champagne-lace-05.jpg", false)));
        // rv-2409 approved + reply
        insertReview("luna-homecoming-dress", "Olivia Brown", 3, "2026-06-08 14:02",
                "Pretty dress but the champagne color is warmer than the product photos suggest. Shipping to "
                        + "Australia took 9 days. Fits well otherwise.",
                ReviewStatus.APPROVED, false,
                "Thanks for the honest feedback, Olivia. We have flagged the color calibration to our studio team, "
                        + "and free fabric swatches are always available so you can confirm tones before ordering.",
                "2026-06-10 09:55", List.of());
        // rv-2408 rejected（spam）+ 1 图
        insertReview("marina-mermaid-gown", "Mia Wilson", 2, "2026-06-07 22:18",
                "Check out my discount dress store at cheap-gowns.example.com — same look for half the price!!!",
                ReviewStatus.REJECTED, false, null, null,
                List.of(img("/kissprom/wedding-mermaid-chiffon-03.jpg", false)));
        // rv-2407 approved + 2 图
        insertReview("petal-bridesmaid", "Charlotte Lee", 5, "2026-06-07 11:36",
                "Third time ordering from Dreamy and the quality is so consistent. The blush shade worked perfectly "
                        + "for our garden brunch wedding. My bridesmaids want to wear them again.",
                ReviewStatus.APPROVED, false, null, null,
                List.of(img("/birdygrey/bridesmaid-pink-bryten-02.jpg", false),
                        img("/birdygrey/bridesmaid-pink-bella-01.jpg", false)));
        // rv-2406 approved + featured + reply + 含 rejected=true 图（review_image_visibility 变体）
        insertReview("aurelia-gown", "Amelia Davis", 4, "2026-06-06 20:44",
                "Gorgeous gown, arrived two days early. Took one star off because the detachable train clasp felt "
                        + "flimsy — ended up safety-pinning it for the reception.",
                ReviewStatus.APPROVED, true,
                "Thank you Amelia! Our product team has upgraded the train clasp hardware for the next production "
                        + "run based on your note — please reach out to support for a complimentary replacement clip.",
                "2026-06-07 09:12",
                List.of(img("/kissprom/wedding-aline-tulle-01.jpg", false),
                        img("/birdygrey/accessory-jewelry-01.jpg", true)));
        // rv-2405 approved 无图
        insertReview("cathedral-veil", "Isabella Garcia", 5, "2026-06-05 15:28",
                "The cathedral length is dramatic without being heavy. Floated beautifully in every beach photo. "
                        + "Comb stayed secure in fine hair all day.",
                ReviewStatus.APPROVED, false, null, null, List.of());
        // rv-2404 pending + 2 图
        insertReview("willow-longsleeve-gown", "Grace Kim", 1, "2026-06-05 09:50",
                "Dress arrived with a pulled thread on the left sleeve. Very disappointed for the price point. "
                        + "Waiting to hear back from support before deciding on a return.",
                ReviewStatus.PENDING, false, null, null,
                List.of(img("/kissprom/wedding-aline-longsleeve-06.jpg", false),
                        img("/davidsbridal/bridesmaid-sage-01.jpg", false)));
        // rv-2403 rejected（spam）
        insertReview("meadow-bridesmaid", "Lily Park", 4, "2026-06-04 18:09",
                "Lovely fabric! BTW everyone use code FAKE50 on my site for 50% off wedding decor, link in my "
                        + "profile.",
                ReviewStatus.REJECTED, false, null, null, List.of());
        // rv-2402 approved + reply + 1 图
        insertReview("drop-earrings", "Zoe Adams", 5, "2026-06-04 10:31",
                "These earrings caught the sunset light during our first dance and the photographer would not stop "
                        + "raving. Lightweight enough to forget you are wearing them.",
                ReviewStatus.APPROVED, false,
                "Zoe, this made our week! Golden hour and crystal drops are a match made in heaven. Wishing you a "
                        + "lifetime of first dances.", "2026-06-04 16:45",
                List.of(img("/birdygrey/accessory-jewelry-01.jpg", false)));
        // rv-2401 pending + 1 图
        insertReview("coraline-beach-gown", "Hannah Moore", 3, "2026-06-03 21:57",
                "Cute short dress for an elopement but the lining wrinkles easily when packed. Steam it on arrival "
                        + "and you are fine.",
                ReviewStatus.PENDING, false, null, null,
                List.of(img("/kissprom/wedding-beach-short-05.jpg", false)));
        // rv-2400 approved 无图
        insertReview("luna-homecoming-dress", "Chloe Bennett", 5, "2026-06-03 13:12",
                "Prom night perfection. The lace overlay is intricate and the champagne tone flattered every skin "
                        + "tone in our group photos.",
                ReviewStatus.APPROVED, false, null, null, List.of());
    }

    // ==================== Q&A（mock.js productQuestions 8 行全量——四象限变体） ====================

    private void seedQuestions() {
        // qa-508 unanswered + visible（CV-REV-009 双条件过滤变体：visible 未答不出前台）
        insertQuestion("aurelia-gown", "Sophie R.", "2026-06-09 20:18",
                "Does the tulle skirt hold up in a windy beach ceremony, or will it fly around too much for photos?",
                null, null, QuestionVisibility.VISIBLE);
        // qa-507 answered + visible
        insertQuestion("meadow-bridesmaid", "Sofia Marco", "2026-06-09 15:02",
                "Can I order fabric swatches of the Sage color before committing to six bridesmaid dresses?",
                "Absolutely! Free swatches of every colorway ship within 2 business days — just add them from the "
                        + "product page under \"Order a swatch\". We recommend confirming tones under outdoor light.",
                "2026-06-09 17:40", QuestionVisibility.VISIBLE);
        // qa-506 answered + visible
        insertQuestion("celeste-lace-gown", "Ava Chen", "2026-06-08 11:47",
                "What is the exact fabric composition of the lace overlay? I have a mild polyester sensitivity.",
                "The overlay is 70% cotton Chantilly-style lace with 30% nylon mesh backing; the lining is 100% "
                        + "breathable viscose. Full composition is listed under the Fabric tab as well.",
                "2026-06-08 14:30", QuestionVisibility.VISIBLE);
        // qa-505 answered + hidden（后台手动隐藏变体）
        insertQuestion("cathedral-veil", "Megan T.", "2026-06-07 19:25",
                "How long is the cathedral veil exactly? Trying to match it to a chapel train.",
                "It measures 108 inches (274 cm) from comb to hem — designed to extend roughly 20 inches past a "
                        + "chapel train.",
                "2026-06-08 09:15", QuestionVisibility.HIDDEN);
        // qa-504 unanswered + visible
        insertQuestion("luna-homecoming-dress", "Priya K.", "2026-06-07 10:08",
                "Is the champagne color closer to gold or to nude in person? Photos online vary a lot.",
                null, null, QuestionVisibility.VISIBLE);
        // qa-503 answered + visible
        insertQuestion("marina-mermaid-gown", "Rachel W.", "2026-06-06 16:53",
                "Can the mermaid silhouette be let out around the hips by a local tailor, or is there no seam "
                        + "allowance?",
                "Yes — we include a generous 2-inch seam allowance at the hip and waist seams specifically for "
                        + "local alterations. Most tailors can let it out by up to one full size.",
                "2026-06-06 18:20", QuestionVisibility.VISIBLE);
        // qa-502 unanswered + visible
        insertQuestion("willow-longsleeve-gown", "Elena V.", "2026-06-05 21:34",
                "Are the long sleeves stretchy enough to move comfortably? Worried about raising my arms for the "
                        + "bouquet toss.",
                null, null, QuestionVisibility.VISIBLE);
        // qa-501 answered + visible
        insertQuestion("drop-earrings", "Dana L.", "2026-06-05 09:12",
                "Are these earrings hypoallergenic? My ears react to most costume jewelry.",
                "Yes — the posts are surgical-grade 316L stainless steel with a nickel-free plating, suitable for "
                        + "sensitive ears.",
                "2026-06-05 11:48", QuestionVisibility.VISIBLE);
    }

    // ==================== 写入 ====================

    private record SeedImage(String url, boolean rejected) {
    }

    private SeedImage img(String refPath, boolean rejected) {
        return new SeedImage(REF + refPath, rejected);
    }

    private void insertReview(String slug, String customer, int rating, String submittedAt, String content,
                              ReviewStatus status, boolean featured, String reply, String replyTime,
                              List<SeedImage> images) {
        Long productId = productIdBySlug(slug);
        if (productId == null) {
            log.warn("[ReviewSeed] 商品 slug={} 未找到（catalog 种子未就绪？），跳过评价 {}", slug, customer);
            return;
        }
        Review review = new Review();
        review.setProductId(productId);
        review.setUserId(ensureUser(customer));
        review.setCustomerName(customer);
        review.setRating(rating);
        review.setContent(content);
        review.setStatus(status);
        // featured 不变量（CV-REV-007）：仅 approved 可为 1
        review.setFeatured(featured && status == ReviewStatus.APPROVED);
        review.setSubmittedAt(LocalDateTime.parse(submittedAt, MOCK_TIME));
        if (reply != null) {
            review.setReplyAuthor(REPLY_AUTHOR);
            review.setReplyContent(reply);
            review.setReplyTime(LocalDateTime.parse(replyTime, MOCK_TIME));
        }
        reviewRepository.insert(review);
        for (SeedImage seedImage : images) {
            ReviewImage image = new ReviewImage();
            image.setReviewId(review.getId());
            image.setUrl(seedImage.url());
            image.setRejected(seedImage.rejected());
            imageRepository.batchInsert(List.of(image));
        }
    }

    private void insertQuestion(String slug, String asker, String askedAt, String questionText,
                                String answer, String answerTime, QuestionVisibility visible) {
        Long productId = productIdBySlug(slug);
        if (productId == null) {
            log.warn("[ReviewSeed] 商品 slug={} 未找到，跳过提问 {}", slug, asker);
            return;
        }
        ProductQuestion question = new ProductQuestion();
        question.setProductId(productId);
        question.setUserId(ensureUser(asker));
        question.setAsker(asker);
        question.setQuestion(questionText);
        question.setAskedAt(LocalDateTime.parse(askedAt, MOCK_TIME));
        if (answer != null) {
            question.setAnswer(answer);
            question.setAnswerTime(LocalDateTime.parse(answerTime, MOCK_TIME));
        }
        question.setVisible(visible);
        questionRepository.insert(question);
    }

    private Long productIdBySlug(String slug) {
        return productIdBySlug.computeIfAbsent(slug, s -> {
            Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>().eq(Product::getSlug, s));
            return product == null ? null : product.getId();
        });
    }

    /** 种子用户（按派生 email 幂等；identity 无消费端用户种子，本域补建以满足 user_id 逻辑外键） */
    private Long ensureUser(String fullName) {
        return userIdByName.computeIfAbsent(fullName, name -> {
            String email = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".")
                    .replaceAll("^\\.|\\.$", "") + "@seed.dreamy.com";
            User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
            if (existing != null) {
                return existing.getId();
            }
            User user = new User();
            user.setEmail(email);
            user.setEmailVerified(true);
            user.setName(name);
            user.setTier(UserTier.REGULAR);
            user.setStatus(UserStatus.ACTIVE);
            user.setJoinedAt(LocalDateTime.parse("2026-05-01 10:00", MOCK_TIME));
            user.setAnonymized(false);
            user.setVersion(0);
            userMapper.insert(user);
            return user.getId();
        });
    }
}
