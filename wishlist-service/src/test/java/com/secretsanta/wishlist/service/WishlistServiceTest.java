package com.secretsanta.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.secretsanta.wishlist.dto.CreateWishlistItemRequest;
import com.secretsanta.wishlist.dto.ReceiverWishlistResponse;
import com.secretsanta.wishlist.dto.WishlistItemResponse;
import com.secretsanta.wishlist.entity.DrawAssignmentProjection;
import com.secretsanta.wishlist.entity.GroupProjection;
import com.secretsanta.wishlist.entity.WishlistItem;
import com.secretsanta.wishlist.exception.WishlistException;
import com.secretsanta.wishlist.repository.DrawAssignmentProjectionRepository;
import com.secretsanta.wishlist.repository.GroupParticipantProjectionRepository;
import com.secretsanta.wishlist.repository.GroupProjectionRepository;
import com.secretsanta.wishlist.repository.WishlistItemRepository;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String GIVER_ID = "giver-001";
    private static final String RECEIVER_ID = "receiver-001";

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private GroupProjectionRepository groupRepository;

    @Mock
    private GroupParticipantProjectionRepository participantRepository;

    @Mock
    private DrawAssignmentProjectionRepository assignmentRepository;

    @Captor
    private ArgumentCaptor<WishlistItem> wishlistItemCaptor;

    private WishlistService wishlistService;

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistService(
                wishlistItemRepository,
                groupRepository,
                participantRepository,
                assignmentRepository
        );
    }

    @Test
    void addItem_saves_item_for_current_user_when_member() {
        givenGroup(false);
        when(participantRepository.existsByGroupIdAndUserId(GROUP_ID, GIVER_ID)).thenReturn(true);
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        wishlistService.addItem(
                GROUP_ID,
                GIVER_ID,
                new CreateWishlistItemRequest("  Headphones  ", "Noise cancelling", " https://example.com ")
        );

        verify(wishlistItemRepository).save(wishlistItemCaptor.capture());
        WishlistItem savedItem = wishlistItemCaptor.getValue();
        assertThat(savedItem.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(savedItem.getOwnerUserId()).isEqualTo(GIVER_ID);
        assertThat(savedItem.getTitle()).isEqualTo("Headphones");
        assertThat(savedItem.getUrl()).isEqualTo("https://example.com");
    }

    @Test
    void addItem_fails_when_user_is_not_group_member() {
        givenGroup(false);
        when(participantRepository.existsByGroupIdAndUserId(GROUP_ID, GIVER_ID)).thenReturn(false);

        assertThatThrownBy(() -> wishlistService.addItem(
                GROUP_ID,
                GIVER_ID,
                new CreateWishlistItemRequest("Book", null, null)
        ))
                .isInstanceOfSatisfying(WishlistException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getErrorCode()).isEqualTo("WISHLIST_FORBIDDEN");
                });

        verify(wishlistItemRepository, never()).save(any());
    }

    @Test
    void receiverWishlist_is_blocked_before_draw() {
        givenGroup(false);
        when(participantRepository.existsByGroupIdAndUserId(GROUP_ID, GIVER_ID)).thenReturn(true);

        assertThatThrownBy(() -> wishlistService.getReceiverWishlist(GROUP_ID, GIVER_ID))
                .isInstanceOfSatisfying(WishlistException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getErrorCode()).isEqualTo("WISHLIST_DRAW_NOT_COMPLETED");
                });

        verify(assignmentRepository, never()).findByGroupIdAndGiverId(any(), any());
    }

    @Test
    void receiverWishlist_returns_only_current_users_receiverItems_afterDraw() {
        givenGroup(true);
        when(participantRepository.existsByGroupIdAndUserId(GROUP_ID, GIVER_ID)).thenReturn(true);
        when(assignmentRepository.findByGroupIdAndGiverId(GROUP_ID, GIVER_ID))
                .thenReturn(Optional.of(assignment(false)));
        when(wishlistItemRepository.findByGroupIdAndOwnerUserIdOrderByCreatedAtAsc(GROUP_ID, RECEIVER_ID))
                .thenReturn(List.of(wishlistItem(RECEIVER_ID, "Coffee grinder")));

        ReceiverWishlistResponse response = wishlistService.getReceiverWishlist(GROUP_ID, GIVER_ID);

        assertThat(response.receiverId()).isEqualTo(RECEIVER_ID);
        assertThat(response.items())
                .extracting(WishlistItemResponse::title)
                .containsExactly("Coffee grinder");
        verify(wishlistItemRepository)
                .findByGroupIdAndOwnerUserIdOrderByCreatedAtAsc(GROUP_ID, RECEIVER_ID);
    }

    @Test
    void setGiftPurchased_updates_only_currentUsersAssignment() {
        givenGroup(true);
        when(participantRepository.existsByGroupIdAndUserId(GROUP_ID, GIVER_ID)).thenReturn(true);
        when(assignmentRepository.findByGroupIdAndGiverId(GROUP_ID, GIVER_ID))
                .thenReturn(Optional.of(assignment(false)));
        when(assignmentRepository.save(any(DrawAssignmentProjection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = wishlistService.setGiftPurchased(GROUP_ID, GIVER_ID, true);

        assertThat(response.giftPurchased()).isTrue();
        assertThat(response.purchasedAt()).isNotNull();
        verify(assignmentRepository).findByGroupIdAndGiverId(GROUP_ID, GIVER_ID);
    }

    private void givenGroup(boolean drawn) {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(GroupProjection.builder()
                .groupId(GROUP_ID)
                .drawn(drawn)
                .build()));
    }

    private DrawAssignmentProjection assignment(boolean giftPurchased) {
        return DrawAssignmentProjection.builder()
                .groupId(GROUP_ID)
                .giverId(GIVER_ID)
                .receiverId(RECEIVER_ID)
                .receiverName("Receiver")
                .giftPurchased(giftPurchased)
                .build();
    }

    private WishlistItem wishlistItem(String ownerUserId, String title) {
        Instant now = Instant.now();
        return WishlistItem.builder()
                .id(UUID.randomUUID())
                .groupId(GROUP_ID)
                .ownerUserId(ownerUserId)
                .title(title)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
