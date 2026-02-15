# Overview
Event-driven microservices platform for Secret Santa gift exchanges.<br/>
**Technology stack**:
- Spring Boot 4.0.2 
- Java 25 
- Kafka
- PostgreSQL 
- MongoDB

Project created for demonstration and educational purposes. </br>

## Prerequisites
- Docker version 29.1.5
- Java 25.0.2 LTS
- Apache Maven 3.9.12

#### Build order 

<p>sherable-common -> sherable-infrastructure -> user-service / group-service -> api-gateway</p>

## Launching the project 

#### shareable-common
```bash
cd shareable-common
mvn clean install
```

#### shareable-infrastructure
```bash
cd shareable-infrastructure
mvn clean install
```

#### user-service
```bash
cd user-service
mvn clean install
```

#### group-service
```bash
cd group-service
mvn clean install
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

### Update Group
```HTTP
PUT /api/groups/{groupId} HTTP/1.1
Content-Type: application/json

{
    "name": "EngineersUpdated",
    "description": "Group Zaktualizowana",
    "maxMembers": 10
}
```

### Add Member
```HTTP
POST /api/groups/{groupId}/members HTTP/1.1
Content-Type: application/json

{
    "userId": "e9123b90-180b-4378-a1d6-a51a03ec7657",
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
</br>


<h6>The project is in the functionality development stage and is for demonstration purposes only</h6>
<h6>Author: mjaracz</h6>
