package eventsourcing.postgresql.repository;

import eventsourcing.postgresql.domain.event.Event;
import eventsourcing.postgresql.domain.event.EventWithId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

@Transactional(propagation = Propagation.MANDATORY)
@Repository
@RequiredArgsConstructor
public class EventSubscriptionDeadLetterRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void saveDeadLetter(String subscriptionName,
                               String aggregateType,
                               EventWithId<Event> eventWithId,
                               Throwable error,
                               int retryCount) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        error.printStackTrace(pw);

        jdbcTemplate.update("""
                        INSERT INTO ES_EVENT_SUBSCRIPTION_DEAD_LETTER
                            (SUBSCRIPTION_NAME, EVENT_ID, TRANSACTION_ID, AGGREGATE_TYPE, EVENT_TYPE, ERROR_MESSAGE, STACK_TRACE, RETRY_COUNT)
                        VALUES
                            (:subscriptionName, :eventId, :transactionId::xid8, :aggregateType, :eventType, :errorMessage, :stackTrace, :retryCount)
                        """,
                Map.of(
                        "subscriptionName", subscriptionName,
                        "eventId", eventWithId.id(),
                        "transactionId", eventWithId.transactionId().toString(),
                        "aggregateType", aggregateType,
                        "eventType", eventWithId.event().getEventType(),
                        "errorMessage", error.getMessage() != null ? error.getMessage() : error.getClass().getName(),
                        "stackTrace", sw.toString(),
                        "retryCount", retryCount
                ));
    }
}
