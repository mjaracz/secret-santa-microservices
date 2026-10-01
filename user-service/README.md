# User Service (Pure Worker)

> **Part of Secret Santa Microservices Platform**
>
> *Note: In production, this would be a separate repository.*

---

## 🏗️ Architectural Role

**Type:** `Pure Event-Driven Worker`

**Responsibility:**  
Domain service responsible for user registration and credential validation. It processes Kafka commands, persists users to PostgreSQL, and publishes domain events or command replies. JWTs are issued and validated by the API Gateway.

**Key Characteristics:**
- ✅ **Pure worker** - Kafka consumer/producer only
- ✅ **Domain logic** - User registration and credential authentication
- ✅ **Database per service** - Isolated PostgreSQL instance
- ✅ **Event-driven** - Consumes commands, publishes domain events
- ❌ **No REST endpoints** - Pure asynchronous processing
- ❌ **No HTTP exposure** - Not accessible from external clients

**Architecture Pattern:**  
Event-Driven Microservices + Database per Service + Domain-Driven Design

**Communication Flow:**
```
API Gateway publishes CreateUserCommand
    ↓
Kafka (user.commands topic)
    ↓
User Service @KafkaListener
    ↓
Business Logic (validation, hashing)
    ↓
PostgreSQL (persist user)
    ↓
Kafka (user.events topic)
    ↓
Correlated UserCreatedEvent → API Gateway
```

---

## 📦 Dependencies

### Spring Boot Initializer Selection

When generating from [start.spring.io](https://start.spring.io):

**Project Metadata:**
- Spring Boot: `4.0.2`
- Java: `25`
- Group: `com.secretsanta`
- Artifact: `user-service`
- Package: `com.secretsanta.user`

**Dependencies to Add:**

| Category | Dependency Name | Identifier | Purpose |
|----------|----------------|------------|---------|
| **Messaging** | Spring Kafka | `spring-boot-starter-kafka` | Consume commands and publish events |
| **SQL** | Spring Data JPA | `spring-boot-starter-data-jpa` | ORM for PostgreSQL |
| **SQL** | PostgreSQL Driver | `postgresql` | Database connectivity |
| **Validation** | Spring Boot Validation | `spring-boot-starter-validation` | Validate registration commands |
| **Migrations** | Spring Boot Flyway | `spring-boot-starter-flyway` | Apply database schema migrations |
| **Security** | Spring Security Crypto | `spring-security-crypto` | BCrypt password hashing and matching |
| **Shared contracts** | `shareable-common` | `com.secretsanta:shareable-common` | Commands and events |
| **Shared transport** | `shareable-infrastructure` | `com.secretsanta:shareable-infrastructure` | `KafkaServiceBus` |
| **Developer Tools** | Lombok | `lombok` | Reduce boilerplate |

### Maven Dependencies (pom.xml)
```xml
<dependencies>
    <!-- Kafka command consumer and event producer -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-kafka</artifactId>
    </dependency>

    <!-- Request validation and database migrations -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-flyway</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- BCrypt password handling -->
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-crypto</artifactId>
    </dependency>

    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Contracts and shared Kafka transport -->
    <dependency>
        <groupId>com.secretsanta</groupId>
        <artifactId>shareable-common</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.secretsanta</groupId>
        <artifactId>shareable-infrastructure</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

**Critical Notes:**
- ❌ **DO NOT add `spring-boot-starter-web`** - This is a pure worker (no REST)
- ✅ **Minimal dependencies** - Only domain logic essentials

---

## 🎯 Domain Responsibility

### Implemented operations
- **User Registration** - Create accounts with normalized email uniqueness and a BCrypt password hash
- **Authentication** - Validate credentials and return an authentication reply; token issuance belongs to the API Gateway

### Business Rules
- Email must be unique (database constraint)
- Passwords hashed with BCrypt (never plaintext)
- New accounts start in `PENDING_VERIFICATION`; the current login handler only rejects accounts in `DELETED` status
- User IDs are UUIDs for global uniqueness

### Authentication request flow

The Gateway sends `AuthenticateUserCommand` on `user.commands`. Before
publishing it, the Gateway encrypts the submitted password with the User
Service's RSA public key. The worker decrypts it with its private key, finds
the user by normalized email, and compares the password with the stored BCrypt
hash. A successful request publishes `UserAuthenticatedEvent` with the user
ID and the command's `commandId` as `correlationId`. Invalid credentials
produce a generic `AUTH_INVALID_CREDENTIALS` failure, regardless of whether
the email or password was incorrect. The Gateway uses the success reply to
issue the JWT; the User Service does not create or sign tokens.

---

## 📨 Event Production

Publishes domain events and request-reply events to `user.events`:

| Event Type | Trigger | Consumed By |
|------------|---------|-------------|
| `UserCreatedEvent` | Registration completed | API Gateway (registration request-reply) |
| `UserAuthenticatedEvent` | Credentials validated | API Gateway (login request-reply) |

**Event Schema:**
```json
{
  "eventId": "<event-uuid>",
  "timestamp": 1790798400000,
  "eventType": "USER_CREATED",
  "correlationId": "<command-uuid>",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "John Doe",
  "status": "PENDING_VERIFICATION"
}
```

---

## 📡 Event Consumption

Listens to Kafka topics:

| Topic | Event | Action |
|-------|-------|--------|
| `user.commands` | CreateUserCommand | Validate → Hash password → Save to DB → Publish UserCreatedEvent |
| `user.commands` | AuthenticateUserCommand | Decrypt password → Compare BCrypt hash → Publish UserAuthenticatedEvent or generic failure |

`UserCommandListener` is a Kafka adapter: it registers handlers with
`KafkaServiceBus`, deserializes incoming command messages, delegates to
`UserService`, and publishes events with the command ID as the correlation ID.
The current listener registers `CreateUserCommand` and
`AuthenticateUserCommand`; the other command classes in the shared library are
not yet wired to handlers. The Gateway consumes these replies and completes
the waiting HTTP request.

---

## 💾 Database Schema (PostgreSQL)

**Database Name:** `user_db`  
**Port:** `5432` (in docker-compose)
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) UNIQUE NOT NULL,
    email_normalized VARCHAR(320) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION'
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DELETED')),
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
```

**Entity Example:**
```java
@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;
    
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "email_normalized", nullable = false, unique = true, length = 320)
    private String emailNormalized;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserAccountStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
```

---

## 🚀 Running Locally

### Prerequisites
- Java 25
- Docker (Kafka + PostgreSQL)

### Steps

1. **Start infrastructure:**
```bash
   docker-compose up -d postgres-user kafka zookeeper
```

2. **Run service:**
```bash
   cd user-service
   mvn spring-boot:run
```

3. **Verify database:**
```bash
   docker exec -it postgres-user psql -U user_admin -d user_db
   \dt  # List tables
   SELECT * FROM users;
```

4. **Publish test command:**
```bash
   # Via Kafka console producer
   docker exec -it kafka kafka-console-producer \
     --broker-list localhost:9092 \
     --topic user.commands
   
   # Then paste a command with its required BaseCommand metadata:
   {"commandId":"9c5b8c53-cb6d-4650-87f5-4e6bb51b1d22","timestamp":1790798400000,"commandType":"CREATE_USER","email":"test@example.com","name":"John Doe","password":"<account-password>"}
```

---

## ⚙️ Configuration

The worker reads its database and Kafka settings from environment variables.
The authentication private key is supplied separately and must match the
public key configured in API Gateway.

| Variable | Meaning |
|----------|---------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | User Service PostgreSQL connection |
| `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_CONSUMER_GROUP_ID` | Kafka connection and worker consumer group |
| `KAFKA_TOPIC_USER_COMMANDS`, `KAFKA_TOPIC_USER_EVENTS` | Command and reply topic names |
| `USER_AUTH_PRIVATE_KEY_BASE64` | Base64-encoded PKCS#8 RSA private key used to decrypt login passwords |

Store the private key in the deployment's secret store. The worker has no REST
controller; HTTP login requests enter through API Gateway.

---

## 📊 Technology Stack

- **Spring Boot**: 4.0.2
- **Java**: 25
- **Spring Data JPA**: ORM layer
- **PostgreSQL**: 15
- **Apache Kafka**: Event streaming
- **Lombok**: Code generation
- **Maven**: 3.9+

---

## 🔮 Future Enhancements

- [ ] Email verification workflow
- [ ] OAuth2 integration (Google, GitHub)
- [ ] User avatar storage (S3)
- [ ] Two-factor authentication

---

**Author:** Michał (mjaracz) 
**Role:** Pure Event-Driven Worker  
**Domain:** User Identity & Authentication
