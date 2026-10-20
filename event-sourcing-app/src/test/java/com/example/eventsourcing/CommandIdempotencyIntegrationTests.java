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
        assertThat(json.get("error").asText()).isEqualTo("Idempotency-Key reused with different payload");
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
        assertThat(json.get("error").asText()).contains("Request is currently being processed");
    }
}
