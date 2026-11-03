UI todo:

The logo+title would get moved into the left tab bit,
which would have tabs for:
 - Play/library (Instances)
 - News (News from minecraft.net, if possible, otherwise just launcher updates)
 - Accounts (Skins, account management).
 - Settings (a bunch of simple/common settings)

Example layout for sidebar/overall frame:
(all the items on the sidebar would have images next to them)
```
 ______________________________________________________________________________ 
|SebbyLauncher                                                    | - | [] | X |
|______________________________________________________________________________|
|SebbyLauncher |                                                               |
|Play          |                                                               |
|News          |                                                               |
|Accounts      |                                                               |
|              |                                                               |
|              |                       Coming Soon!                            |
|              |                                                               |
|              |                                                               |
|              |                                                               |
|              |                                                               |
|              |                                                               |
|Settings      |                                                               |
|______________|_______________________________________________________________|
```

Play page:
just a grid with squares for each instance (customizable image for each),
with option bar on the right for currently selected instance
(big play button, other buttons below it for mod/version management, like prism),
double-clicking an instance launches it,
add instance button which opens page to select version, mod loader, name, and image,
also an advanced settings on the left for stuff like memory and custom jvm args
(would also be in instance settings),
account dropdown selector with IGNs and head icons

News page:
shows the minecraft.net news, could just be an embedded browser,
with a home button to go back to the original url (it's `https://www.minecraft.net/article`),
copy button to copy the current url for sharing

Accounts page:
shows all active accounts in a short top bar, with heads next to IGNs,
button to add account which shows picker for offline account (enter username) or login or enter token,
big skin/cape swapper with previously used skins, and capes the selected account owns,
with a 3d model

Settings page:
A simple list of settings (maybe with sections if it gets long enough).
Main settings would be:
 - Style/theme (swing builtins)
 - Color?
 - Delete saved resources (libraries/assets/client jars, opens small selector window and asks confirmation)
 - Load custom version.json
 - Check all instances for updates
 - Import/export settings
 - Anything else common or useful/needed here

All of these could be in separate Java files (to make the code nicer and more readable), and the current Window code could be mostly rewritten.