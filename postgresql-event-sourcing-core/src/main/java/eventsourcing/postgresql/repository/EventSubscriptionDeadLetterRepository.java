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
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Transactional(propagation = Propagation.MANDATORY)
@Repository
@RequiredArgsConstructor
public class EventSubscriptionDeadLetterRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    public List<eventsourcing.postgresql.domain.event.EventSubscriptionDeadLetterRecord> findDeadLetters(
            String subscriptionName, int limit) {
        if (subscriptionName != null && !subscriptionName.isBlank()) {
            return jdbcTemplate.query("""
                            SELECT ID,
                                   SUBSCRIPTION_NAME,
                                   EVENT_ID,
                                   TRANSACTION_ID::text,
                                   AGGREGATE_TYPE,
                                   EVENT_TYPE,
                                   ERROR_MESSAGE,
                                   STACK_TRACE,
                                   RETRY_COUNT,
                                   CREATED_AT
                              FROM ES_EVENT_SUBSCRIPTION_DEAD_LETTER
                             WHERE SUBSCRIPTION_NAME = :subscriptionName
                             ORDER BY ID ASC
                             LIMIT :limit
                            """,
                    Map.of("subscriptionName", subscriptionName, "limit", limit),
                    this::toRecord);
        } else {
            return jdbcTemplate.query("""
                            SELECT ID,
                                   SUBSCRIPTION_NAME,
                                   EVENT_ID,
                                   TRANSACTION_ID::text,
                                   AGGREGATE_TYPE,
                                   EVENT_TYPE,
                                   ERROR_MESSAGE,
                                   STACK_TRACE,
                                   RETRY_COUNT,
                                   CREATED_AT
                              FROM ES_EVENT_SUBSCRIPTION_DEAD_LETTER
                             ORDER BY ID ASC
                             LIMIT :limit
                            """,
                    Map.of("limit", limit),
                    this::toRecord);
        }
    }

    public java.util.Optional<eventsourcing.postgresql.domain.event.EventSubscriptionDeadLetterRecord> findDeadLetterById(long id) {
        List<eventsourcing.postgresql.domain.event.EventSubscriptionDeadLetterRecord> result = jdbcTemplate.query("""
                        SELECT ID,
                               SUBSCRIPTION_NAME,
                               EVENT_ID,
                               TRANSACTION_ID::text,
                               AGGREGATE_TYPE,
                               EVENT_TYPE,
                               ERROR_MESSAGE,
                               STACK_TRACE,
                               RETRY_COUNT,
                               CREATED_AT
                          FROM ES_EVENT_SUBSCRIPTION_DEAD_LETTER
                         WHERE ID = :id
                        """,
                Map.of("id", id),
                this::toRecord);
        return result.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(result.get(0));
    }

    public boolean deleteDeadLetterById(long id) {
        int updated = jdbcTemplate.update("""
                        DELETE FROM ES_EVENT_SUBSCRIPTION_DEAD_LETTER
                         WHERE ID = :id
                        """,
                Map.of("id", id));
        return updated > 0;
    }

    private eventsourcing.postgresql.domain.event.EventSubscriptionDeadLetterRecord toRecord(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new eventsourcing.postgresql.domain.event.EventSubscriptionDeadLetterRecord(
                rs.getLong("ID"),
                rs.getString("SUBSCRIPTION_NAME"),
                rs.getLong("EVENT_ID"),
                new java.math.BigInteger(rs.getString("TRANSACTION_ID")),
                rs.getString("AGGREGATE_TYPE"),
                rs.getString("EVENT_TYPE"),
                rs.getString("ERROR_MESSAGE"),
                rs.getString("STACK_TRACE"),
                rs.getInt("RETRY_COUNT"),
                rs.getObject("CREATED_AT", java.time.OffsetDateTime.class)
        );
    }
}

