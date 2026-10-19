package com.example.eventsourcing;

import com.example.eventsourcing.domain.event.OrderCompletedEvent;
import com.example.eventsourcing.domain.event.OrderPlacedEvent;
import com.example.eventsourcing.domain.event.OrderPriceAdjustedEvent;
import eventsourcing.postgresql.domain.event.Event;
import eventsourcing.postgresql.domain.event.EventWithId;
import eventsourcing.postgresql.repository.EventRepository;
import eventsourcing.postgresql.service.EventSubscriptionProcessor;
import eventsourcing.postgresql.service.event.AsyncEventHandler;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "event-sourcing.subscription-batch-size=10",
        "event-sourcing.subscription-max-retries=2",
        "event-sourcing.subscription-retry-backoff-ms=10"
})
class DeadLetterQueueIntegrationTests extends AbstractContainerBaseTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventSubscriptionProcessor subscriptionProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    static class PoisonPillTestHandler implements AsyncEventHandler {
        private final List<Long> processedEventIds = new CopyOnWriteArrayList<>();

        @Override
        public void handleEvent(EventWithId<Event> eventWithId) {
            long eventId = eventWithId.id();
            if (eventWithId.event().getVersion() == 2) {
                throw new RuntimeException("Simulated Poison Pill at version 2");
            }
            processedEventIds.add(eventId);
        }

        public List<Long> getProcessedEventIds() {
            return processedEventIds;
        }

        @Nonnull
        @Override
        public String getAggregateType() {
            return "ORDER_DLQ_TEST";
        }

        @Nonnull
        @Override
        public String getSubscriptionName() {
            return "dlq-test-subscription";
        }
    }

    @Test
    void shouldRoutePoisonPillToDeadLetterQueueAndContinueProcessingStream() {
        UUID aggregateId = UUID.randomUUID();
        PoisonPillTestHandler poisonPillTestHandler = new PoisonPillTestHandler();

        // Prepare 3 events in ORDER_DLQ_TEST aggregate
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO ES_AGGREGATE (ID, VERSION, AGGREGATE_TYPE) VALUES (?, 0, 'ORDER_DLQ_TEST')", aggregateId);

            // Event 1 (Normal)
            eventRepository.appendEvent(OrderPlacedEvent.builder()
                    .aggregateId(aggregateId)
                    .version(1)
                    .riderId(UUID.randomUUID())
                    .price(new BigDecimal("100.00"))
                    .route(List.of())
                    .build());

            // Event 2 (Poison Pill: version == 2)
            eventRepository.appendEvent(OrderPriceAdjustedEvent.builder()
                    .aggregateId(aggregateId)
                    .version(2)
                    .newPrice(new BigDecimal("120.00"))
                    .build());

            // Event 3 (Normal: version == 3)
            eventRepository.appendEvent(OrderCompletedEvent.builder()
                    .aggregateId(aggregateId)
                    .version(3)
                    .build());
        });

        // Trigger processing
        subscriptionProcessor.processNewEvents(poisonPillTestHandler);

        // Verify that event 1 and event 3 were successfully processed
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(poisonPillTestHandler.getProcessedEventIds()).hasSize(2);
        });

        // Verify that Dead Letter Queue contains the failed event 2
        Integer dlqCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ES_EVENT_SUBSCRIPTION_DEAD_LETTER WHERE SUBSCRIPTION_NAME = 'dlq-test-subscription'",
                Integer.class
        );
        assertThat(dlqCount).isEqualTo(1);

        String errorMessage = jdbcTemplate.queryForObject(
                "SELECT ERROR_MESSAGE FROM ES_EVENT_SUBSCRIPTION_DEAD_LETTER WHERE SUBSCRIPTION_NAME = 'dlq-test-subscription'",
                String.class
        );
        assertThat(errorMessage).contains("Simulated Poison Pill at version 2");

        Integer retryCount = jdbcTemplate.queryForObject(
                "SELECT RETRY_COUNT FROM ES_EVENT_SUBSCRIPTION_DEAD_LETTER WHERE SUBSCRIPTION_NAME = 'dlq-test-subscription'",
                Integer.class
        );
        // Initial try + 2 retries = 3 attempts total
        assertThat(retryCount).isEqualTo(3);
    }
}
