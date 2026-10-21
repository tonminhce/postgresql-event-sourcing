package com.example.eventsourcing;

import com.example.eventsourcing.error.ErrorType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ErrorCatalogEndpointTests extends AbstractContainerBaseTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeTheErrorCatalog() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/errors", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode catalog = objectMapper.readTree(response.getBody());
        assertThat(catalog.isArray()).isTrue();
        assertThat(catalog).hasSize(ErrorType.values().length);

        Map<String, JsonNode> entriesByCode = new HashMap<>();
        catalog.forEach(entry -> entriesByCode.put(entry.get("code").asText(), entry));

        for (ErrorType type : ErrorType.values()) {
            JsonNode entry = entriesByCode.get(type.code());
            assertThat(entry).as("the catalog must contain the error %s", type.code()).isNotNull();
            assertThat(entry.get("status").asInt()).isEqualTo(type.status().value());
            assertThat(entry.get("title").asText()).isEqualTo(type.title());
            assertThat(entry.get("type").asText()).endsWith("#" + type.slug());
        }
    }
}
