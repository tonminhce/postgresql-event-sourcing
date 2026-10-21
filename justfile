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

e2e:
    E2E_TESTING=true ./mvnw test -pl event-sourcing-app -Dtest=PostgresEventSourcingApplicationE2ETests

test-e2e: jib up e2e
    just down
