package com.example.eventsourcing;

import eventsourcing.postgresql.metrics.EventSourcingMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class EventSourcingMetricsIntegrationTests extends AbstractContainerBaseTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private EventSourcingMetrics metrics;

    @Test
    void shouldExposeActuatorAndPrometheusMetrics() {
        // Verify health endpoint
        ResponseEntity<String> healthResponse = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(healthResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(healthResponse.getBody()).contains("\"status\":\"UP\"");

        // Place an order to trigger event store append and metrics recording
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = """
                {
                  "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                  "price":"99.50",
                  "route":[]
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify that the append timer recorded the operation
        var timer = meterRegistry.find("event_sourcing_append_duration_seconds")
                .tag("aggregate_type", "ORDER")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isGreaterThanOrEqualTo(1);

        // Record a simulated OCC conflict and DLQ event to verify micrometer counters
        metrics.recordOccConflict("ORDER");
        var occCounter = meterRegistry.find("event_sourcing_occ_conflicts_total")
                .tag("aggregate_type", "ORDER")
                .counter();
        assertThat(occCounter).isNotNull();
        assertThat(occCounter.count()).isGreaterThanOrEqualTo(1.0);

        metrics.recordDlqEvent("order-events", "ORDER");
        var dlqCounter = meterRegistry.find("event_sourcing_dlq_events_total")
                .tag("subscription", "order-events")
                .counter();
        assertThat(dlqCounter).isNotNull();
        assertThat(dlqCounter.count()).isGreaterThanOrEqualTo(1.0);

        // Query prometheus scraping endpoint
        ResponseEntity<String> prometheusResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(prometheusResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(prometheusResponse.getBody())
                .contains("event_sourcing_append_duration_seconds")
                .contains("event_sourcing_occ_conflicts_total")
                .contains("event_sourcing_dlq_events_total");
    }
}
