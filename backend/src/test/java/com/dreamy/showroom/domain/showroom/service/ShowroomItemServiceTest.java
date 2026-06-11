package com.dreamy.showroom.domain.showroom.service;

import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.showroom.domain.member.repository.ShowroomCommentRepository;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.showroom.domain.member.repository.ShowroomVoteRepository;
import com.dreamy.showroom.domain.showroom.entity.Showroom;
import com.dreamy.showroom.domain.showroom.entity.ShowroomItem;
import com.dreamy.showroom.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.showroom.domain.showroom.repository.ShowroomRepository;
import com.dreamy.showroom.dto.ShowroomDtos.ItemCreate;
import com.dreamy.showroom.dto.ShowroomDtos.ShowroomItemDto;
import com.dreamy.showroom.error.ShowroomErrorCode;
import com.dreamy.showroom.error.ShowroomException;
import com.dreamy.showroom.port.CatalogSnapshotPort;
import com.dreamy.showroom.port.CatalogSnapshotPort.ProductCardBrief;
import com.dreamy.showroom.testsupport.ImmediateShowroomTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * E-SHR-08/09 款式增删单元测试。
 * L2 TRACE: TC-SHR-016 单测面（uk 冲突 → 409102；color 归一化空串出参省略）/
 * TC-SHR-024 单测面（product 不存在/draft → 404501 透传，bs-722）/
 * TC-SHR-017 单测面（移除级联：成员回退 + ordered 仅清引用 + votes/comments 全删，调用序断言）。
 */
@ExtendWith(MockitoExtension.class)
class ShowroomItemServiceTest {

    private static final long ROOM = 8L;
    private static final long OWNER = 1L;
    private static final long PRODUCT = 11L;
    private static final long ITEM = 21L;

    @Mock
    ShowroomRepository showroomRepository;
    @Mock
    ShowroomItemRepository itemRepository;
    @Mock
    ShowroomMemberRepository memberRepository;
    @Mock
    ShowroomVoteRepository voteRepository;
    @Mock
    ShowroomCommentRepository commentRepository;
    @Mock
    CatalogSnapshotPort catalogPort;
    @Mock
    DyeLotService dyeLotService;

    ShowroomItemService service;

    @BeforeEach
    void setUp() {
        service = new ShowroomItemService(showroomRepository, itemRepository, memberRepository,
                voteRepository, commentRepository, catalogPort, dyeLotService,
                new ImmediateShowroomTxRunner());
        Showroom room = new Showroom();
        room.setId(ROOM);
        room.setOwnerId(OWNER);
        lenient().when(showroomRepository.findByIdAndOwner(ROOM, OWNER)).thenReturn(room);
    }

    private void stubPublishedProduct() {
        lenient().when(catalogPort.getProductCards(anyCollection(), anyString()))
                .thenReturn(Map.of(PRODUCT, new ProductCardBrief(PRODUCT, "meadow-bridesmaid",
                        "Meadow", new BigDecimal("158"), "/img.jpg", true, 7, true)));
    }

    @Test
    @DisplayName("添加成功：color 未选归一化空串落库、出参省略 color、计数 0、dye_lot_notice=false")
    void addWithoutColor() {
        stubPublishedProduct();
        doAnswer(invocation -> {
            ShowroomItem row = invocation.getArgument(0);
            row.setId(ITEM);
            return null;
        }).when(itemRepository).insert(any(ShowroomItem.class));
        when(dyeLotService.isWithinWindow(null)).thenReturn(false);

        ShowroomItemDto dto = service.add(OWNER, ROOM, new ItemCreate(PRODUCT, "   "));
        assertThat(dto.color()).isNull();
        assertThat(dto.likeCount()).isZero();
        assertThat(dto.dislikeCount()).isZero();
        assertThat(dto.myVote()).isNull();
        assertThat(dto.comments()).isEmpty();
        assertThat(dto.dyeLotNotice()).isFalse();
    }

    @Test
    @DisplayName("同房同 product+color 二次添加：uk 冲突 → 409102 ITEM_ALREADY_EXISTS")
    void duplicateItemConflict() {
        stubPublishedProduct();
        doThrow(new DuplicateKeyException("uk_si_room_product_color"))
                .when(itemRepository).insert(any(ShowroomItem.class));

        assertThatThrownBy(() -> service.add(OWNER, ROOM, new ItemCreate(PRODUCT, "Sage")))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.ITEM_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("商品不存在/未发布 → 404501 透传 catalog（bs-722）")
    void productNotPublishedPassthrough() {
        when(catalogPort.getProductCards(anyCollection(), anyString())).thenReturn(Map.of());
        assertThatThrownBy(() -> service.add(OWNER, ROOM, new ItemCreate(PRODUCT, null)))
                .isInstanceOfSatisfying(CatalogException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(CatalogErrorCode.PRODUCT_NOT_FOUND));

        when(catalogPort.getProductCards(anyCollection(), anyString()))
                .thenReturn(Map.of(PRODUCT, new ProductCardBrief(PRODUCT, "draft", "Draft",
                        BigDecimal.ONE, null, false, 1, false)));
        assertThatThrownBy(() -> service.add(OWNER, ROOM, new ItemCreate(PRODUCT, null)))
                .isInstanceOf(CatalogException.class);
    }

    @Test
    @DisplayName("移除款式级联（TX-SHR-007）：assigned/reminded 回退 → ordered 清引用 → 删 vote/comment/item")
    void removeCascade() {
        ShowroomItem item = new ShowroomItem();
        item.setId(ITEM);
        item.setShowroomId(ROOM);
        when(itemRepository.findByIdAndShowroom(ITEM, ROOM)).thenReturn(item);

        service.remove(OWNER, ROOM, ITEM);

        InOrder order = Mockito.inOrder(memberRepository, voteRepository, commentRepository, itemRepository);
        order.verify(memberRepository).unassignByItem(ITEM);
        order.verify(memberRepository).clearAssignedItemKeepOrdered(ITEM);
        order.verify(voteRepository).deleteByItems(List.of(ITEM));
        order.verify(commentRepository).deleteByItems(List.of(ITEM));
        order.verify(itemRepository).deleteById(ITEM);
    }

    @Test
    @DisplayName("item 不存在/跨房 → 404102；showroom 跨用户 → 404101")
    void removeGuards() {
        when(itemRepository.findByIdAndShowroom(999L, ROOM)).thenReturn(null);
        assertThatThrownBy(() -> service.remove(OWNER, ROOM, 999L))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.SHOWROOM_ITEM_NOT_FOUND));

        when(showroomRepository.findByIdAndOwner(ROOM, 666L)).thenReturn(null);
        assertThatThrownBy(() -> service.remove(666L, ROOM, ITEM))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.SHOWROOM_NOT_FOUND));
    }
}
