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
@Table(name = "group_members")
class GroupMember {
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "group_id", nullable = false)
    lateinit var group: Group

    @field:Column(name = "user_id", nullable = false)
    lateinit var userId: String

    @field:Column(name = "user_email")
    var userEmail: String? = null

    @field:Column(name = "user_name", nullable = false)
    lateinit var userName: String

    @field:Column(nullable = false)
    lateinit var role: String

    @field:CreationTimestamp
    @field:Column(name = "joined_at", nullable = false)
    var joinedAt: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        return id != null && id == (other as GroupMember).id
    }

    override fun hashCode(): Int = Hibernate.getClass(this).hashCode()
}
