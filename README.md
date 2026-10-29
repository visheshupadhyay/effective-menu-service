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
