# User Service persistence latency comparison

This comparison measures the same API Gateway endpoints with two User Service
persistence implementations:

- MongoDB from commit `6554973`;
- PostgreSQL restored from commit `2156847`, while retaining the current Gateway,
  Kafka request-reply path and `Server-Timing` filter.

The measurements were made locally on the same machine with a sequential client,
warm services and indexes, the same BCrypt configuration, and the same Kafka
broker. `Server-Timing: total;dur=...` is used below, so latency covers Gateway,
Kafka request-reply, User Service and persistence. Warm-up requests are excluded.
The PostgreSQL run used an isolated `user_benchmark_pg` database migrated by
Flyway V1-V3. Hibernate SQL logging was disabled during measurement.

## Results

| Endpoint | Samples | MongoDB avg (ms) | PostgreSQL avg (ms) | Difference (ms) | PostgreSQL vs MongoDB | MongoDB p95 (ms) | PostgreSQL p95 (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|
| `POST /api/auth/sign-in` success | 30 | 118.28 | 127.28 | +9.00 | +7.6% | 127.86 | 136.59 |
| `POST /api/auth/sign-in` invalid password | 20 | 119.05 | 120.85 | +1.80 | +1.5% | 154.83 | 127.43 |
| `GET /api/auth/me` | 50 | 3.48 | 3.66 | +0.18 | +5.2% | 4.88 | 4.74 |
| `POST /api/auth/refresh` | 25 | 39.71 | 53.57 | +13.86 | +34.9% | 43.93 | 97.05 |
| `POST /api/auth/verification/resend` for active account | 30 | 29.08 | 29.33 | +0.25 | +0.9% | 32.25 | 31.79 |
| `GET /api/auth/verify-email` with invalid token | 30 | 25.26 | 27.53 | +2.27 | +9.0% | 26.82 | 29.29 |
| `POST /api/users` unique registration | 20 | 106.16 | 123.39 | +17.23 | +16.2% | 115.54 | 130.28 |
| `POST /api/users` duplicate email | 30 | 25.62 | 27.70 | +2.08 | +8.1% | 27.18 | 29.82 |
| `POST /api/users` Gateway validation failure | 50 | 3.55 | 3.60 | +0.05 | +1.4% | 4.24 | 4.55 |
| `POST /api/auth/sign-out` | 15 | 31.59 | 37.34 | +5.75 | +18.2% | 36.28 | 68.78 |

## Interpretation

The database engine is not the dominant source of latency in this request path.
Gateway-only JWT and validation paths take about 3.5 ms. Indexed lookups plus the
Kafka round trip are generally in the 25-30 ms range on both databases. BCrypt
raises registration and sign-in to roughly 106-127 ms regardless of persistence.

PostgreSQL is measurably slower in this implementation when one request expands
to multiple relational operations. Refresh rotates one row and inserts another;
registration inserts a user and a verification token. MongoDB stores these as
changes to one aggregate document. This is a comparison of the implemented data
models and persistence stacks (Spring Data MongoDB versus Hibernate/JPA), not a
claim that MongoDB is inherently faster than PostgreSQL.

## Transactionality decision

MongoDB already guarantees atomic updates within one user document, so moving to
PostgreSQL is not required solely to make the current embedded aggregate atomic.
PostgreSQL becomes valuable when authentication data is treated as independently
growing records: refresh-session history, token-family revocation, verification
tokens, audit queries and cleanup jobs. Row locks, relational constraints and
separate indexes make those invariants explicit and prevent the user document
from becoming an unbounded, increasingly contended write target.

For a production identity domain, the measured PostgreSQL overhead is reasonable:
about 9 ms for successful sign-in, 14 ms for refresh and 17 ms for registration.
These paths remain dominated by BCrypt or the Kafka request-reply flow. PostgreSQL
is therefore the safer default when consistency, auditability and session growth
matter more than minimizing a small amount of local latency. The embedded MongoDB
model remains valid when session/token collections are strictly bounded and all
important invariants stay inside one aggregate document.

The result is a local sequential baseline, not a capacity test. A decision based
on performance should additionally use repeated interleaved runs, a larger data
set, concurrent clients, p99, throughput, and the same durability configuration.
Consistency guarantees, constraints, query patterns and operational cost remain
more important architectural inputs than the small differences in lookup paths.
