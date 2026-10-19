package eventsourcing.postgresql.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventMetadata {

    UUID correlationId;
    UUID causationId;
    String traceparent;
    int schemaVersion;
    OffsetDateTime timestamp;
    Map<String, Object> properties;

    @JsonCreator
    public EventMetadata(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("causationId") UUID causationId,
            @JsonProperty("traceparent") String traceparent,
            @JsonProperty("schemaVersion") Integer schemaVersion,
            @JsonProperty("timestamp") OffsetDateTime timestamp,
            @JsonProperty("properties") Map<String, Object> properties
    ) {
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.traceparent = traceparent;
        this.schemaVersion = schemaVersion != null ? schemaVersion : 1;
        this.timestamp = timestamp != null ? timestamp : OffsetDateTime.now();
        this.properties = properties != null ? Map.copyOf(properties) : Collections.emptyMap();
    }

    public static EventMetadata empty() {
        return new EventMetadata(null, null, null, 1, OffsetDateTime.now(), Collections.emptyMap());
    }

    public static EventMetadata of(UUID correlationId, UUID causationId, String traceparent) {
        return new EventMetadata(correlationId, causationId, traceparent, 1, OffsetDateTime.now(), Collections.emptyMap());
    }

    public static EventMetadata of(UUID correlationId, UUID causationId, String traceparent, int schemaVersion) {
        return new EventMetadata(correlationId, causationId, traceparent, schemaVersion, OffsetDateTime.now(), Collections.emptyMap());
    }
}
