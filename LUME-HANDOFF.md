# Lume Client — Session Handoff / Context

> Paste-this-to-continue. In a new session say **"продолжаем Lume, читай LUME-HANDOFF.md"**.
> Last updated: 2026-06-24.

## What this is
**Lume Client** — a paid Minecraft **utility/QoL client** (inspired by Pulse Visuals but original), for anarchy servers (FunTime/HolyWorld). It is a **launcher (Electron) + Fabric mod**:
- Launcher: enter license key → it downloads MC + Fabric + the mod → launches the game (offline/TLauncher-style auth).
- In-game mod: glass ClickGUI (Right Shift) + HUD overlay.

**User profile:** beginner in Java/Gradle; explain simply, in Russian. Communicate in Russian.

## ⚠️ Scope boundary (important, agreed with user)
Build **legit only**: visuals/HUD/QoL/performance/cosmetics + launcher + license keys.
Do **NOT** build cheats (unfair multiplayer advantage / anti-cheat bypass): KillAura, Reach, AntiKB, Velocity, Hitbox, Timer, Scaffold, ESP/Chams-through-walls, X-Ray, AutoTotem/AutoPot, Freecam, AutoFish, etc. Rendering/design techniques are fine.

## Locations
- Mod: `C:\Users\popko\Desktop\claudecode\claudecode\LumeClient`
- Launcher: `C:\Users\popko\Desktop\claudecode\claudecode\LumeLauncher`
- Skill (general method): `~/.claude/skills/minecraft-glass-client/SKILL.md`
- Memory (project state): `<memory>/lume-client.md` (loaded automatically)

## Toolchain (installed)
JDK 21 Temurin `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` (JAVA_HOME set), IntelliJ IDEA Community, Git, Node 24 / npm 11.

## Versions (mod)
Fabric MC **1.21.1**, yarn 1.21.1+build.3, loader 0.16.10, fabric-api 0.116.12+1.21.1, loom 1.7-SNAPSHOT, Gradle 8.10.2, Java 21 (`release = 21`).

## Build + distribute (run after every mod change)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
cd C:\Users\popko\Desktop\claudecode\claudecode\LumeClient
.\gradlew.bat build --no-daemon --console=plain
# then copy the jar everywhere it's used:
$jar = "C:\Users\popko\Desktop\claudecode\claudecode\LumeClient\build\libs\lume-client-1.0.0.jar"
Copy-Item $jar "C:\Users\popko\Desktop\claudecode\claudecode\LumeLauncher\resources\lume-client.jar" -Force
Copy-Item $jar "$env:APPDATA\.minecraft\mods\lume-client-1.0.0.jar" -Force
Copy-Item $jar "$env:APPDATA\.lumeclient\mods\lume-client.jar" -Force
```
Launcher run: `node_modules\electron\dist\electron.exe .` in LumeLauncher (or `npm start`). Demo key: **LUME-TEST-2026-DEMO**.

## Testing loop (no in-game screenshot tool — rely on user)
- In-game changes apply only on a **fresh Play** (jar copied into game dir on Play). "Nothing changed" usually = user looked at an already-running instance. Always tell them: fully close MC → Play.
- Diagnose in-game via `System.out.println("[Lume] …")` then read `%APPDATA%\.lumeclient\logs\latest.log` after they launch.
- Launcher UI can be previewed: static server on `LumeLauncher/renderer` (.claude/launch.json "lume-launcher-preview", port 8123) + Claude Preview tools. **preview_screenshot does NOT reload — call preview_eval("location.reload()") first.**

## DESIGN (current, approved)
- **Cream base + LAVENDER accent.** Light = cream glass, dark = warm-dark-cream glass. Default light. Toggle in menu.
- **Liquid-glass** look: bright rim edge, soft shadow, frosted translucency (vanilla menu blur shows through), glow.
- **Native-resolution rendering** is THE key fix that made it crisp (not "minecrafty"): everything ×guiScale, matrix scaled 1/guiScale. Applied in ClickGuiScreen AND HudRenderer.
- **Custom font**: bundled `assets/lume/font/lume.ttf` = **Poppins-Medium**, rasterized via Java2D into a NativeImage atlas (LumeFont.java), `setFilter(true)` bilinear → smooth. RenderUtil.text/width route through it (fallback to vanilla TTF). Poppins has NO Cyrillic (UI is English).
- **Unified logo** (lavender "L" mark): in-game (RenderUtil.drawLogo), launcher (SVG), and desktop shortcut icon (`LumeLauncher/resources/lume.ico` + `lume.png`).
- **In-game GUI layout (unique, NOT Pulse/sidebar):** centered window; header = logo + Light/Dark toggle; full-width search; segmented category pill (active = lavender gradient); grid of module **cards** where the **whole card is the toggle**, name **centered**, enabled = lavender-tinted fill + glow + dot; hover = card lifts + white glow follows cursor.
- **Launcher**: cream bg + 3 lavender glow blobs drifting side-to-side (drift1/2/3, ~14–17s), lavender logo, **Activate/Play buttons = lavender gradient with lavender glow**.

## Premium animations + Target HUD (added 2026-06-24, after QoL/cosmetics)
- **Theme.java is now ANIMATED**: every colour getter crossfades prev→current mode over 360ms (smoothstep). `dark` field is private — use `Theme.isDark()` / `Theme.toggle()` / `Theme.setDark()`. Added `Theme.colorLerp(a,b,t)` (public ARGB lerp) + `isAnimating()`. Public getters blend `raw(prevDark)`↔`raw(dark)`; private `xxx(boolean d)` overloads hold the raw palette. GUI+HUD morph between themes automatically (getters queried per-frame).
- **ClickGuiScreen animations**: per-element state `Map<String,float[]> anim` = {hover, enable, press}, advanced each frame via `approach(cur,target,rate,dt)` (frame-rate independent, dt computed from lastFrame). Cards: smooth hover lift, fill brightens (colorLerp glassRow→glassHov→accent-tint by enable), enable glow ramps, on-dot scales/fades in. **Press pulse** = matrix shrink-and-spring (scale 1−0.06·press around card centre) + white flash; press set to 1f on click, decays. Theme button has same hover-glow + press pulse. Click handler triggers `animFor(name)[2]=1f`.
- **Scrollable module grid** (ClickGuiScreen): the card grid now scrolls (mouse wheel) — needed once a category has >8 modules (Visuals has 10 incl. Target HUD). Smooth `scroll`→`scrollTarget` (native px), `mouseScrolled` adjusts target, clamped to maxScroll in render. Grid clipped via `ctx.enableScissor(x/S, clipTop/S, (x+W)/S, clipBot/S)` — **scissor takes GUI-logical coords (NOT affected by the matrix), so divide native coords by S** (verified via bytecode: enableScissor just pushes a ScreenRect, no pose transform). Cards loop over ALL mods with `ry = gy+row*pitch - scroll`, cull off-screen, hover/click hit-tests gated to [clipTop,clipBot]. Lavender scrollbar drawn on the right when overflow. Scroll resets to 0 on category switch / search.
- **Contained hover glow** `RenderUtil.containedGlow(clipX,clipY,clipW,clipH, cx,cy, radius, rgb, intensity)`: strong radial glow centred on cursor but every layer CLAMPED to the clip rect → light stays inside the button (no scissor; coords clamped, clip inset by 2*S to dodge rounded corners). Replaced the old weak spilling white glow. Colour = white↔accent lerp, intensity = hover anim.
- **Target HUD** (`module/modules/visual/TargetHud.java`, Category.VISUALS, default OFF): top-centre glass panel with target name + HP bar (green/yellow/red by ratio) + "hp / max". Render+raycast in `HudRenderer.renderTarget`/`findTarget`: `ProjectileUtil.raycast` to 32 blocks, **clipped at first block via `world.raycast(RaycastContext OUTLINE/NONE)`** so no see-through-walls; **skips `isInvisible()` entities** (legit — never reveals hidden players). Uses `getCameraPosVec(1f)`/`getRotationVec(1f)`.

## Settings system + expandable cards + Target HUD v2 (added 2026-06-24)
- **Settings framework** (`module/setting/`): `Setting` (base) + `BoolSetting`, `SliderSetting` (value/min/max/integer, fraction()/setFraction()/display()), `ColorSetting` (accent flag + r/g/b, rgb()). `Module` now has `add(setting)` / `getSettings()` / `hasSettings()`. Only **Custom Crosshair** (size/thickness/gap sliders, Color, outline, center dot) and **Target HUD** (3D head, Health bar, Animate HP) have settings so far.
- **Expandable cards** (ClickGuiScreen): per-card anim is now `float[4]` {hover, enable, press, **expand**}. Cards with settings show a **chevron** (rotates ▶→▼ via `RotationAxis.POSITIVE_Z`). **Click card body = toggle module; click chevron/right-area = expand**. Expand animates card height; the settings panel is revealed by the growing card via a **nested scissor** (card rect intersected with grid scissor). Grid layout is now **variable-height**: per-card height = headerH + expand·panelHeight; row height = max of the two columns; rows stacked with cumulative Y. Hover lift disabled while expanded.
- **Hit-testing via recorded regions**: render records `cHits` (card header + arrow rects) and `sHits` (each control: kind 0 bool / 1 slider / 2 colour-accent / 3 colour-channel, with trackX/trackW). `mouseClicked` consults these (settings first, then arrow, then header). Sliders drag via `mouseDragged` + `activeSlider`, released in `mouseReleased`. `lastClipTop/Bot` gate clicks to the viewport.
- **Target HUD v2**: now uses `mc.targetedEntity` (already **attack-reach limited + wall-blocked** by vanilla) instead of the 32-block raycast → only shows when you can actually hit it. Added **3D head**: `InventoryScreen.drawEntity(ctx, x1,y1,x2,y2, size, 0.0625f, followX, followY, entity)` rendered AFTER the native matrix pop (GUI space) so it sits on the panel; box coords = native/S; wrapped in try/catch. **Smooth HP**: static hpDisp (fast ease) + hpGhost (slow trailing damage bar, pale red behind the main green/yellow/red bar); keyed by entity id, resets on target change. Layout has head box on the left, name+bar+hp on the right (`targetLayout` shared by panel + head).

## Font swap + HUD sizes + preview + recolors (added 2026-06-24)
- **ModeSetting** added (`module/setting/ModeSetting.java`) — pick-one cycler (modes list + index + cycle()). Rendered as "‹ value ›"; click left half = prev, right half = next (SHit kind 4).
- **Game Font module** (`cosmetic/GameFont.java`, COSMETIC): ModeSetting Font = Default/Poppins/Inter/Montserrat/JetBrains. Swaps the **vanilla** default font (chat, tooltips, signs, scoreboard) — NOT the Lume HUD/menu font. Done via `TextRendererMixin` (`@ModifyVariable` on `getFontStorage`, swaps `minecraft:default` → chosen `lume:*` font id when module on & ≠ Default). Bundled TTFs: `assets/lume/font/{inter,montserrat,jbmono}.ttf` + `.json` providers (ids `lume:inter`/`lume:montserrat`/`lume:jbmono`); Poppins reuses existing `lume:main`. **Variable fonts (Inter/Montserrat from Google Fonts) load default master; size 10/oversample 4/shift[0,1] — may need per-font tuning in the .json if baseline/size off.** Mixin registered in lume.mixins.json.
- **HUD sizes** (global + per-element): `HUD` module has `Global scale` + `Size`; every other HUD element module has a `Size` slider (0.5–2.0). `HudRenderer.scaled(ctx, es, anchorX, anchorY, runnable)` wraps each element's draw in a matrix scale around its anchor (es = globalScale × elementSize), read generically via `sizeSetting(moduleName, "Size"/"Global scale")` scanning getSettings(). Target HUD head (drawEntity, GUI space) mirrors the same scale-around-anchor manually so it stays aligned.
- **Crosshair preview** in its expanded card: `HudRenderer.drawCrosshair(ctx,cx,cy,unit)` made public; ClickGuiScreen draws a live preview box (PREVIEW_H=46) at the top of the crosshair settings (panelHeight accounts for it). In-game `renderCrosshair` now delegates to it.
- **Armor HUD**: recoloured to theme (Theme.accent / gold / red by %) and shows the **number only** (no "%"). **Totem Counter**: moved further left (sw/2−134) so it never overlaps the hotbar; count recoloured to accent / red.

## Fonts revised + Cosmetics pack 2 (added 2026-06-24)
- **Game fonts now**: Default, Inter, Rubik, PT Sans, Golos, JetBrains (all have Cyrillic). **Removed Montserrat + Poppins from the game-font list** (Montserrat .ttf/.json deleted; Poppins/`lume.ttf`/`main.json` KEPT — still the client's own UI font, user chose to leave UI on Poppins). New TTFs in `assets/lume/font/` (rubik/ptsans/golos, + inter/jbmono) with matching `.json` providers (ids `lume:rubik` etc.). Download note: PT Sans is at `ofl/ptsans/PT_Sans-Web-Regular.ttf` (NOT apache); Rubik/Golos are variable from google/fonts ofl. GameFont.selectedFont() maps mode→id.
- **Cosmetics pack 2** (all Category.COSMETIC, default OFF):
  - **Custom Menu** (`CustomMenu.java` + `TitleScreenMixin`): replaces the title panorama with Lume cream/lavender background + drifting glow blobs (drawn in `CustomMenu.drawBackground`, mixin cancels vanilla `renderBackground`). Pairs with Menu Logo.
  - **Block Outline** (`BlockOutline.java` + `WorldRendererMixin`): `@Redirect` on the `drawShapeOutline` call inside `WorldRenderer.drawBlockOutline` to recolour to accent / custom RGB (ColorSetting). require=0 → vanilla if target ever mismatches.
  - **Clean View** (`CleanView.java` + `GameRendererMixin`): bool settings "No hurt cam" (cancels `tiltViewWhenHurt`) and "No view bob" (cancels `bobView`).
- Mixins now: GameRendererMixin, InGameHudMixin, TextRendererMixin, TitleScreenMixin, WorldRendererMixin.
- **Pending (user's bigger asks, deferred)**: **Capes** (needs CapeFeatureRenderer/PlayerSkin work — non-trivial, not started). **FT/HW helper** (FunTime/HolyWorld) — user chose a **configurable framework** approach (user fills item names/radii + event chat patterns in GUI); show AoE radius of *visible* effects + event timers/counters ONLY (legit awareness, NO enemy-inventory/through-wall ESP). Not started. Visuals/QoL/UX feature batches also still queued (user picked Cosmetics first this session).

## Visuals pack + whole-card glow (added 2026-06-24)
- **Card hover glow now covers the WHOLE card** (header + expanded settings), following the cursor. ClickGuiScreen anim is now `float[5]` (added [4]=whole-card-hover `gha`); glow uses card height `dh` and `gha`. Header hover [0] still drives toggle lift/fill; clicking settings doesn't lift the card.
- **Visuals modules** (Category.VISUALS):
  - **CPS / Speed / Clock** — extra lines in the HUD info panel (only when "HUD" on). CPS via `MouseMixin`→`util/ClickTracker`. **Clock = in-game time** (world.getTimeOfDay()%24000, tick0=06:00; hh=(t/1000+6)%24, mm=(t%1000)*60/1000). **Speed** = horizontal b/s measured over a 250ms window and refreshed at that rate (static lastX/Z/time + speedShown) so the number is readable, not flickering.
  - **Block Info** — now its OWN HUD (not a panel line). Glass panel top-centre with the block's **3D item icon** (`ctx.drawItem`, GUI space after native pop, mirrors panel scale like Target head) + name. Settings: Size, **Simple info** (bool → instead of panel, draws the name next to the crosshair), **3D block** (bool → show icon). Mutually exclusive with Target HUD in practice (entity target vs block target). See renderBlockPanel/renderBlockIcon/renderBlockSimple/blockLayout.
  - **Module List** (ArrayList) — right-side staircase of enabled modules sorted by name width desc; glass row + accent right bar; `HudRenderer.renderArrayList`, anchored top-right, has Size slider. NOTE: shares the top-right corner with Potion HUD — they overlap if both on (acceptable; revisit if needed).
- Mixins now incl. **MouseMixin** (onMouseButton → ClickTracker).

## HUD/menu editor + fixes (added 2026-06-24)
- **Speed** rewritten: `util/SpeedTracker.update(mc)` called every client tick (in LumeClient tick handler) samples authoritative per-tick position → blocks/s averaged over 6 ticks → rock-steady. HudRenderer reads `SpeedTracker.get()`. (Old per-frame code removed.)
- **Clock** = in-game time now (world.getTimeOfDay()%24000, tick0=06:00).
- **Block Info**: removed the "3D block" setting (icon always shown in panel mode); only **Simple info** + **Size** remain.
- **Menu remembers category**: `selectedCat` is now static.
- **HUD editor (drag elements while menu open)**: `gui/HudLayout` holds per-element {dx,dy} GUI-px offsets (session only). `HudRenderer.transform(name, es, anchorX, anchorY, unit, runnable)` applies offset (×unit: S for native, 1 for GUI overlays) + scale; replaced all `scaled()` calls. Target head + Block icon add the offset manually. ClickGuiScreen draws draggable labelled frames (`hudFrames`/`drawHudFrames`) for ENABLED movable elements (HUD, Potion, Module List, Target, Block, Keystrokes, Inventory, Armor, Totem) in screen space; dragging a frame (when click is OUTSIDE the window) updates HudLayout. HUD resize is via the existing per-module **Size** slider (per user's choice), not the frames.
- **Menu window move + resize** (session, static `winOffX/winOffY/winScale`): window matrix = open-anim × winScale about screen centre + winOffX/Y translate. **Drag the header (top ~42px, off the controls) to move; drag the bottom-right grip to resize** (winScale 0.6–1.8). Mouse hit-tests for everything inside the window go through `localMx/localMy` (undo the transform); drag deltas use raw GUI mouse. dragMode: 1 move, 2 resize, 3 HUD-frame.

## HUD size in editor + scissor-follows-window fix (added 2026-06-25)
- **Window-move clipping bug FIXED**: card grid / per-card / preview scissors used local coords but `enableScissor` is screen-space (ignores the window matrix) → cards clipped to the old centre when the window moved. New `ClickGuiScreen.winScissor(nx1,ny1,nx2,ny2)` maps local-native rect → screen-logical via `curTotal` (= anim×winScale) + winOff. Replaced all three enableScissor calls.
- **Per-element HUD size moved OUT of the menu INTO the editor**: removed the `Size` SliderSetting from every HUD module (HUD, Keystrokes, Potion HUD, Armor, Inventory, Totem, Module List, Target HUD, Block Info). Size now stored in `HudLayout` (per-element `scale`, +`reset(name)`). `HudRenderer.sizeOf(name)` reads `HudLayout.getScale(name)`.
- **Global scale moved to Settings**: new module `misc/HudScale` (Category.SETTINGS, slider "Scale"). `HudRenderer.globalScale()` reads it (regardless of enabled).
- **Editor UX**: click a HUD frame → it's **selected** (shows a **size slider + "double-click to reset" hint** directly under it); drag the frame body to move; drag the slider to resize (dragMode 5 → `updateHudSize`); **double-click a frame resets** position+size (`HudLayout.reset`); click empty space deselects. `selectedHud` is static.

## Perf pass 2 (2026-06-25)
- Recurring FPS drop traced to **`gradientRoundedRect` per-row fills** (loops over full height each frame) used on HUD panels drawn every frame — worst: info panel (~150 row-fills), ArrayList (per row × per module), potions. **Replaced all HUD-panel gradient fills with solid `roundedRect(Theme.winBg())`** (info, potions, target, block, block-simple, ArrayList). Gradient kept only in the menu segment-active pill + drawLogo (small, 1/frame). `containedGlow` layers 7→5. `HUD Scale` ON now makes every element uniformly the slider value (sizeOf returns it directly; no global×local multiply).

## QoL pack 2 (2026-06-25)
- **Waypoints** (`qol/Waypoints.java`, Category.CHAT): static `list` of WP{name,x,y,z,color} + settings Death point / Through walls. **Add at your position** via the new "Add Waypoint" keybind (default **B**, rebindable in vanilla Controls). **Death marker** auto-saved when DeathScreen appears (LumeClient.handleDeathWaypoint, deathHandled flag). Rendered as billboarded 3D labels (name + distance) in `gui/WaypointRenderer` via `WorldRenderEvents.AFTER_ENTITIES` (uses context.matrixStack()/camera()/consumers(), `cam.getRotation()` billboard, scale −0.025, `tr.draw(..., matrix, consumers, SEE_THROUGH/NORMAL, bg, 0xF000F0)`, then `Immediate.draw()`). NOTE: world→screen projection in the HUD callback is impossible (HUD uses ortho proj), hence 3D world-render approach. Session-only (no persistence yet).
- **Toggle Sneak** (`qol/ToggleSneak.java`): holds `sneakKey.setPressed(true)` while on, releases on disable. (AutoSprint already covers always-sprint.)
- Macros/quick-commands deferred — need the config + a text-input field in the GUI (not built yet).

## Fixes 2026-06-25 (waypoints/outline/sneak)
- **Block Outline was broken**: `drawBlockOutline` calls the private **`drawCuboidShapeOutline`** (NOT `drawShapeOutline`) — old @Redirect targeted the wrong method (require=0 → silent no-op). Fixed with `@ModifyArgs` on `drawCuboidShapeOutline(ms,vc,shape,DDD,F r,F g,F b,F a)` setting args 6/7/8 (rgb) + 9 (alpha≥0.8). Works under Sodium (Sodium keeps vanilla outline).
- **Waypoints render moved HUD-side** (was WorldRenderEvents 3D — likely not firing under Sodium). Deleted `WaypointRenderer` + WorldRenderEvents reg. Now `HudRenderer.renderWaypoints` projects world→screen manually (camera basis from yaw/pitch — no quaternion guessing; FOV from `mc.options.getFov()`, base only so slight drift on sprint) and draws a dot + "name dist m" label in 2D. Sodium-proof (HUD callback always fires). Add key still **B**, death marker auto. Removed "Through walls" setting (2D always shows).
- **Toggle Sneak removed** (user: useless).

## Commands + macros + binds + waypoints v2 + FT/HW scaffold (2026-06-25)
- **Chat command system**: `command/CommandManager` — messages starting with `.` are handled locally (registered via `ClientSendMessageEvents.ALLOW_CHAT` returning `!handle()`). Commands: `.wp` (here/add x y z/color/del/clear/list), `.macro` (add <key> <text>/del/list), `.bind <module> <key|none>`, `.ft`, `.help`. Feedback via `mc.inGameHud.getChatHud().addMessage`. Key name parsing in CommandManager.keyFromName/keyName (letters/digits + SPACE/SHIFT/CTRL/TAB/F1-8).
- **KeyboardMixin** (`Keyboard.onKey` HEAD, PRESS + no screen) → `MODULES.onKey(key)` (module binds — previously never wired!) + `MacroManager.onKey(key)`.
- **MacroManager** (`command/`): key→text macros; text starting with `/` → sendChatCommand else sendChatMessage. Persisted in config.
- **Waypoints v2**: per-WP mutable colour + auto palette (`Waypoints.nextColor()`), manual-coord add, list/del/clear/colour via `.wp`. **Edge arrows**: `HudRenderer.drawEdgeArrow` pins a rotated triangle to the screen edge pointing at off-screen/behind waypoints (toggle "Edge arrows"). On-screen ones show dot+label as before.
- **Config** now also persists module **keybinds** (`m.getKey()`) and **macros**.
- **FT/HW scaffold**: `fthw/ServerType` (FUNTIME/HOLYWORLD/UNKNOWN, detect from server address) + module `modules/fthw/ServerHelper` (Category.CHAT, "Show server" setting) + `.ft` status command. No effects yet — just the base for event timers / effect radii.
- **Binds tab DONE**: ClickGuiScreen now has a 7th segment "Binds" (selectedCat == Category.values().length; `isBindsTab()`/`tabTitle(i)`). Renders a scrollable list of ALL modules (renderBinds) with name + a key chip; click a row → `bindingModule` set ("press a key…") → next `keyPressed` sets `module.setKey` (Esc=unbind), saved to config. `bindHits` list for click hit-testing; key display via `InputUtil.Type.KEYSYM.createFromCode().getLocalizedText()`. segment padSeg reduced to 11*S to fit 7 tabs.
- **GUI text fields DONE** (Waypoints manager): ClickGuiScreen has reusable `field()` text inputs (focusedField + wpName/wpX/wpY/wpZ buffers; numeric fields filter to digits/-/.; charTyped/keyPressed route to focused field; Enter=add, Esc=defocus, Backspace edits). Expand the **Waypoints** card → manager panel: name + X/Y/Z fields + **Add** button (coords if filled, else current pos), and a list of waypoints each with a colour swatch (click = cycle palette) + coords + **X** delete. `wpHits` for hit-testing; `wpManagerHeight` added to panelHeight; rendered after the bool settings in renderSettings. All actions save config.
- **Bind modes**: `Module.BindMode {TOGGLE, HOLD}` + `bindable` flag. Only **bindable** modules show in the Binds tab (currently Zoom=HOLD, AutoSprint=TOGGLE; mark more with `setBindable(true)`). Binds tab row has a HOLD/TOGGLE chip (click cycles). `ModuleManager.onKey(key, pressed)`: HOLD follows pressed, TOGGLE flips on press. KeyboardMixin handles PRESS (no screen) + RELEASE (always, to release holds). bindMode persisted in config.
- **FT/HW engine scaffold** (data still TODO — user said research later; web search was rate-limited): `fthw/EventRule` (server, name, start regex, durationSec) + `fthw/EventManager` (rules list — PLACEHOLDER FunTime "ивент" regex; detects from chat via the ALLOW_GAME hook calling `EventManager.onChat`, tracks `active` with countdown, `tick()` expires, fires `Notifications.push`). **Notifications** (`gui/Notifications`) = top-centre toast system (slide+fade), drawn from HudRenderer, **uses RenderUtil.vanillaText** (vanilla font HAS Cyrillic; LumeFont/Poppins does NOT — IMPORTANT for all Russian HUD text). **Server Helper** module HUD (`HudRenderer.renderServerHelper`) shows "Сервер: …" + active events with countdown (vanilla font). 
- **User's FT/HW answers (FunTime first)**: item helper = world radius + on-screen warning + cooldown timer; events = list in client menu + HUD popup (toast, not chat) + "active now" + countdown. **NEXT: research FunTime (events chat text, item names like трапка/дезориентация, radii) after web-search limit resets (18:00 Europe/Warsaw), then fill EventManager.rules + build ItemRule radius/warning/cooldown + events menu list.**
- **FT/HW filled (FunTime) 2026-06-25**: researched forum.funtime.su — UniqueItems plugin items: **Пласт/Трапка** (blocks vanish ~30s), **Дезориентация, Хроносфера, Огненный смерч, Снежок, Паучье чутьё, Бэкер**. Events seen: Вулкан, Сундук смерти, Чёрная буря, Воздушный шар, Сфера (+AlertEvent bot exists). 
  - `fthw/ItemRule` + `fthw/ItemRules` (held-item detection by display name; **radii are ESTIMATES — tune in-game**). `HudRenderer.renderItemHelper`: when holding a known item → bottom-centre panel (name · R · note + cooldown bar via `getItemCooldownManager().getCooldownProgress`) + `drawGroundRing` (dotted circle around player via the HUD camera-basis projection, Sodium-proof). Cyrillic → vanilla font.
  - `EventManager.rules` populated with the events above (loose regex + estimated 240–300s durations — **verify exact chat text & durations in-game**). Events list shown in the **Server Helper** expanded card (`renderEventList`, vanilla font, live countdown when active).
- **Server tab** (2026-06-25): new special tab "Server" in ClickGuiScreen (selectedCat == Category.values().length+1; `isServerTab()`, tabs = cats+2). Shows status card (Сервер: name + Статус: Активно/Не поддерживается, green/red), "поддерживает только FunTime/HolyWorld" note, an **enable toggle** (controls Server Helper module), and the events list with countdown — all vanilla font (Cyrillic). `ServerHelper` module is now **hidden from the normal grid** (filtered in modules()) and managed from this tab. `serverToggle` rect for click.
- **Still TODO / verify**: exact item radii, exact event chat lines & durations, **event SCHEDULE per anarchy** (needs data — pending user), **Language EN/RU** (translate content like potion names, not module names — pending decisions; note HUD font Poppins has no Cyrillic so RU content must use vanilla font), enemy-effect "warning in zone" (needs effect entity/particle id), HolyWorld data, macros GUI.

## 1.16.5 build started + event schedule (2026-06-25)
- **Event SCHEDULE (self-learning)**: `EventRule` now tracks `lastSeen` + learned `intervalSec` (smoothed from chat-gap on each sighting, repeats <30s ignored). `agoSec()`/`etaSec()`. Server tab shows "идёт Xс / ≈ через Mм / был Nм назад / ещё не видел" (`fmtDur`). Persisted in config under "events". This is the "self-made schedule" (no server data needed — learns by playing).
- **FunTime needs 1.16.5** (1.21.1 client gets "версия не поддерживается" on the anarchy). Decision: **two separate builds, version picked at launch** (1.21.1 + 1.16.5).
- **1.16.5 build scaffold DONE & COMPILES**: new project `C:\Users\popko\Desktop\claudecode\claudecode\LumeClient1165` (separate gradle). **Toolchain proven**: fabric-loom **1.7-SNAPSHOT builds MC 1.16.5** fine on our JDK 21, compiling to **Java 8 bytecode** (`options.release = 8`). Coords: MC 1.16.5, yarn 1.16.5+build.10, loader 0.14.24, fabric-api 0.42.0+1.16. Minimal entrypoint `com.lume.client1165.LumeClient1165` builds → `build/libs/lume-client-1165-1.0.0.jar`. Only available JDKs: JDK 21 + JRE 1.8 (no JDK 8 — fine, we cross-compile to release 8).
- **1.16.5 logic port STARTED (builds)**: ported to `com.lume.client1165.*` in Java 8: `module/{Category,Module,ModuleManager}`, `module/setting/{Setting,BoolSetting,SliderSetting,ColorSetting,ModeSetting}` (ModeSetting uses `Arrays.asList` not `List.of`), and 3 modules adapted to **1.16.5 API**: AutoSprint (`mc.options.keyForward`), FullBright (`mc.options.gamma` double field), Zoom (`mc.options.fov` double field). Entrypoint inits MODULES + `ClientTickEvents.END_CLIENT_TICK`. Builds clean. **Next**: port remaining modules (each needs 1.16.5 API checks), then the render layer (MatrixStack), then launcher version选择. Conversion rules when copying from 1.21: replace records→classes, `switch(...)->`→ classic switch, `instanceof X v`→ cast, `var`→ explicit types, `List.of`→`Arrays.asList`, and all `DrawContext`→`MatrixStack`.
- **1.16.5 PORT — remaining (big, multi-session)**: must be **Java 8 syntax** (NO records/switch-expressions/`var`/text-blocks — our 1.21 code uses all of these) AND **MatrixStack rendering** (1.16.5 has no `DrawContext`; use `MatrixStack` + `DrawableHelper.fill` + `textRenderer.draw(MatrixStack,...)`; `RenderSystem.color4f` not `setShaderColor`; different `drawTexture`). Plan: 1) port version-agnostic logic (module framework, settings, config, fthw, commands, macros, waypoints data) converted to Java 8; 2) rewrite the render layer (RenderUtil/HudRenderer/ClickGui/LumeFont/Notifications/Theme) for MatrixStack. 3) launcher: add a **version selector (1.21.1 / 1.16.5)** to LumeLauncher (download right MC+Fabric, use the right lume jar).

## Rendering gotchas (learned the hard way)
- **FPS fix:** roundedRectRaw fills straight middle in ONE ctx.fill + only 2*r corner rows per-line (AA). glow() capped to 5 layers. glass() uses solid fill (no per-row gradient). This removed the menu FPS drop.
- **Do NOT** tint textures via `RenderSystem.setShaderColor` + `ctx.draw()` flush inside HUD/Screen → caused **black-box artifacts** on the vanilla hotbar. (UiTex.java exists but is disabled, use=false.)
- **GLSL core shader didn't load** (RoundShader.java + assets/lume/shaders/core/lume_round.*) — CoreShaderRegistrationCallback never fired in user's modset (Sodium/AMD/2nd mod "trender"). Kept dormant (ready()=false → AA fallback). True iOS-26 liquid refraction would need this working — open problem.
- User has a 2nd mod **"trender"** in .lumeclient/mods (not added by us) — possible shader conflict.
- User GUI scale = small.

## Key files
Mod (`src/main/java/com/lume/client/`):
- `LumeClient.java` — entrypoint (registers RoundShader, ModuleManager, Right-Shift keybind, HudRenderCallback).
- `module/{Module,Category,ModuleManager}.java`
- `module/modules/visual/`: Hud, Coords, Keystrokes, PotionHud, ArmorHud, InventoryHud, TotemCounter, PingHud, DayCounter
- `module/modules/render/`: FullBright, Zoom, ReducedParticles ; `player/`: AutoSprint
- `gui/`: ClickGuiScreen (card-grid menu), HudRenderer (native-res HUD), RenderUtil (roundedRect AA, glow, gradient, drawLogo, font helpers), LumeFont (custom font), Theme (cream/lavender palette), RoundShader (dormant), UiTex (disabled)
- `mixin/InGameHudMixin.java` — hides vanilla potion overlay when Potion HUD on (require=0 safe). `resources/lume.mixins.json`.

Launcher (`LumeLauncher/`):
- `src/main.js` (Electron + IPC), `src/preload.js`, `src/keys.js` (LOCAL key check — stage 1), `src/launcher.js` (offline auth, fabric install, perf-mod copy, Aikar JVM flags, options.txt tune, hide-on-launch/show-on-close).
- `renderer/index.html` + `renderer.js` (cream/lavender glass UI).
- `resources/`: fabric-installer.jar, fabric-api.jar, lume-client.jar, perf-mods/ (Sodium, Lithium, FerriteCore, Indium, ImmediatelyFast, EntityCulling), lume.ico, lume.png.

## Modules currently implemented (all legit)
Visuals: HUD (info panel watermark/fps), Coords (toggle XYZ), Keystrokes, Potion HUD, Armor HUD (right of hotbar, durability %, no held item), Inventory HUD (transparent items above hotbar), Totem Counter (left of hotbar), Ping, Day Counter, **Target HUD** (name+HP of crosshair target, wall-blocked, hides invisibles).
Render: FullBright, Zoom, Reduced Particles.
Chat & QoL: AutoSprint, **Auto Reconnect**, **Anti-Spam**, **Chat Timestamps**.
Cosmetics (new category COSMETIC): **Custom Crosshair**, **Menu Logo** (on by default).

### QoL/Cosmetic pack (added 2026-06-24) — how each is wired
- **Auto Reconnect** (`module/modules/qol/AutoReconnect.java`): on disconnect, waits 5s then rejoins same server. Last server captured via `ClientPlayConnectionEvents.JOIN` → `AutoReconnect.lastServer` (static). Rejoin = `ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(info.address), info, false, null)` (1.21.1 sig: Screen,MinecraftClient,ServerAddress,ServerInfo,boolean,CookieStorage — pass null). Timer driven from `onTick()` (fires even on DisconnectedScreen). Countdown overlay drawn via `ScreenEvents.afterRender` on DisconnectedScreen (in LumeClient.drawReconnectCountdown).
- **Anti-Spam** (`qol/AntiSpam.java`): `ClientReceiveMessageEvents.ALLOW_GAME` → returns false to drop a message identical to one seen in last 1000ms (deque history 6). Skips overlay/action-bar msgs.
- **Chat Timestamps** (`qol/ChatTimestamps.java`): `ClientReceiveMessageEvents.MODIFY_GAME` → prepends grey `[HH:mm] `. Skips overlay msgs.
- **Custom Crosshair** (`cosmetic/CustomCrosshair.java`): vanilla crosshair cancelled in `InGameHudMixin.renderCrosshair` (HEAD cancel, require=0) when on; Lume one drawn in `HudRenderer.renderCrosshair` (native-res, lavender cross+dot+glow, only first-person & no screen open).
- **Menu Logo** (`cosmetic/MenuLogo.java`): `ScreenEvents.afterRender` on TitleScreen → Lume logo+wordmark+version top-left (LumeClient.drawMenuLogo). On by default.
- All chat/connection/screen hooks registered once in `LumeClient` (registerChatHooks/registerConnectionHooks/registerScreenHooks), gated by module enabled state.

## License/keys
Stage 1 = LOCAL check in `src/keys.js` (demo key `LUME-TEST-2026-DEMO`; also any `LUME-XXXX-XXXX-XXXX` with even last-group checksum). Stage 2 (TODO) = online auth server (Node+SQLite suggested) + HWID bind + signed responses + hosting. Launcher already collects HWID and shows it.

## TODO / next (user will pick)
- ~~QoL pack: Auto-Reconnect, Anti-Spam~~ ✅ DONE 2026-06-24 (+ Chat Timestamps). Still open: quick commands/macros, Waypoints (needs world render).
- ~~Cosmetics: custom crosshair, custom main menu~~ ✅ DONE 2026-06-24 (Custom Crosshair + Menu Logo). Still open: capes (needs player-renderer feature + texture).
- **Online key server** (real monetization).
- **Package launcher to .exe** (electron-builder, use `resources/lume.ico`).
- Optional: get GLSL shaders loading for true blur/refraction (investigate trender/Sodium conflict).
- Optional: per-module settings system (crosshair styles, reconnect delay slider) — currently modules are on/off only.

## Current status
Design approved by user (cream/lavender liquid glass, crisp native render, unique card layout, animated lavender launcher). Everything builds & runs. Last working: QoL + Cosmetics pack (Auto Reconnect, Anti-Spam, Chat Timestamps, Custom Crosshair, Menu Logo) — built OK + distributed 2026-06-24. Pending in-game test by user (fully close MC → Play).

## Server tab behaviour + Game Font swap (2026-06-25)
- **Server tab rules** (user request): not on a supported server → toggle hidden/disabled, **only** the "поддерживает только FunTime и HolyWorld" note shows. On a supported server → note hidden, **only the toggle** shows. Toggle ON → status card + event list appear (as before). `ServerHelper.onTick()` now auto-disables itself the moment `ServerType.current()==UNKNOWN`, so it can't stay "on" if you leave the server even with the menu closed.
- **Game Font fonts replaced**: Inter/Rubik/Golos were **variable fonts** — Minecraft's TTF provider has no `fvar` support and just renders whatever the baked default instance is (Rubik's default was weight 300 "Light", not Regular — a real visual bug). Deleted all 5 old font files and re-sourced fresh **static** TTFs (Latin+Cyrillic subset, via Google Fonts legacy-UA trick: `curl -A "Mozilla/5.0 (Linux; U; Android 2.2...)" https://fonts.googleapis.com/css?family=X:400&subset=latin,cyrillic` → returns a direct `.ttf` url). New options: **Roboto, Open Sans, PT Sans, Noto Sans, JetBrains** (ids `lume:roboto/opensans/ptsans/notosans/jbmono`). Verified all 5 with `fonttools` (`fvar` absent + 0 missing Cyrillic/Latin glyphs) before shipping.

## Migrated main client 1.21.1 → 1.21.4 (2026-06-25)
User asked to move the main client off 1.21.1 onto **1.21.4** (decided after checking Mojang's real version manifest — current "1.21.x" line has gone all the way to 1.21.11; 1.21.4 was chosen as a known-stable target rather than chasing the bleeding edge).
- `gradle.properties`: minecraft_version=1.21.4, yarn_mappings=1.21.4+build.8, fabric_api_version=0.119.4+1.21.4 (loader_version kept at 0.16.10 — loader is MC-version-agnostic and still builds fine).
- **API breaks fixed**: `ParticlesMode` moved package `client.option`→`net.minecraft.particle`. `ItemCooldownManager.getCooldownProgress` now takes `ItemStack` not `Item`. `DrawContext.drawItemInSlot`→renamed back to `drawStackOverlay(TextRenderer,ItemStack,x,y)`. `DrawContext.drawTexture(...)` now requires a `Function<Identifier,RenderLayer>` first arg — use `RenderLayer::getGuiTextured`. `NativeImage.setColor` went private → use public `setColorArgb(x,y,argbInt)` (takes the same int `BufferedImage.getRGB()` returns, no manual channel reordering needed).
- **Deleted dead code that depended on a Fabric API class removed in 1.21.4**: `RoundShader.java` (GLSL core-shader experiment, `CoreShaderRegistrationCallback` gone) — it never actually loaded in-game anyway (`ready()` was always false, dormant fallback path). Also deleted `UiTex.java` — fully unreferenced dead code (already disabled, caused black-box artifacts, zero callers found). Removed the `assets/lume/shaders/core/` files and the `RoundShader.register()` call in `LumeClient.java`.
- Build confirmed: `gradlew build` → BUILD SUCCESSFUL.
- **Launcher updated to match**: `LumeLauncher/src/launcher.js` `MC_VERSION` → `1.21.4`. `renderer/index.html` version label → "1.21.4 · Fabric" (verified via Claude Preview by force-revealing the `#home` card — `#login`/`#home` toggle visibility, home is `class="hidden"` until after key activation). Re-sourced **all** bundled jars in `LumeLauncher/resources/` for 1.21.4 from Modrinth API (project ids: sodium=AANobbMI, lithium=gvQqBUqZ, ferrite-core=uXXizFIs, immediatelyfast=5ZwdcRci, entityculling=NNAgCjsB, fabric-api=P7dR8mSH): fabric-api 0.119.4+1.21.4, sodium 0.6.13, lithium 0.15.3, ferritecore 7.1.3, ImmediatelyFast 1.8.6, EntityCulling 1.10.5. **Indium dropped** — no 1.21.4 build exists upstream (last release targets 1.21/1.21.1, unmaintained since Feb 2025); revisit if a new build appears. `fabric-installer.jar` (v1.1.1, already latest) needed no change — it resolves MC/loader versions dynamically over network, nothing version-baked.
- **Not yet done**: rebuilt jar copied to `LumeLauncher/resources/` only; NOT copied into `%APPDATA%\.minecraft\mods` (that's still on the old 1.21.1 Fabric profile — would need a fresh `fabric-loader-0.16.10-1.21.4` profile installed via TLauncher if the user wants to test outside our own launcher). Primary test path is the Lume launcher itself (Play → downloads vanilla 1.21.4 + installs Fabric fresh). Live launch not yet smoke-tested end-to-end by either of us this session (big vanilla-asset download; deferred to the user).
