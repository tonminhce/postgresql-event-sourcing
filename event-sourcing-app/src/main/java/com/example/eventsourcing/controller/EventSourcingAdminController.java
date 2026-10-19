package com.example.eventsourcing.controller;

import com.example.eventsourcing.service.projection.OrderProjectionRebuildService;
import eventsourcing.postgresql.domain.event.EventSubscriptionDeadLetterRecord;
import eventsourcing.postgresql.service.EventSubscriptionDeadLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class EventSourcingAdminController {

    private final EventSubscriptionDeadLetterService deadLetterService;
    private final OrderProjectionRebuildService projectionRebuildService;

    @GetMapping("/subscriptions/dlq")
    public ResponseEntity<List<EventSubscriptionDeadLetterRecord>> listDeadLetters(
            @RequestParam(required = false) String subscriptionName,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(deadLetterService.getDeadLetters(subscriptionName, limit));
    }

    @PostMapping("/subscriptions/dlq/{id}/replay")
    public ResponseEntity<Map<String, Object>> replayDeadLetter(@PathVariable long id) {
        deadLetterService.replayDeadLetter(id);
        return ResponseEntity.ok(Map.of(
                "status", "REPLAYED",
                "id", id
        ));
    }

    @PostMapping("/projections/orders/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildAllOrderProjections() {
        int count = projectionRebuildService.rebuildAllOrders();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "rebuiltCount", count
        ));
    }

    @PostMapping("/projections/orders/{id}/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildSingleOrderProjection(@PathVariable UUID id) {
        projectionRebuildService.rebuildOrder(id);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "orderId", id
        ));
    }
}
