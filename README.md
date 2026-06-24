# Overview
Multi-threaded distributed microservices architecture for demonstration / educational purposes </br>
**Domain:** Secret Santa gift exchanges
<br/> 
</br>

**Technology stack**:
- Spring Boot 4.0.2 
- Java 25 
- Kafka
- PostgreSQL 
- MongoDB

Project created for demonstration and educational purposes. </br>

**Architecture Pattern:**

The platform is built using **event-driven microservices** pattern with **domain-driven design** principles.

```
┌─────────────────────────────────────────────────────────┐
│                    Client Layer                         │
│  (Web Browser, Mobile App - Future, API Consumers)      │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP/REST
                     ↓
┌────────────────────────────────────────────────────────┐
│                 API Gateway                            │
│  (Single Entry Point, Protocol Translation)            │
└────────────────────┬───────────────────────────────────┘
                     │ Kafka Events (Async)
                     ↓
┌────────────────────────────────────────────────────────┐
│              Apache Kafka (Event Bus)                  │
│  (Topics: user.events, group.events, etc.)             │
└─┬──────────┬──────────┬──────────┬─────────────────────┘
  │          │          │          │
  ↓          ↓          ↓          ↓
┌────┐   ┌─────┐   ┌────────┐  ┌──────────┐
│User│   │Group│   │Wishlist│  │Notification│
│Svc │   │Svc  │   │Svc     │  │Svc        │
└─┬──┘   └─┬───┘   └───┬────┘  └─────┬─────┘
  │        │           │             │
  ↓        ↓           ↓             ↓
┌────┐   ┌────┐     ┌────┐        ┌────┐
│PG  │   │PG  │     │PG  │        │Mongo│
│User│   │Grp │     │Wish│        │Notif│
└────┘   └────┘     └────┘        └─────┘
```

```
External Client (REST/HTTP)
    ↓
API Gateway (Protocol Bridge)
    ↓
Kafka Topics (Event Bus)
    ↓
Domain Workers (Business Logic)
    ↓
Databases (Persistence)
```

**Key Principles:**
- ✅ **API Gateway ONLY** exposes REST endpoints
- ✅ **Domain workers** communicate ONLY via Kafka events
- ✅ **Zero synchronous calls** between microservices
- ✅ **No REST dependencies** in worker services
- ✅ **Event sourcing** for all inter-service communication

**Why Pure Workers?**
- Loose coupling (services don't know about each other)
- Independent scaling (scale workers without touching gateway)
- Fault tolerance (Kafka buffers events if service down)
- Async benefits (non-blocking, higher throughput)
- Natural audit trail (all commands/events in Kafka)

### Database Per Service Pattern

Each domain service owns its isolated database:

| Service | Database | Type | Port |
|---------|----------|------|------|
| API Gateway | None | Stateless | - |
| User Service | user_db | PostgreSQL | 5432 |
| Group Service | group_db | PostgreSQL | 5433 |
| Wishlist Service (In Prgoress) | wishlist_db | PostgreSQL | 5434 |
| Notification Service (In Progress) | notification_db | **MongoDB** | 27017 |

## Prerequisites
- Docker version 29.1.5
- Java 25.0.2 LTS
- Apache Maven 3.9.12

#### Build order 

<p>sherable-common -> sherable-infrastructure -> user-service / group-service -> api-gateway</p>

## Launching the project 
<h5> Each step in dedicated terminal</h5>

<h5> Containers Infrastructure </h5>

```bash
docker compose up 
```

<h5> Shareable Common</h5>

```bash
cd shareable-common
mvn clean install
```

<h5> Shareable Infrastructure</h5>

```bash
cd shareable-infrastructure
mvn clean install
```

<h5> User Service</h5>

```bash
cd user-service
mvn clean install
mvn spring-boot:run
```

<h5> Group Service</h5>

```bash
cd group-service
mvn clean install
mvn spring-boot:run
```

<h5> Api Gatewat</h5>

```bash
cd api-gateway
mvn clean install
mvn spring-boot:run
```

## Available endpoints

### Create User
```HTTP
POST /api/users HTTP/1.1
Content-Type: application/json

{
    "email": "Tom@email.me",
    "name": "Tom",
    "password": "1234"
}
```

### Create Group
```HTTP
POST /api/groups HTTP/1.1
Content-Type: application/json

{
    "name": "Group Name",
    "description": "Grupa Example 2.0",
    "ownerId": "e9123b90-180b-4378-a1d6-a51a03ec7657",
    "maxMembers": 50
}
```

### Update Group (requested only by the group owner)
```HTTP
PUT /api/groups/{groupId} HTTP/1.1
Content-Type: application/json

{
    "requestedBy": "e9123b90-180b-4378-a1d6-a51a03ec7657",
    "name": "EngineersUpdated",
    "description": "Group Zaktualizowana",
    "maxMembers": 10
}
```

### Add Member (requested only by the group owner)
```HTTP
POST /api/groups/{groupId}/members HTTP/1.1
Content-Type: application/json

{
    "requestedBy": "e9123b90-180b-4378-a1d6-a51a03ec7657",
    "userId": "265088b3-70eb-40cf-b632-00c289d117fc",
    "userEmail": "example@email.me",
    "userName": "Tom123",
    "role": "member"
}
```

### Group Draw (requested only by the group owner )
```HTTP
POST /api/groups/{groupId}/draw HTTP/1.1
Content-Type: application/json

{
    "requestedBy": "265088b3-70eb-40cf-b632-00c289d117fc"
}
```

### Delete Group
```HTTP
DELETE /api/groups/{groupId}?ownerId={ownerId} HTTP/1.1
```

### Add Wishlist Item
```HTTP
POST /api/groups/{groupId}/wishlist HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "title": "Noise-cancelling headphones",
    "description": "Optional gift details",
    "url": "https://example.com/gift"
}
```

### Get Own Wishlist
```HTTP
GET /api/groups/{groupId}/wishlist HTTP/1.1
Authorization: Bearer {accessToken}
```

### Update Wishlist Item
```HTTP
PUT /api/groups/{groupId}/wishlist/{itemId} HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "title": "Updated gift title",
    "description": "Updated optional gift details",
    "url": "https://example.com/updated-gift"
}
```

### Delete Wishlist Item
```HTTP
DELETE /api/groups/{groupId}/wishlist/{itemId} HTTP/1.1
Authorization: Bearer {accessToken}
```

### Get My Assignment (available after draw)
```HTTP
GET /api/groups/{groupId}/assignments/me HTTP/1.1
Authorization: Bearer {accessToken}
```

### Mark Gift As Purchased
```HTTP
PATCH /api/groups/{groupId}/assignments/me/purchased HTTP/1.1
Authorization: Bearer {accessToken}
Content-Type: application/json

{
    "giftPurchased": true
}
```

### Get Receiver Wishlist (available after draw)
```HTTP
GET /api/groups/{groupId}/receiver-wishlist HTTP/1.1
Authorization: Bearer {accessToken}
```
</br>


<h6>The project is in the functionality development stage and is for demonstration purposes only</h6>
<h6>Author: mjaracz</h6>
