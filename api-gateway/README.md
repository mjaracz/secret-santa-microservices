# API Gateway

> **Part of Secret Santa Microservices Platform**
>
> *Note: In production, this would be a separate repository. This mono-repo structure is for portfolio demonstration.*

---

## 🏗️ Architectural Role

**Type:** `Gateway Service` (**NOT a Worker**)

**Responsibility:**  
Single entry point for all client requests. Acts as a REST/HTTP gateway that translates synchronous HTTP calls into asynchronous Kafka events. Routes commands to domain-specific worker services without implementing any business logic.

**Key Characteristics:**
- ✅ **Exposes REST endpoints** - Only service accessible via HTTP from external clients
- ✅ **Protocol translation** - Converts HTTP → Kafka events
- ✅ **Stateless** - No database, no persistent state
- ✅ **Event producer** - Publishes command events to Kafka topics
- ✅ **Reply consumer** - Completes HTTP requests from correlated worker events
- ❌ **No business logic** - All domain logic delegated to pure workers
- ❌ **No domain worker role** - It consumes replies, but does not execute worker business logic

**Architecture Pattern:**  
API Gateway Pattern + Event-Driven Microservices

**Communication Flow:**
```
HTTP Client
    ↓
WebFlux Controller → Gateway Service → CommandDispatcher
    ↓                         ↑
KafkaServiceBus → command topic → Domain Worker
    ↑                         ↓
PendingReplyStore ← correlated reply event ← worker event topic
    ↓
HTTP response (or signed JWT for successful login)
```

---

## 📦 Dependencies

### Spring Boot Initializer Selection

When generating this module from [start.spring.io](https://start.spring.io):

**Project Metadata:**
- Spring Boot: `4.0.2`
- Java: `25`
- Packaging: `Jar`
- Group: `com.secretsanta`
- Artifact: `api-gateway`
- Package: `com.secretsanta.gateway`

**Dependencies to Add:**

| Category | Dependency Name | Identifier | Purpose |
|----------|----------------|------------|---------|
| **Web** | Spring WebFlux | `spring-boot-starter-webflux` | Reactive HTTP controllers |
| **Messaging** | Spring Kafka | `spring-boot-starter-kafka` | Publish commands and consume worker replies |
| **Security** | Spring Security OAuth2 Resource Server | `spring-boot-starter-security-oauth2-resource-server` | Validate bearer JWTs |
| **Validation** | Spring Boot Validation | `spring-boot-starter-validation` | Validate request DTOs |
| **Ops** | Spring Boot Actuator | `spring-boot-starter-actuator` | Health checks and metrics |

### Maven Dependencies (pom.xml)
```xml
<dependencies>
    <!-- Reactive HTTP API -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Kafka command and reply transport -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-kafka</artifactId>
    </dependency>

    <!-- JWT bearer token support -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- Request validation and health endpoints -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

**Critical Notes:**
- ❌ **DO NOT add `spring-boot-starter-web`** - Use WebFlux instead (reactive)
- ❌ **DO NOT add JPA/Database** - Gateway is stateless
- Domain persistence and business rules belong in the worker services

---

## 🎯 Domain Responsibility

### What Gateway Does
- **Request Mapping** - Maps HTTP controllers and DTOs to Kafka commands
- **Request Validation** - Validates HTTP request fields
- **Authentication** - Issues JWTs after successful login and validates bearer tokens on protected routes
- **Request Correlation** - Waits for the worker reply that matches the command ID
- **Protocol Translation** - HTTP requests → Kafka commands and correlated replies → HTTP responses

### What Gateway Does NOT Do
- ❌ **No business logic** - No user validation, no draw algorithms
- ❌ **No database access** - Completely stateless
- ❌ **No direct service calls** - Only Kafka communication
- ❌ **No persistence or domain processing** - Workers own domain state and rules

The Gateway owns HTTP adaptation and authentication. Worker services own
domain decisions and persistence.

---

## 📨 Kafka Request-Reply Flow

The Gateway sends commands through `CommandDispatcher` and
`KafkaServiceBus.sendAndReceive(...)`. The bus registers a pending reply by
`commandId`, publishes the command and returns a `CompletableFuture`. Worker
listeners copy that ID into the reply event's `correlationId`. The Gateway's
`EventReplyListener` completes the matching future, after which the controller
maps the result to an HTTP response. The default reply timeout is five seconds.

### Commands published by the Gateway

| Kafka Topic | Command Types | Target Worker |
|-------------|---------------|---------------|
| `user.commands` | `CreateUserCommand`, `AuthenticateUserCommand` | User Service |
| `group.commands` | `CreateGroupCommand`, `UpdateGroupCommand`, `DeleteGroupCommand`, `AddMemberCommand`, `DrawNamesCommand`, `GetMyGroupsCommand` | Group Service |

Each command has a `commandType`, `commandId` and `timestamp`. The shared
command/event schemas are documented in
[shareable-common](../shareable-common/README.md).

### Replies consumed by the Gateway

`EventReplyListener` listens on `user.events` and `group.events` and completes
the pending request using `correlationId`. Successful commands return a
`CommandResponse` with the worker event in `data`; `CommandFailedEvent` becomes
a failed `CommandResponse`. A reply timeout is mapped to HTTP `504`.

| Reply topic | Examples | HTTP handling |
|-------------|----------|---------------|
| `user.events` | `UserCreatedEvent`, `UserAuthenticatedEvent`, `CommandFailedEvent` | Registration response or login token/error |
| `group.events` | `GroupCreatedEvent`, `MyGroupsFetchedEvent`, `DrawCompletedEvent`, `CommandFailedEvent` | Group response or query result |

## 🔐 HTTP API and JWT

| Method | Path | Authentication | Purpose |
|--------|------|----------------|---------|
| `POST` | `/api/auth/login` | Public | Validate credentials and issue a JWT |
| `POST` | `/api/users` | Public | Register a user |
| `GET` | `/api/groups/me` | Bearer JWT required | Return groups for the JWT subject |
| `POST` | `/api/groups` | Currently public | Create a group |
| `PUT` | `/api/groups/{groupId}` | Currently public | Update a group |
| `DELETE` | `/api/groups/{groupId}` | Currently public | Delete a group |
| `POST` | `/api/groups/{groupId}/members` | Currently public | Add a group member |
| `POST` | `/api/groups/{groupId}/draw` | Currently public | Run the group draw |

Only `GET /api/groups/me` currently requires authentication. The remaining
group operations are permitted by the present security configuration; do not
assume that they are protected by the login flow.

Login accepts an email and password:

```http
POST /api/auth/login HTTP/1.1
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "<account-password>"
}
```

A successful response contains:

```json
{
  "accessToken": "<signed-jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

The Gateway encrypts the password with RSA-OAEP using the User Service's RSA
public key before placing it in `AuthenticateUserCommand`. The User Service
decrypts it, compares it with the stored BCrypt hash, and publishes
`UserAuthenticatedEvent` on `user.events`. The Gateway issues an HS256 JWT
only after that reply succeeds.
The token subject is the user ID; it also carries the configured issuer,
audience, issue time and expiry. Its lifetime is 900 seconds.

Send the token to the current-user endpoint:

```http
GET /api/groups/me HTTP/1.1
Authorization: Bearer <accessToken>
```

The endpoint does not accept a user ID. It reads the authenticated JWT subject,
sends `GetMyGroupsCommand` to `group.commands`, then returns a `CommandResponse`
whose `data` contains `MyGroupsFetchedEvent`. A missing or invalid token
returns `401 Unauthorized`.

An empty result has this shape:

```json
{
  "success": true,
  "commandId": "<command-uuid>",
  "data": {
    "eventId": "<event-uuid>",
    "timestamp": 1790798400000,
    "eventType": "MY_GROUPS_FETCHED",
    "correlationId": "<command-uuid>",
    "groups": []
  }
}
```

---

## 💾 Database

**None** - Gateway is completely stateless.

---

## 🚀 Running Locally

### Prerequisites
- Java 25
- Docker (Kafka + Zookeeper)

### Steps

1. **Start Kafka and the worker databases:**
```bash
   docker-compose up -d zookeeper kafka postgres-user postgres-group
```

2. **Build the shared libraries:**
```bash
   cd shareable-common
   mvn clean install
   cd ../shareable-infrastructure
   mvn clean install
   cd ..
```

3. **Run User Service and Group Service** in separate terminals:
```bash
   cd user-service
   mvn spring-boot:run
```
```bash
   cd group-service
   mvn spring-boot:run
```
   Both workers and their databases must be available for the login and group
   query examples.

4. **Run Gateway:**
```bash
   cd api-gateway
   mvn spring-boot:run
```

5. **Health Check:**
```bash
   curl http://localhost:8090/actuator/health
   # Expected: {"status":"UP"}
```

6. **Register, log in and call the current-user endpoint:**
```bash
   curl -X POST http://localhost:8090/api/users \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com","name":"John Doe","password":"<account-password>"}'

   curl -X POST http://localhost:8090/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com","password":"<account-password>"}'

   curl http://localhost:8090/api/groups/me \
     -H "Authorization: Bearer <accessToken>"
```

---

## ⚙️ Configuration

Runtime settings are supplied through environment variables. Keep signing and
RSA private keys in the deployment's secret store; do not commit key material.

| Variable | Used by | Meaning |
|----------|---------|---------|
| `SERVER_PORT` | Gateway | HTTP port (`8090` in the local profile) |
| `KAFKA_BOOTSTRAP_SERVERS` | Gateway and workers | Kafka bootstrap address |
| `KAFKA_CONSUMER_GROUP_ID` | Gateway | Consumer group for worker replies |
| `KAFKA_TOPIC_USER_COMMANDS`, `KAFKA_TOPIC_USER_EVENTS` | Gateway/User Service | User command and reply topics |
| `KAFKA_TOPIC_GROUP_COMMANDS`, `KAFKA_TOPIC_GROUP_EVENTS` | Gateway/Group Service | Group command and reply topics |
| `KAFKA_REPLY_TIMEOUT_SECONDS` | Gateway | Request-reply timeout (default `5`) |
| `JWT_SECRET_BASE64` | Gateway | Base64-encoded HMAC key; at least 32 decoded bytes |
| `JWT_ISSUER`, `JWT_AUDIENCE` | Gateway | JWT issuer (default `https://secret-santa-api`) and audience (default `secret-santa-api`) |
| `USER_AUTH_PUBLIC_KEY_BASE64` | Gateway | Base64-encoded X.509 RSA public key used to encrypt login passwords |
| `USER_AUTH_PRIVATE_KEY_BASE64` | User Service | Matching Base64-encoded PKCS#8 RSA private key |

Gateway and User Service RSA values must be a matching key pair. The Gateway
validates the JWT signature, issuer, audience and expiry before accepting a
bearer token.

---

## 📊 Technology Stack

- **Spring Boot**: 4.0.2
- **Java**: 25
- **Spring WebFlux**: Non-blocking I/O
- **Spring Security OAuth2 Resource Server**: JWT validation
- **Apache Kafka**: Event streaming
- **Maven**: 3.9+

---

## 🔮 Future Enhancements

- [x] JWT login and bearer authentication for `GET /api/groups/me`
- [x] Request-reply correlation through command and event IDs
- [ ] Apply authentication and actor-based authorization to the remaining group write routes
- [ ] Circuit breaker (Resilience4j)
- [ ] Distributed tracing (Zipkin)
- [ ] Rate limiting per client

---

**Author:** Michał (mjaracz)  
**Role:** Gateway Service (REST → Kafka Bridge)  
**Pattern:** API Gateway + Event-Driven Architecture
