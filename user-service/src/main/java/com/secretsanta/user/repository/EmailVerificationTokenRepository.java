package com.secretsanta.user.repository;

import com.secretsanta.user.entity.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailVerificationToken token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
            update EmailVerificationToken token
            set token.invalidatedAt = :now
            where token.user.id = :userId
              and token.usedAt is null
              and token.invalidatedAt is null
            """)
    int invalidateActiveForUser(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );
}
