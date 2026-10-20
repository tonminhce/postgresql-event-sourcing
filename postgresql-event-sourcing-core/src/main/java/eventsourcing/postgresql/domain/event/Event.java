package eventsourcing.postgresql.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@ToString
public abstract class Event {

    protected final UUID aggregateId;
    protected final int version;
    protected final OffsetDateTime createdDate;

    @Setter
    @JsonIgnore
    protected EventMetadata metadata;

    protected Event(UUID aggregateId, int version) {
        this(aggregateId, version, null);
    }

    protected Event(UUID aggregateId, int version, OffsetDateTime createdDate) {
        this.aggregateId = aggregateId;
        this.version = version;
        this.createdDate = createdDate != null ? createdDate : OffsetDateTime.now();
    }

    @Nonnull
    public abstract String getEventType();
}
