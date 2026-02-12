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
- ❌ **No business logic** - All domain logic delegated to pure workers
- ❌ **Not a worker** - Does not consume/process domain events

**Architecture Pattern:**  
API Gateway Pattern + Event-Driven Microservices

**Communication Flow:**
```
External Client (REST/HTTP)
    ↓
API Gateway (Protocol Bridge)
    ↓
Kafka Topics (Event Bus)
    ↓
Domain Workers (Business Logic)
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
| **Routing** | Spring Cloud Gateway | `cloud-gateway` | Reactive API gateway routing engine |
| **Web** | Spring Reactive Web | `webflux` | Non-blocking HTTP handling (required by Gateway) |
| **Messaging** | Spring for Apache Kafka | `kafka` | Event producer for command publishing |
| **Ops** | Spring Boot Actuator | `actuator` | Health checks, metrics, monitoring |

### Maven Dependencies (pom.xml)
```xml
<dependencies>
    <!-- Spring Cloud Gateway -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>

    <!-- Spring WebFlux (Reactive Web) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Spring Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

**Critical Notes:**
- ❌ **DO NOT add `spring-boot-starter-web`** - Use WebFlux instead (reactive)
- ❌ **DO NOT add JPA/Database** - Gateway is stateless
- ❌ **DO NOT add Lombok** - Minimal code in gateway layer

---

## 🎯 Domain Responsibility

### What Gateway Does
- **Request Routing** - Maps HTTP paths to Kafka topics
- **Request Validation** - Basic input validation (syntax, required fields)
- **Protocol Translation** - HTTP POST/PUT/DELETE → Kafka command events
- **Rate Limiting** - Protects backend from request floods
- **CORS Handling** - Cross-origin resource sharing policies
- **API Versioning** - Supports /v1, /v2 endpoints

### What Gateway Does NOT Do
- ❌ **No business logic** - No user validation, no draw algorithms
- ❌ **No database access** - Completely stateless
- ❌ **No direct service calls** - Only Kafka communication
- ❌ **No data transformation** - Workers handle data processing

**Philosophy:** Gateway is a "dumb pipe" that routes traffic.

---

## 📨 Event Production

Publishes **command events** to Kafka:

| Kafka Topic | Command Events | Target Worker |
|-------------|----------------|---------------|
| `user.commands` | CreateUserCommand, UpdateUserCommand, DeleteUserCommand | User Service |
| `group.commands` | CreateGroupCommand, AddMemberCommand, RemoveMemberCommand, ExecuteDrawCommand | Group Service |
| `wishlist.commands` | AddWishlistItemCommand, UpdateWishlistItemCommand, DeleteWishlistItemCommand | Wishlist Service |

**Example Event:**
```json
{
  "commandType": "CreateUserCommand",
  "email": "user@example.com",
  "name": "John Doe",
  "timestamp": 1707562800000
}
```

---

## 📡 Event Consumption

**Typically NONE** - Gateway publishes events but does not consume them.

*Optional:* Can subscribe to result events for pseudo-synchronous responses:
- `user.events.UserCreatedEvent` → Return HTTP 201 with userId
- `group.events.GroupCreatedEvent` → Return HTTP 201 with groupId

*Recommendation:* Keep gateway purely async for better resilience.

---

## 💾 Database

**None** - Gateway is completely stateless.

---

## 🚀 Running Locally

### Prerequisites
- Java 25
- Docker (Kafka + Zookeeper)

### Steps

1. **Start Kafka:**
```bash
   docker-compose up -d kafka zookeeper
```

2. **Run Gateway:**
```bash
   cd api-gateway
   mvn spring-boot:run
```

3. **Health Check:**
```bash
   curl http://localhost:8080/actuator/health
   # Expected: {"status":"UP"}
```

4. **Test Endpoint:**
```bash
   curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"email":"test@example.com","name":"John Doe"}'
```

---

## ⚙️ Configuration

`application.yml`:
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      routes:
        - id: user-route
          uri: kafka:user.commands
          predicates:
            - Path=/api/users/**

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

---

## 📊 Technology Stack

- **Spring Boot**: 4.0.2
- **Java**: 25
- **Spring Cloud Gateway**: Reactive routing
- **Spring WebFlux**: Non-blocking I/O
- **Apache Kafka**: Event streaming
- **Maven**: 3.9+

---

## 🔮 Future Enhancements

- [ ] JWT authentication
- [ ] Request correlation IDs
- [ ] Circuit breaker (Resilience4j)
- [ ] Distributed tracing (Zipkin)
- [ ] Rate limiting per client

---

**Author:** Michał (mjaracz)  
**Role:** Gateway Service (REST → Kafka Bridge)  
**Pattern:** API Gateway + Event-Driven Architecture