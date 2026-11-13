# Effective Menu Service

The Effective Menu Service consumes Catalog menu-item events, maintains one read-model snapshot per outlet, and serves the customer menu API.

## Local run

Start PostgreSQL and Kafka from `deliveryhero-platform-integration`, then run:

```powershell
$env:EFFECTIVE_MENU_DB_URL = "jdbc:postgresql://127.0.0.1:5434/effective_menu_db"
$env:EFFECTIVE_MENU_DB_USERNAME = "effective_menu_app"
$env:EFFECTIVE_MENU_DB_PASSWORD = "effective_menu_password"
$env:KAFKA_BOOTSTRAP_SERVERS = "127.0.0.1:9092"
mvn spring-boot:run
```

The service runs on `http://localhost:8081`. The customer endpoint is:

```text
GET /api/v1/outlets/{outletId}/menu
```

Health endpoint: `http://localhost:8081/actuator/health`.

## Event flow

```text
Catalog Command Service
    -> Kafka: catalog.menu-item.changed
    -> Effective Menu Service consumer
    -> idempotent/version-aware snapshot update
    -> effective-menu PostgreSQL read model
    -> GET /api/v1/outlets/{outletId}/menu
```

This service never reads the Catalog Service database directly.

## Redis cache behavior

Redis is a performance layer; PostgreSQL remains the source of truth for
effective-menu snapshots.

- Cache keys use the form `effective-menu:outlet:{outletId}`.
- A cached snapshot lives for the configured TTL (`60s` by default).
- A cache hit returns the snapshot without a PostgreSQL query.
- A cache miss reads PostgreSQL and then refreshes Redis.
- Redis read, write, eviction, or JSON-deserialization failures are converted
  into safe cache misses/fallbacks. They do not fail the customer menu request.
- When locking is enabled, the first request that misses Redis obtains a
  per-outlet refresh lock. It loads PostgreSQL and fills Redis; concurrent
  requests wait briefly and reuse the refreshed value. The lock expires after
  `5s` to prevent a permanently stuck lock.
- A menu-item event evicts the affected outlet key. The next customer request
  reads the updated PostgreSQL snapshot and repopulates Redis.

### Configuration

These environment variables control the cache:

| Variable | Default | Purpose |
| --- | --- | --- |
| `EFFECTIVE_MENU_CACHE_ENABLED` | `true` | Enable or disable Redis reads/writes |
| `EFFECTIVE_MENU_CACHE_TTL` | `60s` | Snapshot lifetime in Redis |
| `EFFECTIVE_MENU_CACHE_KEY_PREFIX` | `effective-menu:outlet:` | Snapshot key prefix |
| `EFFECTIVE_MENU_CACHE_LOCK_ENABLED` | `true` | Enable per-outlet refresh locking |
| `EFFECTIVE_MENU_CACHE_LOCK_TTL` | `5s` | Maximum lock lifetime |
| `EFFECTIVE_MENU_CACHE_LOCK_WAIT_TIMEOUT` | `2s` | Maximum time a waiter retries |
| `EFFECTIVE_MENU_CACHE_LOCK_RETRY_INTERVAL` | `50ms` | Delay between lock attempts |
| `SPRING_DATA_REDIS_HOST` | `127.0.0.1` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SPRING_DATA_REDIS_TIMEOUT` | `2s` | Redis command timeout |

### Redis outage recovery

If Redis is unavailable, the service continues serving menus from PostgreSQL.
Cache writes are best-effort, so a Redis outage may increase database reads but
must not lose or change the read-model data. After Redis becomes healthy again,
successful requests automatically repopulate the cache.

Useful checks:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8081/actuator/effective-menu
docker exec -it <redis-container> redis-cli keys "effective-menu:*"
```

The operational endpoint exposes cache hits, misses, read failures, writes,
write failures, evictions, and eviction failures. A growing failure count means
Redis needs investigation; a growing miss count alone is expected after expiry
or an event-driven eviction.

### Health and readiness

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health/liveness
Invoke-RestMethod http://localhost:8081/actuator/health/readiness
Invoke-RestMethod http://localhost:8081/actuator/health
```

Liveness means the Java process is alive. Readiness includes PostgreSQL and Kafka;
either dependency being unavailable makes readiness `DOWN`. Redis is intentionally
not a readiness dependency because customer reads fall back to PostgreSQL when Redis
is unavailable.

The custom `kafkaHealthIndicator` checks broker metadata using the configured
`spring.kafka.bootstrap-servers` address. The Docker Compose value is `kafka:19092`;
the host-run default is `localhost:9092`.

### Failure recovery commands

From `deliveryhero-platform-integration`:

```powershell
docker compose stop kafka
docker compose start kafka
docker compose stop redis
docker compose start redis
docker compose ps
```

After restarting Kafka, wait for its health check before expecting readiness to return
to `UP`. After restarting Redis, the next customer request repopulates the cache from
PostgreSQL.

### Local cache benchmark

Run the benchmark with Redis available through Docker:

```powershell
mvn -Dtest=EffectiveMenuCacheBenchmarkTest test
```

The test prints approximate timings for a Redis hit, Redis miss, the complete
PostgreSQL fallback path, and one Redis-unavailable fallback sample. It warms up
the JVM before measuring and uses assertions only for correctness. Timing values
are machine-specific and should be recorded as local comparison evidence, not
 as a production capacity guarantee.

### Scheduled availability handling

The consumer receives schedule windows and the outlet timezone with the versioned
menu-item event. It uses an outlet weekday override first and otherwise inherits the
base schedule, then evaluates the current local day/time. Outside every active
window it stores `availability=false` while preserving visibility; archived items
remain absent. After saving a newer snapshot it evicts only that outlet's Redis key.
