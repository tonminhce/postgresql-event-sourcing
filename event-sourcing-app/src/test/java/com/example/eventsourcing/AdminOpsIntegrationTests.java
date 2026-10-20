package com.example.eventsourcing;

import com.example.eventsourcing.service.event.OrderIntegrationEventSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class AdminOpsIntegrationTests extends AbstractContainerBaseTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRebuildOrderProjectionFromEventStream() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = """
                {
                  "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                  "price":"150.00",
                  "route":[]
                }
                """;

        ResponseEntity<String> placeResponse = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
        );
        assertThat(placeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode json = objectMapper.readTree(placeResponse.getBody());
        String orderId = json.get("orderId").asText();

        // Corrupt or alter the read model in RM_ORDER to simulate projection drift/failure
        jdbcTemplate.update("UPDATE RM_ORDER SET STATUS = 'CANCELLED', PRICE = 0.00 WHERE ID = ?", UUID.fromString(orderId));
        String alteredStatus = jdbcTemplate.queryForObject(
                "SELECT STATUS FROM RM_ORDER WHERE ID = ?", String.class, UUID.fromString(orderId));
        assertThat(alteredStatus).isEqualTo("CANCELLED");

        // Trigger projection rebuild via Admin API
        ResponseEntity<String> rebuildResponse = restTemplate.postForEntity(
                "/admin/projections/orders/" + orderId + "/rebuild",
                null,
                String.class
        );
        assertThat(rebuildResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify that the read model has been restored to PLACED by replaying event store
        String restoredStatus = jdbcTemplate.queryForObject(
                "SELECT STATUS FROM RM_ORDER WHERE ID = ?", String.class, UUID.fromString(orderId));
        assertThat(restoredStatus).isEqualTo("PLACED");

        BigDecimal restoredPrice = jdbcTemplate.queryForObject(
                "SELECT PRICE FROM RM_ORDER WHERE ID = ?", BigDecimal.class, UUID.fromString(orderId));
        assertThat(restoredPrice).isEqualByComparingTo("150.00");

        // Also test bulk rebuild endpoint
        ResponseEntity<String> bulkRebuildResponse = restTemplate.postForEntity(
                "/admin/projections/orders/rebuild",
                null,
                String.class
        );
        assertThat(bulkRebuildResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode bulkJson = objectMapper.readTree(bulkRebuildResponse.getBody());
        assertThat(bulkJson.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(bulkJson.get("rebuiltCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldListAndManageDeadLetterQueue() throws Exception {
        // Place an order to ensure at least one event exists
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = """
                {
                  "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                  "price":"200.00",
                  "route":[]
                }
                """;

        ResponseEntity<String> placeResponse = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
        );
        assertThat(placeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode json = objectMapper.readTree(placeResponse.getBody());
        UUID orderId = UUID.fromString(json.get("orderId").asText());

        Long eventId = jdbcTemplate.queryForObject(
                "SELECT ID FROM ES_EVENT WHERE AGGREGATE_ID = ? LIMIT 1",
                Long.class,
                orderId
        );
        assertThat(eventId).isNotNull();

        String transactionIdStr = jdbcTemplate.queryForObject(
                "SELECT TRANSACTION_ID::text FROM ES_EVENT WHERE ID = ?",
                String.class,
                eventId
        );

        // Insert a poison pill record into ES_EVENT_SUBSCRIPTION_DEAD_LETTER
        String subscriptionName = OrderIntegrationEventSender.class.getName();
        jdbcTemplate.update("""
                INSERT INTO ES_EVENT_SUBSCRIPTION_DEAD_LETTER
                    (SUBSCRIPTION_NAME, EVENT_ID, TRANSACTION_ID, AGGREGATE_TYPE, EVENT_TYPE, ERROR_MESSAGE, STACK_TRACE, RETRY_COUNT)
                VALUES
                    (?, ?, ?::xid8, 'ORDER', 'OrderPlacedEvent', 'Simulated failure', 'Test Stacktrace', 3)
                """, subscriptionName, eventId, transactionIdStr);

        // Fetch DLQ entries via Admin API
        ResponseEntity<String> dlqResponse = restTemplate.getForEntity(
                "/admin/subscriptions/dlq?subscriptionName=" + subscriptionName,
                String.class
        );
        assertThat(dlqResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode dlqList = objectMapper.readTree(dlqResponse.getBody());
        assertThat(dlqList.isArray()).isTrue();
        assertThat(dlqList.size()).isGreaterThanOrEqualTo(1);

        long dlqId = dlqList.get(0).get("id").asLong();

        // Replay the DLQ event via Admin API
        ResponseEntity<String> replayResponse = restTemplate.postForEntity(
                "/admin/subscriptions/dlq/" + dlqId + "/replay",
                null,
                String.class
        );
        assertThat(replayResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify the dead letter has been removed from the DLQ
        Integer remainingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ES_EVENT_SUBSCRIPTION_DEAD_LETTER WHERE ID = ?",
                Integer.class,
                dlqId
        );
        assertThat(remainingCount).isEqualTo(0);
    }

    @Test
    void shouldListDeadLettersWithoutSubscriptionNameFilter() throws Exception {
        // Place an order to ensure at least one event exists
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = """
                {
                  "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                  "price":"200.00",
                  "route":[]
                }
                """;

        ResponseEntity<String> placeResponse = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
        );
        assertThat(placeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode json = objectMapper.readTree(placeResponse.getBody());
        UUID orderId = UUID.fromString(json.get("orderId").asText());

        Long eventId = jdbcTemplate.queryForObject(
                "SELECT ID FROM ES_EVENT WHERE AGGREGATE_ID = ? LIMIT 1",
                Long.class,
                orderId
        );
        assertThat(eventId).isNotNull();

        String transactionIdStr = jdbcTemplate.queryForObject(
                "SELECT TRANSACTION_ID::text FROM ES_EVENT WHERE ID = ?",
                String.class,
                eventId
        );

        String subscriptionName = OrderIntegrationEventSender.class.getName();
        jdbcTemplate.update("""
                INSERT INTO ES_EVENT_SUBSCRIPTION_DEAD_LETTER
                    (SUBSCRIPTION_NAME, EVENT_ID, TRANSACTION_ID, AGGREGATE_TYPE, EVENT_TYPE, ERROR_MESSAGE, STACK_TRACE, RETRY_COUNT)
                VALUES
                    (?, ?, ?::xid8, 'ORDER', 'OrderPlacedEvent', 'Simulated failure', 'Test Stacktrace', 3)
                """, subscriptionName, eventId, transactionIdStr);

        // Fetch DLQ entries without the optional subscriptionName filter
        ResponseEntity<String> dlqResponse = restTemplate.getForEntity(
                "/admin/subscriptions/dlq",
                String.class
        );
        assertThat(dlqResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode dlqList = objectMapper.readTree(dlqResponse.getBody());
        assertThat(dlqList.isArray()).isTrue();
        assertThat(dlqList.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldReturnNotFoundWhenRebuildingProjectionOfUnknownOrder() {
        ResponseEntity<String> rebuildResponse = restTemplate.postForEntity(
                "/admin/projections/orders/" + UUID.randomUUID() + "/rebuild",
                null,
                String.class
        );
        assertThat(rebuildResponse.getStatusCode()).isEqualTo(NOT_FOUND);
    }
}
