package eventsourcing.postgresql.service.command;

import eventsourcing.postgresql.domain.command.IdempotencyKeyRecord;
import eventsourcing.postgresql.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

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

    @Transactional
    public void record(IdempotencyKeyRecord record) {
        if (record == null || !StringUtils.hasText(record.key())) {
            return;
        }
        idempotencyKeyRepository.save(record);
    }
}
