package io.github.sebminecrafter.sebbylauncher.downloader;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Mojang's asset index: maps asset names (sounds, languages, icons) to hashed files.
 * {@code virtual} and {@code mapToResources} mark old layouts that need extra copying.
 */
public record AssetIndex(
        Map<String, AssetObject> objects,
        boolean virtual,
        @SerializedName("map_to_resources") boolean mapToResources
) {
    public record AssetObject(String hash, long size) {}
}
