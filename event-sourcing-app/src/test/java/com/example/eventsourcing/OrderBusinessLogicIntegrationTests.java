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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class OrderBusinessLogicIntegrationTests extends AbstractContainerBaseTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String placeTestOrder(BigDecimal price) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String placePayload = """
                {
                  "riderId":"%s",
                  "price":"%s",
                  "route":[]
                }
                """.formatted(UUID.randomUUID(), price);

        ResponseEntity<String> placeResponse = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(placePayload, headers),
                String.class
        );
        assertThat(placeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        return objectMapper.readTree(placeResponse.getBody()).get("orderId").asText();
    }

    @Test
    void shouldRejectOrderPlacementWithNonPositivePrice() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = """
                {
                  "riderId":"7f9217e7-c2ed-4919-9e7f-9ca5c2d9dd83",
                  "price":"-10.00",
                  "route":[]
                }
                """;

        ResponseEntity<String> response = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(UNPROCESSABLE_CONTENT);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(json.get("detail").asText()).contains("Price must be strictly positive");
    }

    @Test
    void shouldRejectDriverAcceptingOwnRide() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String riderId = UUID.randomUUID().toString();
        String placePayload = """
                {
                  "riderId":"%s",
                  "price":"150.00",
                  "route":[]
                }
                """.formatted(riderId);

        ResponseEntity<String> placeResponse = restTemplate.exchange(
                "/orders",
                HttpMethod.POST,
                new HttpEntity<>(placePayload, headers),
                String.class
        );
        assertThat(placeResponse.getStatusCode().is2xxSuccessful()).isTrue();
        String orderId = objectMapper.readTree(placeResponse.getBody()).get("orderId").asText();

        // Attempt to accept order with the same driverId as riderId
        String acceptPayload = """
                {
                  "status":"ACCEPTED",
                  "driverId":"%s"
                }
                """.formatted(riderId);

        ResponseEntity<String> acceptResponse = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>(acceptPayload, headers),
                String.class
        );

        assertThat(acceptResponse.getStatusCode()).isEqualTo(UNPROCESSABLE_CONTENT);
        JsonNode json = objectMapper.readTree(acceptResponse.getBody());
        assertThat(json.get("detail").asText()).isEqualTo("Driver cannot accept their own ride request");
    }

    @Test
    void shouldRejectMissingPayloadFieldsOnModifyOrder() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String orderId = placeTestOrder(new BigDecimal("100.00"));

        // Missing status
        ResponseEntity<String> noStatusResponse = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>("{}", headers),
                String.class
        );
        assertThat(noStatusResponse.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(objectMapper.readTree(noStatusResponse.getBody()).get("detail").asText())
                .isEqualTo("Field 'status' is required");

        // Missing price on ADJUSTED
        ResponseEntity<String> noPriceResponse = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>("{\"status\":\"ADJUSTED\"}", headers),
                String.class
        );
        assertThat(noPriceResponse.getStatusCode()).isEqualTo(UNPROCESSABLE_CONTENT);
        assertThat(objectMapper.readTree(noPriceResponse.getBody()).get("detail").asText())
                .isEqualTo("Field 'price' is required for ADJUSTED status");

        // Missing driverId on ACCEPTED
        ResponseEntity<String> noDriverResponse = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>("{\"status\":\"ACCEPTED\"}", headers),
                String.class
        );
        assertThat(noDriverResponse.getStatusCode()).isEqualTo(UNPROCESSABLE_CONTENT);
        assertThat(objectMapper.readTree(noDriverResponse.getBody()).get("detail").asText())
                .isEqualTo("Field 'driverId' is required for ACCEPTED status");
    }

    @Test
    void shouldRejectCrossResourceReplayOfIdempotencyKey() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String order1 = placeTestOrder(new BigDecimal("100.00"));
        String order2 = placeTestOrder(new BigDecimal("100.00"));

        String idempotencyKey = UUID.randomUUID().toString();
        HttpHeaders idempHeaders = new HttpHeaders();
        idempHeaders.setContentType(MediaType.APPLICATION_JSON);
        idempHeaders.set("Idempotency-Key", idempotencyKey);

        String acceptPayload = """
                {
                  "status":"ACCEPTED",
                  "driverId":"%s"
                }
                """.formatted(UUID.randomUUID());

        // First call on order 1 succeeds
        ResponseEntity<String> res1 = restTemplate.exchange(
                "/orders/" + order1,
                HttpMethod.PUT,
                new HttpEntity<>(acceptPayload, idempHeaders),
                String.class
        );
        assertThat(res1.getStatusCode().is2xxSuccessful()).isTrue();

        // Attempt to replay same idempotency key on order 2 must return 409 Conflict
        ResponseEntity<String> res2 = restTemplate.exchange(
                "/orders/" + order2,
                HttpMethod.PUT,
                new HttpEntity<>(acceptPayload, idempHeaders),
                String.class
        );
        assertThat(res2.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(objectMapper.readTree(res2.getBody()).get("detail").asText())
                .isEqualTo("Idempotency-Key already used for a different order");
    }

    @Test
    void shouldSupportIdempotencyOnPutOrders() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String orderId = placeTestOrder(new BigDecimal("200.00"));

        String idempotencyKey = UUID.randomUUID().toString();
        HttpHeaders putHeaders = new HttpHeaders();
        putHeaders.setContentType(MediaType.APPLICATION_JSON);
        putHeaders.set("Idempotency-Key", idempotencyKey);

        String acceptPayload = """
                {
                  "status":"ACCEPTED",
                  "driverId":"%s"
                }
                """.formatted(UUID.randomUUID());

        // First call
        ResponseEntity<String> response1 = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>(acceptPayload, putHeaders),
                String.class
        );
        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();

        // Second call (retry with same Idempotency-Key). Without idempotency, this would throw 400 Bad Request
        ResponseEntity<String> response2 = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>(acceptPayload, putHeaders),
                String.class
        );
        assertThat(response2.getStatusCode().is2xxSuccessful()).isTrue();

        // Check idempotency record stored in database
        Integer idempCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ES_IDEMPOTENCY_KEY WHERE KEY = ?",
                Integer.class,
                idempotencyKey
        );
        assertThat(idempCount).isEqualTo(1);
    }

    @Test
    void shouldAdjustPriceAndCompleteOrderViaApi() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String orderId = placeTestOrder(new BigDecimal("100.00"));

        // Adjust price to 210.00
        ResponseEntity<String> adjustResponse = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>("{\"status\":\"ADJUSTED\",\"price\":\"210.00\"}", headers),
                String.class
        );
        assertThat(adjustResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Accept order
        UUID driverId = UUID.randomUUID();
        ResponseEntity<String> acceptResponse = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>("{\"status\":\"ACCEPTED\",\"driverId\":\"" + driverId + "\"}", headers),
                String.class
        );
        assertThat(acceptResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Complete order
        ResponseEntity<String> completeResponse = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>("{\"status\":\"COMPLETED\"}", headers),
                String.class
        );
        assertThat(completeResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify status & price in read model
        String status = jdbcTemplate.queryForObject(
                "SELECT STATUS FROM RM_ORDER WHERE ID = ?",
                String.class,
                UUID.fromString(orderId)
        );
        BigDecimal price = jdbcTemplate.queryForObject(
                "SELECT PRICE FROM RM_ORDER WHERE ID = ?",
                BigDecimal.class,
                UUID.fromString(orderId)
        );
        assertThat(status).isEqualTo("COMPLETED");
        assertThat(price).isEqualByComparingTo(new BigDecimal("210.00"));
    }

    @Test
    void shouldCancelOrderWithReason() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String orderId = placeTestOrder(new BigDecimal("180.00"));

        String cancelPayload = """
                {
                  "status":"CANCELLED",
                  "reason":"Passenger changed departure time"
                }
                """;

        ResponseEntity<String> cancelResponse = restTemplate.exchange(
                "/orders/" + orderId,
                HttpMethod.PUT,
                new HttpEntity<>(cancelPayload, headers),
                String.class
        );
        assertThat(cancelResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify status in read model
        String status = jdbcTemplate.queryForObject(
                "SELECT STATUS FROM RM_ORDER WHERE ID = ?",
                String.class,
                UUID.fromString(orderId)
        );
        assertThat(status).isEqualTo("CANCELLED");
    }
}
