# SebbyLauncher

by Sebminecrafter

> [!WARNING]
> SebbyLauncher is in early development.
> If you encounter any bugs, please feel free to share them to the
> [GitHub Issues](https://github.com/Sebminecrafter/SebbyLauncher/issues),
> and feel free to contribute!

## A simple Minecraft launcher written fully in Java

The goal of SebbyLauncher is to be simple, fast, and open-source.

It features:

* Version selection
* Separate instance folders (configurable)
* Mod loaders
* Selectable Fabric and Quilt loader versions per instance
* Mod and pack downloading through Modrinth, filtered for an instance's Minecraft version

### Mods and packs management

Open an instance's **Manage mods & packs** dialog to browse, install, update, enable, disable, and remove mods,
resource packs, data packs, and shader packs. Each category has its own tab and uses the corresponding folder
inside the instance game directory (`mods`, `resourcepacks`, `datapacks`, or `shaderpacks`).
Installed files are identified directly by their SHA-1 hashes with Modrinth.

### Tests

Run the unit tests with `mvn test`.
