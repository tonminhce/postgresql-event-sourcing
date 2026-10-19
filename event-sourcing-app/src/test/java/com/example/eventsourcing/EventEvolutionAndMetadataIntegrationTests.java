package com.example.eventsourcing;

import com.example.eventsourcing.domain.OrderAggregate;
import com.example.eventsourcing.domain.event.OrderPlacedEvent;
import com.example.eventsourcing.domain.event.OrderPriceAdjustedEvent;
import eventsourcing.postgresql.domain.event.Event;
import eventsourcing.postgresql.domain.event.EventMetadata;
import eventsourcing.postgresql.domain.event.EventWithId;
import eventsourcing.postgresql.repository.AggregateRepository;
import eventsourcing.postgresql.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EventEvolutionAndMetadataIntegrationTests extends AbstractContainerBaseTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AggregateRepository aggregateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldPersistAndRetrieveEventMetadata() {
        UUID aggregateId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO ES_AGGREGATE (ID, VERSION, AGGREGATE_TYPE) VALUES (?, 0, 'ORDER_METADATA_TEST')", aggregateId);

            OrderPlacedEvent event = OrderPlacedEvent.builder()
                    .aggregateId(aggregateId)
                    .version(1)
                    .riderId(UUID.randomUUID())
                    .price(new BigDecimal("99.99"))
                    .route(List.of())
                    .build();

            event.setMetadata(EventMetadata.of(correlationId, causationId, traceparent, 1));
            eventRepository.appendEvent(event);
        });

        List<EventWithId<Event>> events = transactionTemplate.execute(status ->
                eventRepository.readEvents(aggregateId, null, null)
        );

        assertThat(events).hasSize(1);
        Event loadedEvent = events.get(0).event();
        assertThat(loadedEvent.getMetadata()).isNotNull();
        assertThat(loadedEvent.getMetadata().getCorrelationId()).isEqualTo(correlationId);
        assertThat(loadedEvent.getMetadata().getCausationId()).isEqualTo(causationId);
        assertThat(loadedEvent.getMetadata().getTraceparent()).isEqualTo(traceparent);
        assertThat(loadedEvent.getMetadata().getSchemaVersion()).isEqualTo(1);
    }

    @Test
    void shouldUpcastLegacyEventPayloadToCurrentSchemaOnRead() {
        UUID aggregateId = UUID.randomUUID();

        // Simulate a legacy event in the database where "legacyPriceField" was used instead of "newPrice"
        // and schemaVersion was 1
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO ES_AGGREGATE (ID, VERSION, AGGREGATE_TYPE) VALUES (?, 0, 'ORDER_EVOLUTION_TEST')", aggregateId);

            jdbcTemplate.update("""
                    INSERT INTO ES_EVENT (TRANSACTION_ID, AGGREGATE_ID, VERSION, EVENT_TYPE, JSON_DATA, METADATA)
                    VALUES (pg_current_xact_id(), ?, 1, 'ORDER_PRICE_ADJUSTED',
                            '{"aggregateId": "%s", "version": 1, "legacyPriceField": 175.50}'::jsonb,
                            '{"schemaVersion": 1}'::jsonb)
                    """.formatted(aggregateId), aggregateId);
        });

        // Hydrate events - the EventUpcasterChain should automatically convert "legacyPriceField" -> "newPrice"
        List<EventWithId<Event>> events = transactionTemplate.execute(status ->
                eventRepository.readEvents(aggregateId, null, null)
        );

        assertThat(events).hasSize(1);
        assertThat(events.get(0).event()).isInstanceOf(OrderPriceAdjustedEvent.class);
        OrderPriceAdjustedEvent upcastedEvent = (OrderPriceAdjustedEvent) events.get(0).event();
        assertThat(upcastedEvent.getNewPrice()).isEqualByComparingTo("175.50");
    }

    @Test
    void shouldCompactSnapshotsViaUpsertOnSameAggregate() {
        UUID aggregateId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            aggregateRepository.createAggregateIfAbsent("ORDER_SNAPSHOT_TEST", aggregateId);

            OrderAggregate aggV10 = new OrderAggregate(aggregateId, 10);
            aggregateRepository.createAggregateSnapshot(aggV10);

            OrderAggregate aggV20 = new OrderAggregate(aggregateId, 20);
            aggregateRepository.createAggregateSnapshot(aggV20);
        });

        // Verify only 1 snapshot exists for this aggregate, and its version is 20
        Integer snapshotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ES_AGGREGATE_SNAPSHOT WHERE AGGREGATE_ID = ?",
                Integer.class,
                aggregateId
        );
        assertThat(snapshotCount).isEqualTo(1);

        Integer latestVersion = jdbcTemplate.queryForObject(
                "SELECT VERSION FROM ES_AGGREGATE_SNAPSHOT WHERE AGGREGATE_ID = ?",
                Integer.class,
                aggregateId
        );
        assertThat(latestVersion).isEqualTo(20);
    }
}
