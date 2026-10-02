# Shared Message Contracts

`shareable-common` is the Maven library for command, event and DTO types shared
by API Gateway and the worker services. It contains Jackson metadata, Jakarta
Bean Validation annotations and Lombok annotations. It has no Spring, Kafka or
JPA dependencies.

## Source layout

```text
src/main/java/com/secretsanta/common/
├── BaseCommand.java
├── BaseEvent.java
├── CommandFailedEvent.java
├── group/
│   ├── commands/
│   │   ├── AddMemberCommand.java
│   │   ├── CreateGroupCommand.java
│   │   ├── DeleteGroupCommand.java
│   │   ├── DrawNamesCommand.java
│   │   ├── GetMyGroupsCommand.java
│   │   └── UpdateGroupCommand.java
│   ├── dto/
│   │   ├── DrawAssignmentDto.java
│   │   └── GroupSummaryDto.java
│   └── events/
│       ├── DrawCompletedEvent.java
│       ├── GroupCreatedEvent.java
│       ├── GroupDeletedEvent.java
│       ├── GroupUpdatedEvent.java
│       ├── MemberAddedEvent.java
│       └── MyGroupsFetchedEvent.java
└── user/
    ├── UserAccountStatus.java
    ├── commands/
    │   ├── AuthenticateUserCommand.java
    │   ├── CreateUserCommand.java
    │   ├── DeleteUserCommand.java
    │   └── UpdateUserCommand.java
    └── events/
        ├── UserAuthenticatedEvent.java
        └── UserCreatedEvent.java
```

## Command and event metadata

`BaseCommand` provides `commandId`, `timestamp` and `commandType`. Its
`initDefaults(type)` method fills in an ID and timestamp if they are missing,
then sets the type. `BaseEvent` similarly provides `eventId`, `timestamp`,
`eventType` and `correlationId`.

Jackson uses `commandType` and `eventType` to deserialize the concrete subtype.
The supported subtype names are registered in `BaseCommand` and `BaseEvent`.
Keep these names stable because producers and consumers use them on Kafka.

For request reply, a worker copies the incoming command's `commandId` into the
reply event's `correlationId`. API Gateway uses that value to match the reply
to its waiting HTTP request. `BaseCommand` has no `actorId` property; an actor
identifier belongs to the specific command that needs it.

## Login message flow

`POST /api/auth/login` is handled by API Gateway. It encrypts the supplied
password with the User Service's RSA public key using OAEP with SHA-256, then
sends an `AuthenticateUserCommand` to `user.commands`. The Kafka command
contains the encrypted password, not the submitted plaintext password:

```json
{
  "commandId": "<uuid>",
  "timestamp": 1790798400000,
  "commandType": "AUTHENTICATE_USER",
  "email": "user@example.com",
  "encryptedPassword": "<base64-ciphertext>"
}
```

User Service decrypts the password, checks it against the stored BCrypt hash,
and publishes a `UserAuthenticatedEvent` to `user.events`. The event carries
the command ID as its `correlationId` and the authenticated user's ID:

```json
{
  "eventId": "<uuid>",
  "timestamp": 1790798400000,
  "eventType": "USER_AUTHENTICATED",
  "correlationId": "<commandId>",
  "userId": "<user-uuid>"
}
```

After receiving this reply, API Gateway issues the JWT. The HTTP request and
response DTOs, JWT signing and token validation belong to API Gateway; they are
not shared Kafka contracts.

## Current-user group query

`GET /api/groups/me` requires a bearer JWT at API Gateway. The Gateway takes
the user ID from the verified token subject and publishes a
`GetMyGroupsCommand` on `group.commands`. This command declares `requestedBy`
itself; the value is not inherited from `BaseCommand`:

```json
{
  "commandId": "<uuid>",
  "timestamp": 1790798400000,
  "commandType": "GET_MY_GROUPS",
  "requestedBy": "<user-uuid>"
}
```

Group Service queries groups containing that member and publishes a correlated
`MyGroupsFetchedEvent` to `group.events`:

```json
{
  "eventId": "<uuid>",
  "timestamp": 1790798400000,
  "eventType": "MY_GROUPS_FETCHED",
  "correlationId": "<commandId>",
  "groups": [
    {
      "groupId": "<group-uuid>",
      "name": "Gift Exchange",
      "description": null,
      "ownerId": "<user-uuid>",
      "maxMembers": 12,
      "memberCount": 4,
      "drawn": false,
      "createdAt": "2026-09-30T12:00:00Z"
    }
  ]
}
```

Each entry in `groups` is a `GroupSummaryDto` record with `groupId`, `name`,
`description`, `ownerId`, `maxMembers`, `memberCount`, `drawn` and `createdAt`.
No memberships produce an empty array. API Gateway wraps the event in its
`CommandResponse` before returning it over HTTP.

## Other current message types

The shared library also defines these contracts:

| Area | Commands | Events and replies |
|------|----------|--------------------|
| User | `CreateUserCommand`, `UpdateUserCommand`, `DeleteUserCommand`, `AuthenticateUserCommand` | `UserCreatedEvent`, `UserAuthenticatedEvent` |
| Group | `CreateGroupCommand`, `UpdateGroupCommand`, `DeleteGroupCommand`, `AddMemberCommand`, `DrawNamesCommand`, `GetMyGroupsCommand` | `GroupCreatedEvent`, `GroupUpdatedEvent`, `GroupDeletedEvent`, `MemberAddedEvent`, `DrawCompletedEvent`, `MyGroupsFetchedEvent` |

`CommandFailedEvent` is the shared failure reply. `DrawAssignmentDto` contains
one draw pairing. `UserAccountStatus` contains the account lifecycle values
used by User Service.

## Adding a command or event

1. Add the message class under the relevant `user` or `group` package and
   extend `BaseCommand` or `BaseEvent`.
2. Register the Java class and its stable wire name in the matching
   `@JsonSubTypes` list. Without this registration, Kafka deserialization into
   the base type cannot select the new subtype.
3. Add or update the worker handler. For a request-reply command, set the
   response `correlationId` to the command's `commandId` before publishing.
4. Add serialization/deserialization and handler tests where the message is
   produced or consumed.
5. Build and install this module before rebuilding modules that depend on it.

## Build order

From the repository root, install the shared libraries before the services:

```bash
cd shareable-common
mvn clean install

cd ../shareable-infrastructure
mvn clean install
```

Then build the consuming service modules. The current artifact coordinates are
`com.secretsanta:shareable-common:0.0.1-SNAPSHOT`.
