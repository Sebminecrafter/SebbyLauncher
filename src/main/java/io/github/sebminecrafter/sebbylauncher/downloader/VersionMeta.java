package io.github.sebminecrafter.sebbylauncher.downloader;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The parts of Mojang's version JSON needed to download and launch a version.
 * Fields missing from the JSON are null.
 */
public record VersionMeta(
        String id,
        String type,
        String mainClass,
        String assets,
        AssetIndexRef assetIndex,
        JavaVersion javaVersion,
        Arguments arguments,
        Map<String, DownloadInfo> downloads,
        List<Library> libraries
) {
    public record DownloadInfo(String sha1, long size, String url) {}

    public record AssetIndexRef(String id, String sha1, long size, String url) {}

    public record Artifact(String path, String sha1, long size, String url) {}

    public record LibDownloads(Artifact artifact) {}

    public record JavaVersion(String component, int majorVersion) {}

    /**
     * Launch arguments (1.13 and newer). Each entry is either a plain string
     * or an object with {@code rules} and a {@code value} (string or array).
     */
    public record Arguments(List<JsonElement> game, List<JsonElement> jvm) {}

    public record OsRule(String name, String arch, String version) {}

    public record Rule(String action, OsRule os, Map<String, Boolean> features) {
        /**
         * Whether a list of rules allows something on the current platform.
         * No rules means always; otherwise the last matching rule wins.
         */
        public static boolean allowed(List<Rule> rules) {
            if (rules == null || rules.isEmpty()) return true;
            boolean allowed = false;
            for (Rule r : rules) {
                if (r.matches()) allowed = r.action().equals("allow");
            }
            return allowed;
        }

        /**
         * Whether this rule applies to the current OS, architecture and OS version.
         * The launcher has no optional features enabled, so rules requiring one never match.
         */
        boolean matches() {
            if (features != null && features.containsValue(true)) return false;
            if (os == null) return true;
            if (os.name() != null && !os.name().equals(Platform.os())) return false;
            if (os.arch() != null && !os.arch().equals(Platform.arch())) return false;
            return os.version() == null
                    || Pattern.compile(os.version()).matcher(System.getProperty("os.version")).find();
        }
    }

    public record Library(String name, LibDownloads downloads, List<Rule> rules) {
        /**
         * Whether this library is needed on the current platform.
         */
        public boolean appliesHere() {
            return Rule.allowed(rules);
        }
    }

    /**
     * Current platform, named the way Mojang names it.
     */
    static final class Platform {
        private Platform() {}

        /**
         * Gets the os name, according to how it's named in a Minecraft version JSON file
         * @return windows, osx or linux
         */
        static String os() {
            String n = System.getProperty("os.name").toLowerCase();
            if (n.startsWith("win")) return "windows";
            if (n.contains("mac")) return "osx";
            return "linux";
        }

        /**
         * Gets the os architecture, according to how it's named in a Minecraft version JSON file
         * @return x86, arm64 or x86_64
         */
        static String arch() {
            String a = System.getProperty("os.arch").toLowerCase();
            if (a.contains("aarch64") || a.contains("arm64")) return "arm64";
            if (a.equals("x86") || a.equals("i386") || a.equals("i686")) return "x86";
            return "x86_64";
        }
    }
}
