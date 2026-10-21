package com.example.eventsourcing;

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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class CommandIdempotencyIntegrationTests extends AbstractContainerBaseTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnCachedResponseOnDuplicateIdempotencyKey() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        String payload1 = """
                {
                  "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                  "price":"100.00",
                  "route":[]
                }
                """;

        // First call
        ResponseEntity<String> response1 = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload1, headers),
                String.class
        );
        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode json1 = objectMapper.readTree(response1.getBody());
        String orderId1 = json1.get("orderId").asText();

        // Second call with identical Idempotency-Key and payload (simulating real network retry)
        ResponseEntity<String> response2 = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload1, headers),
                String.class
        );
        assertThat(response2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response2.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        JsonNode json2 = objectMapper.readTree(response2.getBody());
        String orderId2 = json2.get("orderId").asText();

        // Must return identical cached orderId
        assertThat(orderId2).isEqualTo(orderId1);

        // Verify in database that only ONE order aggregate exists with this orderId
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ES_AGGREGATE WHERE ID = ?",
                Integer.class,
                UUID.fromString(orderId1)
        );
        assertThat(count).isEqualTo(1);

        // Verify record exists in ES_IDEMPOTENCY_KEY
        Integer idempCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ES_IDEMPOTENCY_KEY WHERE KEY = ?",
                Integer.class,
                idempotencyKey
        );
        assertThat(idempCount).isEqualTo(1);
    }

    @Test
    void shouldReturn422WhenReusingIdempotencyKeyWithDifferentPayload() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        String payload1 = """
                {
                  "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                  "price":"100.00",
                  "route":[]
                }
                """;

        ResponseEntity<String> response1 = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload1, headers),
                String.class
        );
        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();

        // Second call with identical Idempotency-Key but different price (payload tampering)
        String payload2 = """
                {
                  "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                  "price":"999.00",
                  "route":[]
                }
                """;

        ResponseEntity<String> response2 = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload2, headers),
                String.class
        );
        assertThat(response2.getStatusCode().value()).isEqualTo(422);
        JsonNode json = objectMapper.readTree(response2.getBody());
        assertThat(json.get("detail").asText()).isEqualTo("Idempotency-Key reused with different payload");
        assertThat(json.get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_MISMATCH");
    }

    @Test
    void shouldReturn409ConflictWhenRequestIsCurrentlyInProgress() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        // Simulate an in-flight lease already held in DB
        jdbcTemplate.update("""
                INSERT INTO ES_IDEMPOTENCY_KEY (KEY, COMMAND_TYPE, AGGREGATE_ID, STATUS, REQUEST_HASH)
                VALUES (?, 'PlaceOrderCommand', ?, 'STARTED', 'somehash')
                """, idempotencyKey, UUID.randomUUID());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        String payload = """
                {
                  "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                  "price":"100.00",
                  "route":[]
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("detail").asText()).contains("Request is currently being processed");
    }

    @Test
    void shouldNotAppendEventOnDuplicateIdempotencyKeyWhenModifyingOrder() throws Exception {
        // The Idempotency-Key namespace is global: one key identifies one command, so the
        // order placement and the modification have to use different keys.
        HttpHeaders placeHeaders = jsonHeaders(UUID.randomUUID().toString());
        HttpHeaders headers = jsonHeaders(UUID.randomUUID().toString());

        ResponseEntity<String> placeResponse = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>("""
                        {
                          "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                          "price":"100.00",
                          "route":[]
                        }
                        """, placeHeaders),
                String.class
        );
        assertThat(placeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        String orderId = objectMapper.readTree(placeResponse.getBody()).get("orderId").asText();

        String acceptPayload = """
                {
                  "status":"ACCEPTED",
                  "driverId":"2c068a1a-9263-433f-a70b-067d51b98378"
                }
                """;

        ResponseEntity<String> response1 = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>(acceptPayload, headers),
                String.class
        );
        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();

        // Second call with identical Idempotency-Key and payload (simulating real network retry)
        ResponseEntity<String> response2 = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>(acceptPayload, headers),
                String.class
        );
        assertThat(response2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response2.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");

        // The replay must not append a second OrderAcceptedEvent
        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ES_EVENT e JOIN ES_AGGREGATE a ON a.ID = e.AGGREGATE_ID WHERE a.ID = ?",
                Integer.class,
                UUID.fromString(orderId)
        );
        assertThat(eventCount).isEqualTo(2);

        // The order projection is still at the version of the first call
        ResponseEntity<String> getResponse = restTemplate.getForEntity("/orders/" + orderId, String.class);
        assertThat(getResponse.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode order = objectMapper.readTree(getResponse.getBody());
        assertThat(order.get("version").asInt()).isEqualTo(2);
        assertThat(order.get("status").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void shouldReturn422WhenReusingIdempotencyKeyWithDifferentPayloadWhenModifyingOrder() throws Exception {
        HttpHeaders placeHeaders = jsonHeaders(UUID.randomUUID().toString());
        HttpHeaders headers = jsonHeaders(UUID.randomUUID().toString());

        ResponseEntity<String> placeResponse = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>("""
                        {
                          "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                          "price":"100.00",
                          "route":[]
                        }
                        """, placeHeaders),
                String.class
        );
        assertThat(placeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        String orderId = objectMapper.readTree(placeResponse.getBody()).get("orderId").asText();

        ResponseEntity<String> response1 = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>("""
                        {
                          "status":"ACCEPTED",
                          "driverId":"2c068a1a-9263-433f-a70b-067d51b98378"
                        }
                        """, headers),
                String.class
        );
        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();

        // Same Idempotency-Key but a different payload (payload tampering)
        ResponseEntity<String> response2 = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>("""
                        {
                          "status":"ACCEPTED",
                          "driverId":"6e2f0c1a-1b3d-4a6e-9c2f-1d4b8e7a5c33"
                        }
                        """, headers),
                String.class
        );
        assertThat(response2.getStatusCode().value()).isEqualTo(422);
        JsonNode json = objectMapper.readTree(response2.getBody());
        assertThat(json.get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_MISMATCH");
    }

    private HttpHeaders jsonHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    @Test
    void shouldReturn201WithLocationHeaderWhenOrderIsPlaced() throws Exception {
        HttpHeaders headers = jsonHeaders(UUID.randomUUID().toString());

        ResponseEntity<String> response = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>("""
                        {
                          "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                          "price":"100.00",
                          "route":[]
                        }
                        """, headers),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        String orderId = objectMapper.readTree(response.getBody()).get("orderId").asText();
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/orders/" + orderId);
    }

    @Test
    void shouldReturnProblemDetailsWhenOrderIsNotFound() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/orders/" + UUID.randomUUID(),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("status").asInt()).isEqualTo(404);
        assertThat(json.get("title").asText()).isEqualTo("Resource not found");
        assertThat(json.get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(json.get("detail").asText()).contains("Order not found for ID");
        assertThat(json.get("instance").asText()).startsWith("/orders/");
    }

    @Test
    void shouldReturnUpdatedOrderWhenOrderIsModified() throws Exception {
        HttpHeaders placeHeaders = jsonHeaders(UUID.randomUUID().toString());
        HttpHeaders headers = jsonHeaders(UUID.randomUUID().toString());

        ResponseEntity<String> placeResponse = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>("""
                        {
                          "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                          "price":"100.00",
                          "route":[]
                        }
                        """, placeHeaders),
                String.class
        );
        assertThat(placeResponse.getStatusCode().value()).isEqualTo(201);
        String orderId = objectMapper.readTree(placeResponse.getBody()).get("orderId").asText();

        ResponseEntity<String> modifyResponse = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>("""
                        {
                          "status":"ACCEPTED",
                          "driverId":"2c068a1a-9263-433f-a70b-067d51b98378"
                        }
                        """, headers),
                String.class
        );

        assertThat(modifyResponse.getStatusCode().value()).isEqualTo(200);
        JsonNode order = objectMapper.readTree(modifyResponse.getBody());
        assertThat(order.get("id").asText()).isEqualTo(orderId);
        assertThat(order.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(order.get("version").asInt()).isEqualTo(2);
    }
}
