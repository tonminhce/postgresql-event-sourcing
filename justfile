#!/usr/bin/env just --justfile

build:
    ./mvnw -DskipTests package

test:
    ./mvnw test

jib:
    ./mvnw compile jib:dockerBuild -pl event-sourcing-app

up:
    docker compose up -d

# Two instances of the application, to exercise the transactional outbox and the
# optimistic concurrency control across several JVMs.
up-scaled:
    docker compose up -d --scale event-sourcing-app=2

down:
    docker compose down -v

# Dump the PostgreSQL database and the Kafka log directory into ./backup.
backup:
    mkdir -p backup
    docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' > backup/postgres.dump
    docker compose exec -T kafka sh -c 'tar czf - -C /var/lib/kafka/data . || true' > backup/kafka-data.tar.gz
    docker compose exec -T kafka /bin/kafka-topics --bootstrap-server localhost:9092 --describe > backup/kafka-topics.txt
    ls -lh backup

e2e:
    E2E_TESTING=true ./mvnw test -pl event-sourcing-app -Dtest=PostgresEventSourcingApplicationE2ETests

test-e2e: jib up e2e
    just down
