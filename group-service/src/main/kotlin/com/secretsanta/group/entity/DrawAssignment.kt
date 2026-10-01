package com.secretsanta.group.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "draw_assignments")
class DrawAssignment {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "group_id", nullable = false)
    lateinit var group: Group

    @field:Column(name = "giver_id", nullable = false)
    lateinit var giverId: String

    @field:Column(name = "giver_name", nullable = false)
    lateinit var giverName: String

    @field:Column(name = "receiver_id", nullable = false)
    lateinit var receiverId: String

    @field:Column(name = "receiver_name", nullable = false)
    lateinit var receiverName: String

    @field:CreationTimestamp
    @field:Column(name = "drawn_at", nullable = false)
    var drawnAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        return id != null && id == (other as DrawAssignment).id
    }

    override fun hashCode(): Int = Hibernate.getClass(this).hashCode()
}
