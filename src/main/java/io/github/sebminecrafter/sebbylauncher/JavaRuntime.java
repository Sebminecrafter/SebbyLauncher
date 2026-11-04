package io.github.sebminecrafter.sebbylauncher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaRuntime {
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?m)^\\s*(?:openjdk|java)\\s+(?:version\\s+)?[\\\\\"']?(\\d+)(?:\\.(\\d+))?");

    private JavaRuntime() {}

    public static int featureVersion(Path executable) throws IOException {
        if (!Files.isRegularFile(executable)) {
            throw new IOException("Java executable does not exist: " + executable);
        }

        Path versionExecutable = versionProbeExecutable(executable);
        Process process;
        try {
            process = new ProcessBuilder(versionExecutable.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new IOException("Could not run Java executable: " + executable, e);
        }

        boolean finished;
        try {
            finished = process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking Java executable: " + executable, e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Timed out while checking Java executable: " + executable);
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException("Java executable returned an error: " + output.strip());
        }
        return parseFeatureVersion(output);
    }

    static int parseFeatureVersion(String output) throws IOException {
        Matcher matcher = VERSION_PATTERN.matcher(output);
        if (!matcher.find()) {
            throw new IOException("Could not determine Java version from command output.");
        }

        try {
            int first = Integer.parseInt(matcher.group(1));
            return first == 1 && matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : first;
        } catch (NumberFormatException e) {
            throw new IOException("Java version number is outside the supported range.", e);
        }
    }

    private static Path versionProbeExecutable(Path executable) {
        if (!executable.getFileName().toString().equalsIgnoreCase("javaw.exe")) {
            return executable;
        }
        Path java = executable.resolveSibling("java.exe");
        return Files.isRegularFile(java) ? java : executable;
    }
}
