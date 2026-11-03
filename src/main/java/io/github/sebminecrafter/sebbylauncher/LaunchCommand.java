package io.github.sebminecrafter.sebbylauncher;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.github.sebminecrafter.sebbylauncher.downloader.VersionMeta;
import io.github.sebminecrafter.sebbylogger.Logger;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the command line that starts a Minecraft version.
 */
@SuppressWarnings("unused")
public final class LaunchCommand {
    private static final Logger logger = new Logger("SebbyLauncher/LaunchCommand");
    private static final Gson GSON = new Gson();
    private static final Type RULES_TYPE = new TypeToken<List<VersionMeta.Rule>>() {
    }.getType();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Prevent instantiation
     */
    private LaunchCommand() {
        throw new UnsupportedOperationException(
                "Cannot be instantiated.");
    }

    /**
     * Builds the full command (java, JVM arguments, main class, game arguments).
     *
     * @param meta       version JSON of the version to start
     * @param session    account to start the game with
     * @param gameDir    the game's working directory (saves, options, ...)
     * @param nativesDir directory for native libraries
     * @param classpath  library JARs followed by the client JAR
     * @return the command, ready for a {@link ProcessBuilder}
     * @throws UnsupportedOperationException for versions that predate the
     *                                       {@code arguments} format
     */
    public static List<String> build(VersionMeta meta, Session session, Path gameDir, Path nativesDir,
            List<Path> classpath) {
        return build(meta, session, gameDir, nativesDir, classpath, 2048, "");
    }

    public static List<String> build(VersionMeta meta, Session session, Path gameDir, Path nativesDir,
            List<Path> classpath,
            int memoryMb, String extraJvmArgs) {
        return build(meta, session, gameDir, nativesDir, classpath, memoryMb, extraJvmArgs,
                Path.of(System.getProperty("java.home"), "bin", "java"));
    }

    public static List<String> build(VersionMeta meta, Session session, Path gameDir, Path nativesDir,
            List<Path> classpath, int memoryMb, String extraJvmArgs, Path javaExecutable) {
        Map<String, String> vars = variables(meta, session, gameDir, nativesDir, classpath);

        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-Xmx" + Math.max(memoryMb, 512) + "M");
        command.addAll(parseJvmArgs(extraJvmArgs));

        if (meta.arguments() != null) {
            command.addAll(resolve(meta.arguments().jvm(), vars));
            if (command.stream().noneMatch(
                    arg -> arg.contains("${classpath}") || arg.equals("-cp") || arg.startsWith("-classpath"))) {
                command.add("-Djava.library.path=" + nativesDir);
                command.add("-cp");
                command.add(classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
            }
            command.add(meta.mainClass());
            command.addAll(resolve(meta.arguments().game(), vars));
            return command;
        }

        if (meta.minecraftArguments() == null || meta.minecraftArguments().isBlank()) {
            throw new UnsupportedOperationException("Version format is not supported: " + meta.id());
        }

        command.add("-Djava.library.path=" + nativesDir);
        command.add("-cp");
        command.add(classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        command.add(meta.mainClass());
        command.addAll(parseLegacyArguments(meta.minecraftArguments(), vars));
        return command;
    }

    /**
     * Copies a command with the access token hidden, so it is safe to log.
     */
    public static List<String> redact(List<String> command) {
        List<String> copy = new ArrayList<>(command);
        int i = copy.indexOf("--accessToken");
        if (i >= 0 && i + 1 < copy.size())
            copy.set(i + 1, "***");
        for (i = 1; i < copy.size(); i++) {
            if (copy.get(i).startsWith("token:")) copy.set(i, "***");
        }
        return copy;
    }

    private static Map<String, String> variables(VersionMeta meta, Session session, Path gameDir, Path nativesDir,
            List<Path> classpath) {
        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", session.name());
        vars.put("auth_uuid", session.uuid());
        vars.put("auth_access_token", session.accessToken());
        vars.put("auth_session", "token:" + session.accessToken() + ":" + session.uuid());
        vars.put("auth_xuid", "");
        vars.put("user_properties", "{}");
        vars.put("clientid", "");
        vars.put("user_type", session.userType() == null ? "msa" : session.userType());
        vars.put("version_name", meta.id());
        vars.put("version_type", meta.type());
        vars.put("game_directory", gameDir.toString());
        vars.put("game_assets", LauncherDirs.ASSETS.resolve("resources").toString());
        vars.put("assets_root", LauncherDirs.ASSETS.toString());
        vars.put("assets_index_name", meta.assetIndex().id());
        vars.put("natives_directory", nativesDir.toString());
        vars.put("launcher_name", LauncherDetails.NAME);
        vars.put("launcher_version", LauncherDetails.VERSION);
        vars.put("classpath", classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        return vars;
    }

    /**
     * Turns argument entries into plain strings: drops entries whose rules don't
     * apply and fills in placeholders.
     */
    private static List<String> resolve(List<JsonElement> args, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        if (args == null)
            return out;

        for (JsonElement arg : args) {
            if (arg.isJsonPrimitive()) {
                out.add(substitute(arg.getAsString(), vars));
                continue;
            }

            JsonObject obj = arg.getAsJsonObject();
            List<VersionMeta.Rule> rules = GSON.fromJson(obj.get("rules"), RULES_TYPE);
            if (!VersionMeta.Rule.allowed(rules))
                continue;

            JsonElement value = obj.get("value");
            if (value.isJsonArray()) {
                for (JsonElement v : value.getAsJsonArray())
                    out.add(substitute(v.getAsString(), vars));
            } else {
                out.add(substitute(value.getAsString(), vars));
            }
        }
        return out;
    }

    private static String substitute(String arg, Map<String, String> vars) {
        return PLACEHOLDER.matcher(arg).replaceAll(m -> {
            String value = vars.get(m.group(1));
            if (value == null) {
                logger.warn("Unknown placeholder: " + m.group());
                return Matcher.quoteReplacement(m.group());
            }
            return Matcher.quoteReplacement(value);
        });
    }

    private static List<String> parseLegacyArguments(String argString, Map<String, String> vars) {
        if (argString == null || argString.isBlank())
            return List.of();

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < argString.length(); i++) {
            char c = argString.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || argString.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(substitute(current.toString(), vars));
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }

        if (!current.isEmpty()) {
            tokens.add(substitute(current.toString(), vars));
        }
        return tokens;
    }

    private static List<String> parseJvmArgs(String extraJvmArgs) {
        if (extraJvmArgs == null || extraJvmArgs.isBlank()) {
            return List.of();
        }

        return Arrays.stream(extraJvmArgs.trim().split("\\s+"))
                .filter(arg -> !arg.isBlank())
                .toList();
    }
}
