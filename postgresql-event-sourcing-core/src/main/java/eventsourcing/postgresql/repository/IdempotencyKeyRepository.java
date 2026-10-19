package eventsourcing.postgresql.repository;

import eventsourcing.postgresql.domain.command.IdempotencyKeyRecord;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
                        SELECT KEY, COMMAND_TYPE, AGGREGATE_ID, RESPONSE_BODY, STATUS_CODE, CREATED_AT
                          FROM ES_IDEMPOTENCY_KEY
                         WHERE KEY = :key
                        """,
                Map.of("key", key),
                this::toRecord
        ).stream().findFirst();
    }

    public void save(IdempotencyKeyRecord record) {
        String responseJson = (record.responseBody() != null && !record.responseBody().isBlank())
                ? record.responseBody()
                : "{}";
        jdbcTemplate.update("""
                        INSERT INTO ES_IDEMPOTENCY_KEY (KEY, COMMAND_TYPE, AGGREGATE_ID, RESPONSE_BODY, STATUS_CODE)
                        VALUES (:key, :commandType, :aggregateId, :responseBody::jsonb, :statusCode)
                        ON CONFLICT (KEY) DO NOTHING
                        """,
                Map.of(
                        "key", record.key(),
                        "commandType", record.commandType(),
                        "aggregateId", record.aggregateId(),
                        "responseBody", responseJson,
                        "statusCode", record.statusCode()
                ));
    }

    private IdempotencyKeyRecord toRecord(ResultSet rs, int rowNum) throws SQLException {
        String key = rs.getString("KEY");
        String commandType = rs.getString("COMMAND_TYPE");
        UUID aggregateId = rs.getObject("AGGREGATE_ID", UUID.class);
        PGobject json = (PGobject) rs.getObject("RESPONSE_BODY");
        int statusCode = rs.getInt("STATUS_CODE");
        OffsetDateTime createdAt = rs.getObject("CREATED_AT", OffsetDateTime.class);
        return new IdempotencyKeyRecord(key, commandType, aggregateId, json != null ? json.getValue() : "{}", statusCode, createdAt);
    }
}
