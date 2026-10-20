package eventsourcing.postgresql.repository;

import eventsourcing.postgresql.domain.event.EventSubscriptionCheckpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

@Transactional(propagation = Propagation.MANDATORY)
@Repository
@RequiredArgsConstructor
public class EventSubscriptionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void createSubscriptionIfAbsent(String subscriptionName) {
        jdbcTemplate.update("""
                        INSERT INTO ES_EVENT_SUBSCRIPTION (SUBSCRIPTION_NAME, LAST_TRANSACTION_ID, LAST_EVENT_ID)
                        VALUES (:subscriptionName, '0'::xid8, 0)
                        ON CONFLICT DO NOTHING
                        """,
                Map.of("subscriptionName", subscriptionName)
        );
    }

    public Optional<EventSubscriptionCheckpoint> readCheckpointAndLockSubscription(String subscriptionName) {
        return jdbcTemplate.query("""
                        SELECT LAST_TRANSACTION_ID::text,
                               LAST_EVENT_ID
                          FROM ES_EVENT_SUBSCRIPTION
                         WHERE SUBSCRIPTION_NAME = :subscriptionName
                           FOR UPDATE SKIP LOCKED
                        """,
                Map.of("subscriptionName", subscriptionName),
                this::toEventSubscriptionCheckpoint
        ).stream().findFirst();
    }

    public void updateEventSubscription(String subscriptionName,
                                        BigInteger lastProcessedTransactionId,
                                        long lastProcessedEventId) {
        jdbcTemplate.update("""
                        UPDATE ES_EVENT_SUBSCRIPTION
                           SET LAST_TRANSACTION_ID = :lastProcessedTransactionId::xid8,
                               LAST_EVENT_ID = :lastProcessedEventId
                         WHERE SUBSCRIPTION_NAME = :subscriptionName
                        """,
                Map.of(
                        "subscriptionName", subscriptionName,
                        "lastProcessedTransactionId", lastProcessedTransactionId.toString(),
                        "lastProcessedEventId", lastProcessedEventId
                ));
    }

    public long calculateSubscriptionLag(String subscriptionName, String aggregateType) {
        Long lag = jdbcTemplate.queryForObject("""
                        SELECT COALESCE((SELECT MAX(e.ID) FROM ES_EVENT e JOIN ES_AGGREGATE a ON a.ID = e.AGGREGATE_ID WHERE a.AGGREGATE_TYPE = :aggregateType), 0)
                               - COALESCE(s.LAST_EVENT_ID, 0) AS LAG
                          FROM ES_EVENT_SUBSCRIPTION s
                         WHERE s.SUBSCRIPTION_NAME = :subscriptionName
                        """,
                Map.of(
                        "subscriptionName", subscriptionName,
                        "aggregateType", aggregateType
                ),
                Long.class
        );
        return lag != null ? Math.max(0, lag) : 0L;
    }

    private EventSubscriptionCheckpoint toEventSubscriptionCheckpoint(ResultSet rs, int rowNum) throws SQLException {
        String lastProcessedTransactionId = rs.getString("LAST_TRANSACTION_ID");
        long lastProcessedEventId = rs.getLong("LAST_EVENT_ID");
        return new EventSubscriptionCheckpoint(new BigInteger(lastProcessedTransactionId), lastProcessedEventId);
    }
}
