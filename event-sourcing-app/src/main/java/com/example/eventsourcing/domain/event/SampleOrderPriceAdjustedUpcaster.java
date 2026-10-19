package com.example.eventsourcing.domain.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eventsourcing.postgresql.domain.event.EventUpcaster;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SampleOrderPriceAdjustedUpcaster implements EventUpcaster {

    @Override
    public boolean canUpcast(String eventType, int currentSchemaVersion) {
        return EventType.ORDER_PRICE_ADJUSTED.toString().equals(eventType) && currentSchemaVersion == 1;
    }

    @Override
    public JsonNode upcast(JsonNode eventJson) {
        if (eventJson instanceof ObjectNode objectNode && objectNode.has("legacyPriceField")) {
            BigDecimal val = new BigDecimal(objectNode.get("legacyPriceField").asText());
            objectNode.put("newPrice", val);
            objectNode.remove("legacyPriceField");
        }
        return eventJson;
    }

    @Override
    public int getTargetVersion() {
        return 2;
    }
}
