package io.github.sebminecrafter.sebbylauncher.downloader;

import java.util.List;
import java.util.Map;

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
        Map<String, DownloadInfo> downloads,
        List<Library> libraries
) {
    public record DownloadInfo(String sha1, long size, String url) {}

    public record AssetIndexRef(String id, String sha1, long size, String url) {}

    public record Artifact(String path, String sha1, long size, String url) {}

    public record LibDownloads(Artifact artifact) {}

    public record OsRule(String name, String arch) {}

    public record Rule(String action, OsRule os) {
        /**
         * Whether this rule applies to the current OS and architecture.
         */
        boolean matches() {
            if (os == null) return true;
            if (os.name() != null && !os.name().equals(Platform.os())) return false;
            return os.arch() == null || os.arch().equals(Platform.arch());
        }
    }

    public record Library(String name, LibDownloads downloads, List<Rule> rules) {
        /**
         * Whether this library is needed on the current platform.
         * No rules means always; otherwise the last matching rule wins.
         */
        public boolean appliesHere() {
            if (rules == null || rules.isEmpty()) return true;
            boolean allowed = false;
            for (Rule r : rules) {
                if (r.matches()) allowed = r.action().equals("allow");
            }
            return allowed;
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
