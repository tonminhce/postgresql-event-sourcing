package eventsourcing.postgresql.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import eventsourcing.postgresql.domain.event.EventUpcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
public class EventUpcasterChain {

    private final List<EventUpcaster> upcasters;

    public EventUpcasterChain(List<EventUpcaster> upcasters) {
        this.upcasters = upcasters.stream()
                .sorted(Comparator.comparingInt(EventUpcaster::getTargetVersion))
                .toList();
    }

    public JsonNode upcast(String eventType, int currentVersion, JsonNode eventJson) {
        int version = currentVersion;
        JsonNode result = eventJson;

        for (EventUpcaster upcaster : upcasters) {
            if (upcaster.canUpcast(eventType, version)) {
                log.debug("Upcasting {} from version {} to {}",
                        eventType, version, upcaster.getTargetVersion());
                result = upcaster.upcast(result);
                version = upcaster.getTargetVersion();
            }
        }
        return result;
    }
}
