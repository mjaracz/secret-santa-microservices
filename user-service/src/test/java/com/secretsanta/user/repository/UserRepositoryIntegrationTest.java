package com.secretsanta.user.repository;

import com.secretsanta.common.user.UserAccountStatus;
import com.secretsanta.common.user.UserRole;
import com.secretsanta.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class UserRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:15-alpine")
                    .withDatabaseName("user_test_db")
                    .withUsername("test_user")
                    .withPassword("test_password");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void startsDatabaseWithFlywayMigrations() {
        Long successfulMigrations = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = true
                """,
                Long.class
        );

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(3L);
    }

    @Test
    void savesPendingUserWithInitialVersion() {
        User savedUser = userRepository.saveAndFlush(
                buildUser(
                        "User@example.com",
                        "user@example.com"
                )
        );

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getStatus())
                .isEqualTo(UserAccountStatus.PENDING_VERIFICATION);
        assertThat(savedUser.getVersion()).isZero();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        userRepository.saveAndFlush(
                buildUser(
                        "First@example.com",
                        "duplicate@example.com"
                )
        );

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(
                        buildUser(
                                "Second@example.com",
                                "duplicate@example.com"
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void treatsEmailCaseVariantsAsDuplicates() {
        userRepository.saveAndFlush(
                buildUser(
                        "User@Example.com",
                        "user@example.com"
                )
        );

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(
                        buildUser(
                                "user@example.com",
                                "user@example.com"
                        )
                )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User buildUser(
            String email,
            String normalizedEmail
    ) {
        return User.builder()
                .email(email)
                .emailNormalized(normalizedEmail)
                .name("New User")
                .passwordHash("bcrypt-hash")
                .status(UserAccountStatus.PENDING_VERIFICATION)
                .emailVerifiedAt(null)
                .build();
    }
}
