# TODO

## 1. Instances and versions

- [X] Show instances on the Play page and let the user pick one to launch.
- [X] Add a New Instance dialog for name, Minecraft version, loader, and image.
- [X] Keep each instance's version and settings with that instance.
- [X] Add instance options for memory, JVM args, and game folder.

## 2. Support older Minecraft versions

- [X] Handle the old launch arguments used before 1.13.
- [X] Download and use the right Mojang Java runtime for older games.
- [X] Check older version JSONs and support their native libraries.

## 3. Mod loaders

- [X] Add Fabric first: merge its profile with vanilla version data and resolve libraries from Maven coordinates.
- [ ] Add Quilt next; it uses a similar setup.
- [ ] Add Forge and NeoForge later; they need installer support.

## 4. Mods

- [ ] Download and update mods in each instance's `mods` folder using Modrinth.
- [ ] Filter results by Minecraft version and loader.
- [ ] Send a unique `User-Agent` with Modrinth API requests. 
  (User agent: `SebbyLauncher/version`, use placeholders from )

## 5. Multiple accounts

- [X] Store each account's tokens separately.
- [X] Add an account picker and show the selected account's name.
- [X] Support adding offline accounts and Microsoft accounts.

## 6. Finish the other pages

- [ ] **News:** show Minecraft news if practical; add Home and Copy URL buttons.
- [ ] **Accounts:** show account heads, skins, and capes; account login options can build on the account store above.
- [ ] **Settings:** add theme/color options, resource cleanup, custom version JSON loading, instance update checks, and settings import/export.
- [ ] **Play:** Add sidebar icons and polish the instance grid and its options panel, make sure to change version and mod loader selector to be typing + dropdown combo

## 7. Improvements

- [ ] Selecting custom runtimes
