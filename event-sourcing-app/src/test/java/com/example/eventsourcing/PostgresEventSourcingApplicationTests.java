package com.example.eventsourcing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import static com.example.eventsourcing.PostgresEventSourcingApplicationE2ETests.E2E_TESTING_ENV_VAR;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
abstract class PostgresEventSourcingApplicationTests extends AbstractContainerBaseTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void orderTestScript() throws Exception {
        // The Kafka container is shared by all the test classes of this JVM, so every test class
        // needs its own consumer group. Otherwise the second class to run resumes from the
        // offsets committed by the first one and does not see the events it has just published.
        new OrderTestScript(restTemplate, KAFKA.getBootstrapServers(), getClass().getSimpleName() + "-consumer").execute();
    }
}
