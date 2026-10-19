package eventsourcing.postgresql.domain.event;

import com.fasterxml.jackson.databind.JsonNode;

public interface EventUpcaster {

    /**
     * Checks if this upcaster can upgrade the event of the specified event type and schema version.
     */
    boolean canUpcast(String eventType, int currentSchemaVersion);

    /**
     * Transforms the event JSON payload from the current version to the target version.
     */
    JsonNode upcast(JsonNode eventJson);

    /**
     * Target schema version after applying this upcaster.
     */
    int getTargetVersion();
}
