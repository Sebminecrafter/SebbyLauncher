package io.github.sebminecrafter.sebbylauncher;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class LaunchCommandTest {
    @Test
    void redactHidesAccessTokenAndReturnsACopy() {
        List<String> command = List.of("java", "--accessToken", "secret-token", "Main");

        List<String> redacted = LaunchCommand.redact(command);

        assertEquals(List.of("java", "--accessToken", "***", "Main"), redacted);
        assertNotSame(command, redacted);
        assertEquals("secret-token", command.get(2));
    }

    @Test
    void redactHidesLegacyTokenArgument() {
        assertEquals(
                List.of("java", "Main", "***"),
                LaunchCommand.redact(List.of("java", "Main", "token:secret:uuid")));
    }

    @Test
    void redactLeavesCommandsWithoutTokensUnchanged() {
        assertEquals(
                List.of("java", "-version"),
                LaunchCommand.redact(List.of("java", "-version")));
    }
}
