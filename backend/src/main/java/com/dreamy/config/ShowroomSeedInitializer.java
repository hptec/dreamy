package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.repository.ProductMapper;
import com.dreamy.enums.UserStatus;
import com.dreamy.enums.UserTier;
import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.user.repository.UserMapper;
import com.dreamy.enums.AssignStatus;
import com.dreamy.enums.VoteValue;
import com.dreamy.domain.member.entity.ShowroomComment;
import com.dreamy.domain.member.entity.ShowroomMember;
import com.dreamy.domain.member.repository.ShowroomCommentRepository;
import com.dreamy.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.domain.member.repository.ShowroomVoteRepository;
import com.dreamy.domain.showroom.entity.Showroom;
import com.dreamy.domain.showroom.entity.ShowroomItem;
import com.dreamy.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * showroom 域种子数据初始化（决策 21：prototype data/showrooms.ts 2 房样例 → 5 表种子行：
 * 成员四态（unassigned/assigned/ordered + email 有/无 + linked 绑定/未绑定）/款式/投票/留言变体；
 * owner_id 关联 identity 种子用户（缺则按派生 email 幂等补建——review 同惯例）、
 * product_id 关联 catalog 种子商品（slug 解析，缺则跳过该款式）；
 * invite_token 重新生成 UUID 不复用原型假值（showroom-data-detail §9 备注②）。
 * 幂等：showroom 表非空即跳过。@Order(40)：在 catalog(20)/review(30) 种子之后执行。
 */
@Component
@Order(40)
public class ShowroomSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(ShowroomSeedInitializer.class);

    private final ShowroomRepository showroomRepository;
    private final ShowroomItemRepository itemRepository;
    private final ShowroomMemberRepository memberRepository;
    private final ShowroomVoteRepository voteRepository;
    private final ShowroomCommentRepository commentRepository;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    private final Map<String, Long> productIdBySlug = new HashMap<>();
    private final Map<String, Long> userIdByName = new HashMap<>();

    public ShowroomSeedInitializer(ShowroomRepository showroomRepository,
                                   ShowroomItemRepository itemRepository,
                                   ShowroomMemberRepository memberRepository,
                                   ShowroomVoteRepository voteRepository,
                                   ShowroomCommentRepository commentRepository,
                                   ProductMapper productMapper, UserMapper userMapper) {
        this.showroomRepository = showroomRepository;
        this.itemRepository = itemRepository;
        this.memberRepository = memberRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        // 幂等：showroom 表非空即跳过（决策 21）
        if (showroomRepository.countAll() > 0) {
            return;
        }
        seedSarah();
        seedAmelia();
        log.info("[ShowroomSeed] showroom 种子数据初始化完成");
    }

    // ==================== sr-sarah："Sarah's Bridal Party" ====================

    private void seedSarah() {
        Long meadow = productIdBySlug("meadow-bridesmaid");
        Long seabreeze = productIdBySlug("seabreeze-bridesmaid");
        Long petal = productIdBySlug("petal-bridesmaid");
        if (meadow == null || seabreeze == null || petal == null) {
            log.warn("[ShowroomSeed] catalog 种子商品未就绪，跳过 Sarah's Bridal Party");
            return;
        }
        Long ownerId = ensureUser("Sarah");
        Showroom room = insertShowroom(ownerId, "Sarah's Bridal Party", LocalDate.parse("2026-09-19"));

        // 款式（last_ordered_at：Emma 已购 meadow → 窗口内变体，dye lot 演示）
        Long itemMeadow = insertItem(room.getId(), meadow, "Sage", LocalDateTime.now().minusHours(2));
        Long itemSeabreeze = insertItem(room.getId(), seabreeze, "Blush", null);
        Long itemPetal = insertItem(room.getId(), petal, "Coral", null);

        // 成员四态变体：bride(owner 绑定)/ordered+email+绑定/assigned+email/assigned 无 email/unassigned
        Long mSarah = insertMember(room.getId(), "Sarah", null, null,
                AssignStatus.UNASSIGNED, ownerId);
        Long mEmma = insertMember(room.getId(), "Emma", "emma@seed.dreamy.com", itemMeadow,
                AssignStatus.ORDERED, ensureUser("Emma"));
        Long mOlivia = insertMember(room.getId(), "Olivia", "olivia@seed.dreamy.com", itemMeadow,
                AssignStatus.ASSIGNED, null);
        Long mMia = insertMember(room.getId(), "Mia", null, itemSeabreeze,
                AssignStatus.ASSIGNED, null);
        Long mChloe = insertMember(room.getId(), "Chloe", null, null,
                AssignStatus.UNASSIGNED, null);

        // 投票（prototype upVotes/downVotes 全量）
        vote(itemMeadow, VoteValue.LIKE, mSarah, mEmma, mOlivia, mMia);
        vote(itemSeabreeze, VoteValue.LIKE, mEmma, mMia, mChloe);
        vote(itemPetal, VoteValue.LIKE, mChloe);
        vote(itemPetal, VoteValue.DISLIKE, mEmma, mOlivia);

        // 留言（prototype comments 全量）
        comment(itemMeadow, mEmma, "Obsessed with this sage shade — it photographs beautifully outdoors.",
                "2026-06-02");
        comment(itemMeadow, mOlivia, "The chiffon is so flowy, and it has pockets-level comfort. Yes from me!",
                "2026-06-03");
        comment(itemSeabreeze, mMia, "One-shoulder is so flattering, and I would actually re-wear this.",
                "2026-06-01");
        comment(itemPetal, mChloe, "I love the coral for a September garden wedding, but happy either way!",
                "2026-06-04");
    }

    // ==================== sr-amelia："Amelia's Vineyard Weekend" ====================

    private void seedAmelia() {
        Long juliet = productIdBySlug("juliet-lace-gown");
        Long aria = productIdBySlug("aria-prom-dress");
        if (juliet == null || aria == null) {
            log.warn("[ShowroomSeed] catalog 种子商品未就绪，跳过 Amelia's Vineyard Weekend");
            return;
        }
        Long ownerId = ensureUser("Amelia");
        Showroom room = insertShowroom(ownerId, "Amelia's Vineyard Weekend", LocalDate.parse("2026-11-07"));

        Long itemJuliet = insertItem(room.getId(), juliet, "Champagne", null);
        Long itemAria = insertItem(room.getId(), aria, "Sage", null);

        Long mAmelia = insertMember(room.getId(), "Amelia", null, null, AssignStatus.UNASSIGNED, ownerId);
        Long mGrace = insertMember(room.getId(), "Grace", "grace@seed.dreamy.com", itemJuliet,
                AssignStatus.ASSIGNED, null);
        Long mLily = insertMember(room.getId(), "Lily", null, null, AssignStatus.UNASSIGNED, null);

        vote(itemJuliet, VoteValue.LIKE, mAmelia, mGrace);
        vote(itemAria, VoteValue.LIKE, mLily);

        comment(itemJuliet, mGrace, "Champagne lace against vineyard greens — dreamy.", "2026-06-07");
    }

    // ==================== 写入 ====================

    private Showroom insertShowroom(Long ownerId, String name, LocalDate weddingDate) {
        Showroom showroom = new Showroom();
        showroom.setOwnerId(ownerId);
        showroom.setName(name);
        showroom.setWeddingDate(weddingDate);
        showroom.setInviteToken(UUID.randomUUID().toString());
        showroom.setInviteVersion(1);
        showroomRepository.insert(showroom);
        return showroom;
    }

    private Long insertItem(Long showroomId, Long productId, String color, LocalDateTime lastOrderedAt) {
        ShowroomItem item = new ShowroomItem();
        item.setShowroomId(showroomId);
        item.setProductId(productId);
        item.setColor(color == null ? "" : color);
        item.setLastOrderedAt(lastOrderedAt);
        itemRepository.insert(item);
        return item.getId();
    }

    private Long insertMember(Long showroomId, String nickname, String email, Long assignedItemId,
                              AssignStatus status, Long linkedCustomerId) {
        ShowroomMember member = new ShowroomMember();
        member.setShowroomId(showroomId);
        member.setNickname(nickname);
        member.setEmail(email);
        member.setAssignedItemId(assignedItemId);
        member.setAssignStatus(status);
        member.setLinkedCustomerId(linkedCustomerId);
        memberRepository.insert(member);
        return member.getId();
    }

    private void vote(Long itemId, VoteValue value, Long... memberIds) {
        for (Long memberId : memberIds) {
            voteRepository.upsert(itemId, memberId, value);
        }
    }

    private void comment(Long itemId, Long memberId, String content, String date) {
        ShowroomComment row = new ShowroomComment();
        row.setShowroomItemId(itemId);
        row.setMemberId(memberId);
        row.setContent(content);
        row.setCreatedAt(LocalDate.parse(date).atTime(10, 0));
        commentRepository.insert(row);
    }

    private Long productIdBySlug(String slug) {
        return productIdBySlug.computeIfAbsent(slug, s -> {
            Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>().eq(Product::getSlug, s));
            return product == null ? null : product.getId();
        });
    }

    /** 种子用户（按派生 email 幂等；review ensureUser 同惯例） */
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
            user.setJoinedAt(LocalDateTime.parse("2026-05-01T10:00"));
            user.setAnonymized(false);
            user.setVersion(0);
            userMapper.insert(user);
            return user.getId();
        });
    }
}
