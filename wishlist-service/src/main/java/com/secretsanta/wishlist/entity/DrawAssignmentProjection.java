package com.secretsanta.wishlist.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "wishlist_draw_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wishlist_assignment_giver",
                columnNames = {"group_id", "giver_user_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawAssignmentProjection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "giver_user_id", nullable = false)
    private String giverId;

    @Column(name = "giver_name")
    private String giverName;

    @Column(name = "receiver_user_id", nullable = false)
    private String receiverId;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "gift_purchased", nullable = false)
    private boolean giftPurchased;

    @Column(name = "purchased_at")
    private Instant purchasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
