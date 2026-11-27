# Genshin UI Themes (Fabric 1.21.7, 26.2, 26.3)

Re-skins every Minecraft GUI with character art, in the style of HD character UI packs, but generated
on the fly from **any image you choose**, with a per-UI crop editor.

| What gets themed | How |
|---|---|
| Inventory, chests, crafting, furnaces, shulker/dispenser/hopper, creative tabs, anvil, smithing, grindstone, stonecutter, loom, cartography, enchanting, brewing, beacon, villager, horse | Art crop composited into an HD (4×) texture. The vanilla GUI palette is the key: panel → veiled art, slots → tinted art windows, bevels/outline → theme frame |
| Hotbar, selection, offhand slot, XP bar | Drawn in the theme colors over a strip of the art, with a glowing selection frame |
| Hearts, hunger, armor | Smooth HD hearts in the theme color; hunger becomes themed icons (moon / petal / drop / leaf / star) |
| Buttons, text fields, sliders, tooltips | Translucent dark panels with accent borders and diamond ornaments |
| Title / menu background | The theme art, slowly drifting (replaces the panorama) |
| Container labels | Recolored for readability on dark art |
| Animation | Themed particles (feathers, petals, bubbles, leaves, sparks) drift inside inventories and over the hotbar |

## Using it

* Press **U** in game (rebindable), or click **✦ Genshin UI** at the top-left of the title or pause menu.
* Select a theme → **Apply** (double-click works too). **Turn Off** restores vanilla.
* **New from Image...** (or drag & drop an image onto the window) creates a theme: colors are picked automatically
  and the crop editor opens.
* **Wallpaper Engine...** finds your Steam workshop folder (`steamapps/workshop/content/431960`) and shows your
  wallpapers with their previews. Click one and its art is unpacked from `scene.pkg` (layers come in as separate
  images: background, character, etc.). In the file picker you can also choose a `scene.pkg` directly.
  Video wallpapers only provide their preview image.
* **Edit / Crop...**: pick a UI on the left, then drag the crop box on the art (scroll or drag a corner to zoom).
  The box is locked to that UI's shape, and the preview below shows the exact result. Each UI can use a different
  image (**Add Art Image...**, then **Art: image N**). Colors, light/dark mode, particles, hunger icons, panel veil
  and slot tint are on the right.

Themes live in `.minecraft/config/genshinui/themes/<id>/` (`theme.json` + images). Applying a theme writes the
generated pack to `resourcepacks/GenshinUI-Theme` and enables it on top, so it shows in the Resource Packs screen too.
`config/genshinui/settings.json` has `textureScale` (default 4; use 2 for low VRAM), `particles`, `menuArt`, and
`recolorLabels`.

## Building

```
gradlew build                # jar in build/libs (bundles sample-themes/ if present)
gradlew build -PnoSamples    # same, without the sample art (safe to share)
gradlew installToPrism       # copies it into the "Genshin UI 1.21.7" Prism Launcher instance
gradlew installToLauncher    # copies it to the official launcher's fabric-loader-1.21.7 profile
gradlew previewThemes        # renders theme textures + mock screens to build/theme-preview, no game needed
gradlew runClientGameTest    # boots the game, applies themes, saves screenshots to build/run/clientGameTest/screenshots
```

Requires Fabric API. Minecraft 1.21.7 needs Fabric Loader ≥ 0.16 and Java 21; 26.2 and 26.3 need Loader ≥ 0.19 and Java 25.

### Project layout

| Path | Contents |
|---|---|
| `src/common/java` | Minecraft-independent code shared by every version: theme model, compositor, pack builder, Wallpaper Engine reader |
| `src/main`, `src/gametest` | Minecraft **1.21.7** (Yarn names). Built by the root project. |
| `versions/26x` | Minecraft **26.x** code (Mojang names, new GUI extractor API), shared by 26.2 and 26.3 |
| `versions/26.2`, `versions/26.3` | Standalone builds for each version, plus tiny per-version compat classes |

Build the 26.x versions with `-p`, e.g. `gradlew -p versions/26.3 build` (jar in `versions/26.3/build/libs`).
`runClientGameTest` and `-PnoSamples` work the same way there.

## Sample themes / art extraction

`sample-themes/` (Columbina, Yae Miko, Furina, Nahida) was made from Wallpaper Engine wallpapers with
`tools/we_extract.py`, which unpacks `scene.pkg` / `.tex` files:

```
python tools/we_extract.py "D:\SteamLibrary\steamapps\workshop\content\431960\<id>" out_dir --min-size 1000
```

If present at build time, these get bundled into the jar and installed on first launch, with Columbina active.
The art belongs to its creators and HoYoverse, so `sample-themes/` is git-ignored. Keep it for personal use and
don't publish a jar that contains it unless you have permission.
