package eventsourcing.postgresql.service;

import eventsourcing.postgresql.config.EventSourcingProperties;
import eventsourcing.postgresql.repository.EventSubscriptionRepository;
import eventsourcing.postgresql.service.event.AsyncEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventSubscriptionProcessor {

    private final EventSubscriptionBatchProcessor batchProcessor;
    private final EventSourcingProperties properties;

    @Async
    public void processNewEvents(AsyncEventHandler eventHandler) {
        String subscriptionName = eventHandler.getSubscriptionName();
        log.debug("Handling new events for subscription {}", subscriptionName);

        int batchSize = properties.getSubscriptionBatchSize();
        boolean hasMore = true;
        while (hasMore) {
            hasMore = batchProcessor.processBatch(eventHandler, subscriptionName, batchSize);
        }
    }
}
