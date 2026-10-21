package com.example.eventsourcing.error;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the documentation of the error types against the drift of the catalog.
 *
 * <p>Every error of the catalog must be documented, and the documentation must not
 * document an error that does not exist anymore. The {@code type} member of a problem
 * detail points to the section of the error, so a missing section makes the response
 * point to nothing.
 */
class ErrorTypeDocumentationTests {

    private static final String DOCUMENTATION_PATH = "docs/api/errors.md";
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("<a id=\"([^\"]+)\"></a>");

    @Test
    void shouldDocumentEveryErrorType() throws IOException {
        String documentation = readDocumentation();

        for (ErrorType type : ErrorType.values()) {
            assertThat(documentation)
                    .as("the error %s must have a section in %s", type.code(), DOCUMENTATION_PATH)
                    .contains("<a id=\"" + type.slug() + "\"></a>");
        }
    }

    @Test
    void shouldNotDocumentUnknownErrorTypes() throws IOException {
        Set<String> documented = anchorsOf(readDocumentation());
        Set<String> catalog = Arrays.stream(ErrorType.values())
                .map(ErrorType::slug)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(documented)
                .as("%s must only document the errors of the catalog", DOCUMENTATION_PATH)
                .isEqualTo(catalog);
    }

    private String readDocumentation() throws IOException {
        return Files.readString(documentationFile());
    }

    private Set<String> anchorsOf(String documentation) {
        Set<String> anchors = new LinkedHashSet<>();
        Matcher matcher = ANCHOR_PATTERN.matcher(documentation);
        while (matcher.find()) {
            anchors.add(matcher.group(1));
        }
        return anchors;
    }

    /**
     * The tests run with the module directory as the working directory, so the root of
     * the repository is the first parent that holds the documentation.
     */
    private Path documentationFile() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(DOCUMENTATION_PATH);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot find " + DOCUMENTATION_PATH);
    }
}
