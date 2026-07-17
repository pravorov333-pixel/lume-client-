# Third-Party Notices

Lume Visuals bundles the following third-party software. Each is used
unmodified, as a separate component (a Fabric mod jar, an embedded font, or
a library), never statically merged into Lume's own source code.

## Bundled performance mods (Fabric, `resources/perf-mods/`)

### Sodium
- **Author:** CaffeineMC
- **License:** PolyForm Shield License 1.0.0 — <https://polyformproject.org/licenses/shield/1.0.0>
- **Source:** <https://github.com/CaffeineMC/sodium>
- Distributed unmodified. This license permits any use except building a
  product that competes with Sodium itself (a chunk-rendering optimizer);
  Lume is a HUD/cosmetics/QoL client and does not compete with it.

### Lithium
- **Author:** CaffeineMC
- **License:** GNU Lesser General Public License v3.0 (LGPL-3.0-only)
- **Source:** <https://github.com/CaffeineMC/lithium>
- Distributed unmodified, as a separate mod jar (dynamically loaded by
  Fabric Loader, not compiled into Lume's own classes) — satisfies LGPL's
  requirement that the library remain separately replaceable.

### ImmediatelyFast
- **Author:** Bawnorton, contributors
- **License:** GNU Lesser General Public License v3.0 (LGPL-3.0)
- **Source:** <https://github.com/RaphiMC/ImmediatelyFast> (or current upstream)
- Distributed unmodified, as a separate mod jar — same LGPL basis as Lithium above.

### FerriteCore
- **Author:** malte0811
- **License:** MIT License
- **Source:** <https://github.com/malte0811/FerriteCore>

### Iris
- **Author:** IrisShaders (coderbot, IMS212, Justsnoopy30, FoundationGames)
- **License:** GNU Lesser General Public License v3.0 (LGPL-3.0-only)
- **Source:** <https://github.com/IrisShaders/Iris>
- Distributed unmodified, as a separate mod jar — same LGPL basis as Lithium/ImmediatelyFast above.
  Adds OptiFine-format shader pack support; Lume auto-selects whatever pack the user drops
  into its shaderpacks folder and exposes a single on/off toggle in the ClickGUI.

## Modding platform (compiled into the Lume Client mod)

### Fabric Loader / Fabric API
- **Author:** FabricMC
- **License:** Apache License 2.0
- **Source:** <https://github.com/FabricMC>

### LWJGL (incl. the NanoVG binding used for Lume's UI rendering)
- **Author:** LWJGL3 contributors
- **License:** BSD 3-Clause License
- **Source:** <https://github.com/LWJGL/lwjgl3>

## Launcher runtime (Electron app)

### Electron
- **License:** MIT License
- **Source:** <https://github.com/electron/electron>

### minecraft-launcher-core
- **License:** MIT License
- **Source:** <https://github.com/Pierce01/MinecraftLauncher-core>

## Fonts (embedded in the Lume Client mod, `assets/lume/font/`)

All bundled fonts are licensed under the SIL Open Font License 1.1
(<https://scripts.sil.org/OFL>), which explicitly permits embedding in
software and commercial redistribution:

- **Poppins** — Indian Type Foundry
- **Noto Sans** — Google
- **Open Sans** — Google / Steve Matteson
- **PT Sans** — ParaType
- **Roboto** — Google
- **JetBrains Mono** — JetBrains

## Minecraft itself

Lume Visuals does not bundle or redistribute Minecraft. The launcher
downloads the game client directly from Mojang's own official servers
using the player's own Microsoft/Mojang account, the same mechanism any
third-party launcher (MultiMC, Prism, etc.) uses.
