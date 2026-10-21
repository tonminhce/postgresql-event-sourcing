package com.example.eventsourcing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The properties of the API itself.
 */
@Configuration
@ConfigurationProperties(prefix = "api")
@Getter
@Setter
public class ApiProperties {

    /**
     * The base URL of the documentation of the error types.
     *
     * <p>The {@code type} member of a problem detail is this URL followed by the anchor
     * of the error, so that it identifies the problem type and points to its
     * documentation, as prescribed by RFC 9457. A deployment that hosts its own
     * documentation overrides it with {@code api.problem-type-docs-url}.
     */
    private String problemTypeDocsUrl =
            "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md";
}
