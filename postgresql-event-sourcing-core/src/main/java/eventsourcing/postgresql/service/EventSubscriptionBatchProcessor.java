package eventsourcing.postgresql.service;

import eventsourcing.postgresql.config.EventSourcingProperties;
import eventsourcing.postgresql.domain.event.Event;
import eventsourcing.postgresql.domain.event.EventWithId;
import eventsourcing.postgresql.repository.EventRepository;
import eventsourcing.postgresql.repository.EventSubscriptionDeadLetterRepository;
import eventsourcing.postgresql.repository.EventSubscriptionRepository;
import eventsourcing.postgresql.service.event.AsyncEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventSubscriptionBatchProcessor {

    private final EventSubscriptionRepository subscriptionRepository;
    private final EventSubscriptionDeadLetterRepository deadLetterRepository;
    private final EventRepository eventRepository;
    private final EventSourcingProperties properties;
    private final eventsourcing.postgresql.metrics.EventSourcingMetrics metrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processBatch(AsyncEventHandler eventHandler, String subscriptionName, int batchSize) {
        subscriptionRepository.createSubscriptionIfAbsent(subscriptionName);
        return subscriptionRepository.readCheckpointAndLockSubscription(subscriptionName)
                .map(checkpoint -> {
                    log.debug("Acquired lock on subscription {}, checkpoint = {}", subscriptionName, checkpoint);
                    List<EventWithId<Event>> events = eventRepository.readEventsAfterCheckpoint(
                            eventHandler.getAggregateType(),
                            checkpoint.lastProcessedTransactionId(),
                            checkpoint.lastProcessedEventId(),
                            batchSize
                    );
                    log.debug("Fetched {} new event(s) for subscription {}", events.size(), subscriptionName);
                    if (events.isEmpty()) {
                        long lag = subscriptionRepository.calculateSubscriptionLag(subscriptionName, eventHandler.getAggregateType());
                        metrics.recordSubscriptionLag(subscriptionName, eventHandler.getAggregateType(), lag);
                        return false;
                    }

                    long startNanos = System.nanoTime();
                    for (EventWithId<Event> eventWithId : events) {
                        handleWithRetry(eventHandler, subscriptionName, eventWithId);
                        subscriptionRepository.updateEventSubscription(
                                subscriptionName, eventWithId.transactionId(), eventWithId.id());
                    }
                    metrics.recordHandlerDuration(subscriptionName, eventHandler.getAggregateType(), System.nanoTime() - startNanos);

                    long lag = subscriptionRepository.calculateSubscriptionLag(subscriptionName, eventHandler.getAggregateType());
                    metrics.recordSubscriptionLag(subscriptionName, eventHandler.getAggregateType(), lag);

                    return events.size() == batchSize;
                })
                .orElseGet(() -> {
                    log.debug("Can't acquire lock on subscription {}", subscriptionName);
                    return false;
                });
    }

    private void handleWithRetry(AsyncEventHandler eventHandler,
                                 String subscriptionName,
                                 EventWithId<Event> eventWithId) {
        int maxRetries = properties.getSubscriptionMaxRetries();
        long backoffMs = properties.getSubscriptionRetryBackoffMs();
        int attempts = 0;

        while (true) {
            attempts++;
            try {
                eventHandler.handleEvent(eventWithId);
                return;
            } catch (Exception ex) {
                log.warn("Error handling event {} (id={}) for subscription {} on attempt {}/{}",
                        eventWithId.event().getEventType(), eventWithId.id(), subscriptionName, attempts, maxRetries, ex);

                if (attempts > maxRetries) {
                    log.error("Max retries ({}) exceeded for event {} (id={}) on subscription {}. Routing to Dead Letter Queue.",
                            maxRetries, eventWithId.event().getEventType(), eventWithId.id(), subscriptionName, ex);
                    try {
                        deadLetterRepository.saveDeadLetter(
                                subscriptionName,
                                eventHandler.getAggregateType(),
                                eventWithId,
                                ex,
                                attempts
                        );
                        metrics.recordDlqEvent(subscriptionName, eventHandler.getAggregateType());
                    } catch (Exception dlqEx) {
                        log.error("Failed to save event to Dead Letter Queue!", dlqEx);
                    }
                    return;
                }

                try {
                    Thread.sleep(backoffMs * (1L << (attempts - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during event retry backoff", ie);
                }
            }
        }
    }
}
