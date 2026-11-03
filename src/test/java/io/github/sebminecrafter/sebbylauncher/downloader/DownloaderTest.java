package io.github.sebminecrafter.sebbylauncher.downloader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloaderTest {
    @Test
    void parsesLatestQuiltLoaderVersionFromMavenMetadata() throws IOException {
        String metadata = """
                <metadata>
                  <versioning>
                    <latest>0.30.2</latest>
                    <release>0.30.2</release>
                  </versioning>
                </metadata>
                """;

        assertEquals("0.30.2", Downloader.parseLatestQuiltLoaderVersion(metadata));
    }

    @Test
    void rejectsMavenMetadataWithoutLatestQuiltLoaderVersion() {
        assertThrows(IOException.class, () -> Downloader.parseLatestQuiltLoaderVersion(
                "<metadata><versioning><release>0.30.2</release></versioning></metadata>"));
    }

    @Test
    void upgradesOldAsmForJava25() {
        VersionMeta.Library oldAsm = library("org.ow2.asm:asm:9.5");

        VersionMeta.Library compatibleAsm = Downloader.upgradeAsmForJavaVersion(oldAsm, 25);

        assertEquals("org.ow2.asm:asm:9.11.1", compatibleAsm.name());
        assertEquals("org/ow2/asm/asm/9.11.1/asm-9.11.1.jar",
                compatibleAsm.downloads().artifact().path());
        assertEquals("https://repo1.maven.org/maven2/org/ow2/asm/asm/9.11.1/asm-9.11.1.jar",
                compatibleAsm.downloads().artifact().url());
    }

    @Test
    void leavesCompatibleAsmAndOlderJavaUnchanged() {
        VersionMeta.Library compatibleAsm = library("org.ow2.asm:asm:9.8");
        VersionMeta.Library oldAsm = library("org.ow2.asm:asm:9.5");

        assertSame(compatibleAsm, Downloader.upgradeAsmForJavaVersion(compatibleAsm, 25));
        assertSame(oldAsm, Downloader.upgradeAsmForJavaVersion(oldAsm, 24));
    }

    @Test
    void quiltAsmOverrideReplacesOlderSameModuleInsteadOfAppending() {
        VersionMeta.Library oldAsm = library("org.ow2.asm:asm:9.5");
        VersionMeta.Library compatibleAsm = library("org.ow2.asm:asm:9.11.1");
        List<VersionMeta.Library> libraries = new ArrayList<>(List.of(oldAsm));

        Downloader.mergeLoaderLibrary(libraries, compatibleAsm, true);

        assertEquals(List.of(compatibleAsm), libraries);
    }

    @Test
    void quiltAsmOverrideKeepsAlreadyCompatibleBaseDependency() {
        VersionMeta.Library baseAsm = library("org.ow2.asm:asm:9.8");
        VersionMeta.Library loaderAsm = library("org.ow2.asm:asm:9.11.1");
        List<VersionMeta.Library> libraries = new ArrayList<>(List.of(baseAsm));

        Downloader.mergeLoaderLibrary(libraries, loaderAsm, true);

        assertEquals(List.of(baseAsm), libraries);
    }

    private static VersionMeta.Library library(String name) {
        String[] coordinates = name.split(":");
        String path = coordinates[0].replace('.', '/') + "/" + coordinates[1] + "/"
                + coordinates[2] + "/" + coordinates[1] + "-" + coordinates[2] + ".jar";
        VersionMeta.Artifact artifact = new VersionMeta.Artifact(path, null, 0, "https://example.test/" + path);
        return new VersionMeta.Library(
                name, new VersionMeta.LibDownloads(artifact, Map.of()), null, null, null);
    }
}
