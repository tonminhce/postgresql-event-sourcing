package eventsourcing.postgresql.service.command;

import eventsourcing.postgresql.domain.command.IdempotencyKeyRecord;
import eventsourcing.postgresql.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional(readOnly = true)
    public Optional<IdempotencyKeyRecord> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return idempotencyKeyRepository.findByKey(key);
    }

    @Transactional
    public void record(IdempotencyKeyRecord record) {
        if (record.key() == null || record.key().isBlank()) {
            return;
        }
        idempotencyKeyRepository.save(record);
    }
}
