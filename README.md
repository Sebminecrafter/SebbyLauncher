# SebbyLauncher

by Sebminecrafter

> [!WARNING]
> SebbyLauncher is in early development, is missing major features, and may not be usable. \
> If you encounter any bugs, please feel free to share them to the
> [GitHub Issues](https://github.com/Sebminecrafter/SebbyLauncher/issues) \
> Feel free to contribute though!

## A simple Minecraft launcher written fully in Java

The goal of SebbyLauncher is to be simple, fast, and open-source.

> Not all of these features, if any, are implemented yet.

It features:

* Version selection
* Separate instance folders (configurable)
* Mod loaders
* Selectable Fabric and Quilt loader versions per instance
* Mod downloading and updating through Modrinth, filtered for an instance's Minecraft version and loader

For Quilt profiles targeting Java 25 or newer, SebbyLauncher upgrades outdated ASM dependencies when needed to support newer Minecraft class files.

Open an instance's **Manage mods** dialog to search for compatible mods, install them to its `mods` folder,
check for updates, and update selected or all tracked mods. Mod update tracking is kept in the instance's
`mods` folder; mods installed outside SebbyLauncher are not tracked for updates.

### Tests

Run the unit tests with `mvn test`.
