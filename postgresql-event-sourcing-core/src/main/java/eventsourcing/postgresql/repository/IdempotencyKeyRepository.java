package eventsourcing.postgresql.repository;

import eventsourcing.postgresql.domain.command.IdempotencyKeyRecord;
import eventsourcing.postgresql.domain.command.IdempotencyStatus;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Transactional(propagation = Propagation.MANDATORY)
@Repository
@RequiredArgsConstructor
public class IdempotencyKeyRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Optional<IdempotencyKeyRecord> findByKey(String key) {
        return jdbcTemplate.query("""
                        SELECT KEY, COMMAND_TYPE, AGGREGATE_ID, STATUS, REQUEST_HASH, RESPONSE_BODY, STATUS_CODE, CREATED_AT
                          FROM ES_IDEMPOTENCY_KEY
                         WHERE KEY = :key
                        """,
                Map.of("key", key),
                this::toRecord
        ).stream().findFirst();
    }

    public boolean tryAcquireLease(String key, String commandType, UUID aggregateId, String requestHash) {
        int inserted = jdbcTemplate.update("""
                        INSERT INTO ES_IDEMPOTENCY_KEY (KEY, COMMAND_TYPE, AGGREGATE_ID, STATUS, REQUEST_HASH)
                        VALUES (:key, :commandType, :aggregateId, 'STARTED', :requestHash)
                        ON CONFLICT (KEY) DO NOTHING
                        """,
                Map.of(
                        "key", key,
                        "commandType", commandType,
                        "aggregateId", aggregateId,
                        "requestHash", requestHash != null ? requestHash : ""
                ));
        return inserted == 1;
    }

    public void complete(String key, String responseBody, int statusCode) {
        String responseJson = StringUtils.hasText(responseBody) ? responseBody : "{}";
        jdbcTemplate.update("""
                        UPDATE ES_IDEMPOTENCY_KEY
                           SET STATUS = 'COMPLETED',
                               RESPONSE_BODY = :responseBody::jsonb,
                               STATUS_CODE = :statusCode
                         WHERE KEY = :key
                        """,
                Map.of(
                        "key", key,
                        "responseBody", responseJson,
                        "statusCode", statusCode
                ));
    }

    public void releaseLease(String key) {
        jdbcTemplate.update("""
                        DELETE FROM ES_IDEMPOTENCY_KEY
                         WHERE KEY = :key AND STATUS = 'STARTED'
                        """,
                Map.of("key", key));
    }

    public void save(IdempotencyKeyRecord record) {
        String responseJson = StringUtils.hasText(record.responseBody()) ? record.responseBody() : "{}";
        String statusStr = record.status() != null ? record.status().name() : IdempotencyStatus.COMPLETED.name();
        jdbcTemplate.update("""
                        INSERT INTO ES_IDEMPOTENCY_KEY (KEY, COMMAND_TYPE, AGGREGATE_ID, STATUS, REQUEST_HASH, RESPONSE_BODY, STATUS_CODE)
                        VALUES (:key, :commandType, :aggregateId, :status, :requestHash, :responseBody::jsonb, :statusCode)
                        ON CONFLICT (KEY) DO UPDATE
                           SET STATUS = EXCLUDED.STATUS,
                               REQUEST_HASH = EXCLUDED.REQUEST_HASH,
                               RESPONSE_BODY = EXCLUDED.RESPONSE_BODY,
                               STATUS_CODE = EXCLUDED.STATUS_CODE
                        """,
                Map.of(
                        "key", record.key(),
                        "commandType", record.commandType(),
                        "aggregateId", record.aggregateId(),
                        "status", statusStr,
                        "requestHash", record.requestHash() != null ? record.requestHash() : "",
                        "responseBody", responseJson,
                        "statusCode", record.statusCode() != null ? record.statusCode() : 200
                ));
    }

    private IdempotencyKeyRecord toRecord(ResultSet rs, int rowNum) throws SQLException {
        String key = rs.getString("KEY");
        String commandType = rs.getString("COMMAND_TYPE");
        UUID aggregateId = rs.getObject("AGGREGATE_ID", UUID.class);
        String statusStr = rs.getString("STATUS");
        IdempotencyStatus status = statusStr != null ? IdempotencyStatus.valueOf(statusStr) : IdempotencyStatus.COMPLETED;
        String requestHash = rs.getString("REQUEST_HASH");
        PGobject json = (PGobject) rs.getObject("RESPONSE_BODY");
        Integer statusCode = rs.getObject("STATUS_CODE", Integer.class);
        OffsetDateTime createdAt = rs.getObject("CREATED_AT", OffsetDateTime.class);
        return new IdempotencyKeyRecord(key, commandType, aggregateId, status, requestHash, json != null ? json.getValue() : "{}", statusCode, createdAt);
    }
}
