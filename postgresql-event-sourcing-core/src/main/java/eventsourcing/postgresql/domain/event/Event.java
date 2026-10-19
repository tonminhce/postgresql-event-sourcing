package eventsourcing.postgresql.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString
public abstract class Event {

    protected final UUID aggregateId;
    protected final int version;
    protected final OffsetDateTime createdDate = OffsetDateTime.now();

    @Setter
    @JsonIgnore
    protected EventMetadata metadata;

    @Nonnull
    public abstract String getEventType();
}
