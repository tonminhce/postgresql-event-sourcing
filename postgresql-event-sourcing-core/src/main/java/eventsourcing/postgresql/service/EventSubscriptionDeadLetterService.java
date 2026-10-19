package eventsourcing.postgresql.service;

import eventsourcing.postgresql.domain.event.Event;
import eventsourcing.postgresql.domain.event.EventSubscriptionDeadLetterRecord;
import eventsourcing.postgresql.domain.event.EventWithId;
import eventsourcing.postgresql.repository.EventRepository;
import eventsourcing.postgresql.repository.EventSubscriptionDeadLetterRepository;
import eventsourcing.postgresql.service.event.AsyncEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventSubscriptionDeadLetterService {

    private final EventSubscriptionDeadLetterRepository deadLetterRepository;
    private final EventRepository eventRepository;
    private final List<AsyncEventHandler> eventHandlers;

    @Transactional(readOnly = true)
    public List<EventSubscriptionDeadLetterRecord> getDeadLetters(String subscriptionName, int limit) {
        return deadLetterRepository.findDeadLetters(subscriptionName, limit);
    }

    @Transactional(readOnly = true)
    public Optional<EventSubscriptionDeadLetterRecord> getDeadLetterById(long id) {
        return deadLetterRepository.findDeadLetterById(id);
    }

    @Transactional
    public boolean replayDeadLetter(long deadLetterId) {
        EventSubscriptionDeadLetterRecord record = deadLetterRepository.findDeadLetterById(deadLetterId)
                .orElseThrow(() -> new NoSuchElementException("Dead letter record not found for ID: " + deadLetterId));

        AsyncEventHandler targetHandler = eventHandlers.stream()
                .filter(h -> h.getSubscriptionName().equals(record.subscriptionName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active AsyncEventHandler registered for subscription: " + record.subscriptionName()));

        EventWithId<Event> eventWithId = eventRepository.findEventById(record.eventId())
                .orElseThrow(() -> new NoSuchElementException("Event not found for ID: " + record.eventId()));

        log.info("Replaying dead letter ID {} for subscription {}", deadLetterId, record.subscriptionName());
        targetHandler.handleEvent(eventWithId);

        boolean deleted = deadLetterRepository.deleteDeadLetterById(deadLetterId);
        log.info("Dead letter ID {} replayed successfully and removed from DLQ: {}", deadLetterId, deleted);
        return true;
    }
}
