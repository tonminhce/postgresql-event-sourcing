package eventsourcing.postgresql.service.command;

import eventsourcing.postgresql.domain.command.IdempotencyKeyRecord;
import eventsourcing.postgresql.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional(readOnly = true)
    public Optional<IdempotencyKeyRecord> get(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        return idempotencyKeyRepository.findByKey(key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquireLease(String key, String commandType, UUID aggregateId, String requestHash) {
        if (!StringUtils.hasText(key)) {
            return true;
        }
        return idempotencyKeyRepository.tryAcquireLease(key, commandType, aggregateId, requestHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, String responseBody, int statusCode) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        idempotencyKeyRepository.complete(key, responseBody, statusCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseLease(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        idempotencyKeyRepository.releaseLease(key);
    }

    @Transactional
    public void record(IdempotencyKeyRecord record) {
        if (record == null || !StringUtils.hasText(record.key())) {
            return;
        }
        idempotencyKeyRepository.save(record);
    }
}
