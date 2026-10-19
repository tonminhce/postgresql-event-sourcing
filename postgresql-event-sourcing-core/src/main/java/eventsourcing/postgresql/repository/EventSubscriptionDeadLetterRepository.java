package eventsourcing.postgresql.repository;

import eventsourcing.postgresql.domain.event.Event;
import eventsourcing.postgresql.domain.event.EventSubscriptionDeadLetterRecord;
import eventsourcing.postgresql.domain.event.EventWithId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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

    public List<EventSubscriptionDeadLetterRecord> findDeadLetters(String subscriptionName, int limit) {
        String sql = """
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
                 WHERE (:subscriptionName IS NULL OR SUBSCRIPTION_NAME = :subscriptionName)
                 ORDER BY ID ASC
                 LIMIT :limit
                """;

        String nameParam = (subscriptionName != null && !subscriptionName.isBlank()) ? subscriptionName : null;
        var params = new MapSqlParameterSource()
                .addValue("subscriptionName", nameParam)
                .addValue("limit", limit);

        return jdbcTemplate.query(sql, params, this::toRecord);
    }

    public Optional<EventSubscriptionDeadLetterRecord> findDeadLetterById(long id) {
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
                         WHERE ID = :id
                        """,
                Map.of("id", id),
                this::toRecord
        ).stream().findFirst();
    }

    public boolean deleteDeadLetterById(long id) {
        int updated = jdbcTemplate.update("""
                        DELETE FROM ES_EVENT_SUBSCRIPTION_DEAD_LETTER
                         WHERE ID = :id
                        """,
                Map.of("id", id));
        return updated > 0;
    }

    private EventSubscriptionDeadLetterRecord toRecord(ResultSet rs, int rowNum) throws SQLException {
        return new EventSubscriptionDeadLetterRecord(
                rs.getLong("ID"),
                rs.getString("SUBSCRIPTION_NAME"),
                rs.getLong("EVENT_ID"),
                new BigInteger(rs.getString("TRANSACTION_ID")),
                rs.getString("AGGREGATE_TYPE"),
                rs.getString("EVENT_TYPE"),
                rs.getString("ERROR_MESSAGE"),
                rs.getString("STACK_TRACE"),
                rs.getInt("RETRY_COUNT"),
                rs.getObject("CREATED_AT", OffsetDateTime.class)
        );
    }
}
