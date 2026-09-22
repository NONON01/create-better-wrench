# Changelog

All notable changes to Create Better Wrench are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).
Versions are SemVer; the current scheme is `<mod version>+mc<Minecraft version>` (e.g. `0.5.0+mc1.21.1`) —
everything after `+` is build metadata. Earlier releases used `<mod version>+create<Create version>`
(e.g. `0.4.0+create6.0.10`), and `1.0.0-beta` was a short-lived prerelease-style label.

## [0.5.0+mc1.21.1] - unreleased

### Added
- **Process mode: batch fan-style processing.** Hold **water bucket ⇒ washing**, **lava bucket ⇒ blasting/smelting**,
  **flint & steel ⇒ smoking** — or **haunting** when the block *below* the depot is soul sand / soul soil / soul fire.
  The whole stack on the depot is converted in one click, using Create's own fan-processing recipes
  (`create:splashing` / `create:haunting` / vanilla smoking / smelting). Buckets are **not consumed** and flint & steel
  only loses **1 durability**. Unlike Create's fan, an empty result **never destroys** the item; every product is
  **ejected** (it pops out from above the depot like the other paths), so multi-result recipes such as washing
  soul sand (quartz + gold nugget) leave in one go.
- **A config file** (`serverconfig/create_better_wrench-server.toml`) for the values that used to be hardcoded:
  deconstruct selection limit & batch size, connect corner/segment/total-block limits and end-point distance.
  Every option carries a **bilingual (English + Chinese) comment**, and the in-game config screen (built into
  NeoForge — no extra dependency) shows them, so players can tune the mod without editing code.
- **Wrench mode** (leftmost, the default): passes right-clicks through to Create's standard wrench behaviour.
- **Connect mode**: pick a start, any number of air-cell corners, then an end; the drivetrain is
  routed per segment (same axis = straight, same plane = one automatic 90° turn, non-planar = refused)
  and the materials are taken from your inventory. Ctrl+Scroll switches the corner type (Gearbox / Large Cogwheel).
- **Deconstruct mode**: two-click area selection, Ctrl+Scroll filters (All / Create only / Redstone only).
  Multi-block structures are delegated to Create's `IWrenchable.onSneakWrenched`, so a Large Water Wheel
  comes apart as a whole.
- **Process mode**: lock a Depot, then right-click it with an item in hand to act as a Deployer,
  Spout or axe. Supports `create:sequenced_assembly`, `create:deploying` / `create:item_application`,
  vanilla log stripping and `create:filling` (batch filling by actual fluid amount).
  The raw pile sits beside the depot as a separate drop; the depot itself shows the item being worked on.
- **Mod Info mode**: placeholder info page.
- Bottom tool-select HUD adapted from Create's blueprint bar (ALT to focus, scroll to cycle,
  multi-line descriptions, `[]`/`{}` spans rendered bold + hint-blue).
- Combat-mode easter egg behind the `cbw.battlemode` permission node (default: operators only).

### Removed
- **Connect mode: the "offhand shaft variant" feature is gone.** The shaft material is now always
  `create:shaft`; the offhand is no longer inspected to pick the shaft item, **and it is no longer
  counted or consumed as a material source** — connect uses your main inventory only.
- **Process mode: the separate "done pile" was removed** (in practice it was a no-op). Finished products
  are ordinary drops now: pickable, and they despawn like any other dropped item after 5 minutes.
  Unlocking a depot therefore returns only the item on the depot plus the raw pile.
- The F9 "export wrench icon" developer tool (and its two lang keys) no longer ships in the jar.

### Changed
- **The Wrench mode's tool-bar description is always centred** — even though it now has two lines, it is a
  per-mode exception to the usual "multi-line = left aligned" rule for the tool-tip panel.
- **The wrench now only works as a mode tool in your main hand.** Held in the **offhand** it is just a
  normal Create wrench: no tool bar, ALT+Scroll does not switch modes, no mode feature applies, and the
  combat easter egg is off (all client handlers, the three server payload checks and the combat gate now
  require the main hand). The Wrench mode description gained a second line saying exactly that.
- Version scheme is `<mod version>+mc<Minecraft version>` again → **`0.5.0+mc1.21.1`**.
- Renamed the mod to **Universal Wrench** (`mod_name`; the `mod_id` stays `create_better_wrench`).
  ⚠️ Renamed **again** to **Create Better Wrench** in the same release — `Universal Wrench` collided with
  an existing Modrinth project of the same name and niche. The **item** is still called
  "Universal Wrench"; only the mod name changed.
- Dropped the placeholder `issueTrackerURL` (optional field, no tracker yet) and pointed `displayURL`
  at the Modrinth project page. The `REPLACE_ME` placeholders are gone.
- Java package moved to `com.nonono.createbetterwrench`; version scheme is now
  `<mod version>+create<Create version>`.
- Process mode: a finished product now **stays on the depot for a configurable number of ticks**
  before popping out. Ctrl+Scroll cycles No stay / Short / Medium (default) / Long = 0 / 2 / 4 / 8 ticks.
- Process mode: the depot is **automatically refilled** from the raw pile after every step, so a batch
  can be worked through without re-placing material.
- Unlocking a depot now **returns everything it still owns to your inventory** — the item on the depot
  plus the raw pile. Finished products are ordinary drops (see below) and stay in the world.
- The `create` dependency no longer forces a load order (`ordering` is now `NONE`).
- **Licensing consolidated into a single `LICENSE.md`** (previously `LICENSE` + `THIRD_PARTY_NOTICES.md`
  + `licenses/Create-MIT.txt`): our terms, every third-party notice, and Create's MIT notice
  (reproduced **verbatim** in Appendix A) now live in one file — which is exactly what the in-game
  mod menu's `license` field points at (`Read attached LICENSE.md`).
- `neoforge.mods.toml`: the `credits` field was removed and the description reduced to a single
  line, matching how Create itself presents its own mod entry.
- **Connect mode: automatic corner orientation is now *searched*** (bounded to 64 attempts) instead of the
  connection being refused on the first self-conflict. The preferred route's behaviour is byte-for-byte
  unchanged; the search is memoised so the client ghost preview stays cheap.
- **Connect mode: stricter server-side checks** — distance to the end point, `mayInteract` per placed block,
  and every placement posts `BlockEvent.EntityPlaceEvent` (a cancellation reverts everything already placed).
  Rejections are reported distinctly: `PROTECTED` / `SELF_CONFLICT` / `TOO_FAR`.
- **Process mode: while you hold the wrench in your *main* hand the click is still consumed (lock/unlock),
  but a wrench in the offhand no longer swallows it** — so "wrench in offhand + material in main hand" can
  actually apply the material. Finished products now pop out as ordinary drops (stay time still configurable).
- **Process mode, fan-style path revised: all products are now ejected.** Water bucket / lava bucket / flint & steel
  processing used to leave the first result on the depot and drop the rest without any motion; every result now pops
  out from above the depot just like the other paths, so multi-result recipes (washing soul sand ⇒ quartz + gold
  nugget) leave in one go and the depot is instantly free for the next item.
  ⚠️ This supersedes the earlier "products stay on the depot" wording in the 0.5.0 entry above.
- **No deprecated API use left.** NeoForge deprecated the whole `LevelReader#hasChunk*` family
  (`hasChunk`, `hasChunkAt(BlockPos)`, `hasChunkAt(int,int)`, `hasChunksAt(...)`); the 8 call sites
  (connect, deconstruct and the three payloads) now use `Level#isLoaded(BlockPos)` — the same API Create uses.
  A full recompile with `-Xlint:deprecation` is now completely silent.
- Deconstruct mode: the reported count now equals the blocks actually removed, including Create's
  multi-block cascade.
- Deprecation cleanup: `@EventBusSubscriber(bus = ...)` is gone (16 annotations simplified, 2 mod-bus
  listeners now registered explicitly from the client-only entry point `BetterWrenchClient`) — the build
  compiles with zero warnings.
- **Shift + right-click now cancels** an unfinished Connect / Deconstruct selection.

### Fixed
- **Connect mode: item duplication fixed.** Material requirements are now totalled per item before placing,
  so a plan can never place more blocks than it charges for.
- **Connect mode: no more getting disconnected** after picking more than 32 corners (the client now mirrors
  the server's cap and tells you instead).
- Connect mode: a route that would need two different blocks in one cell, or that would overwrite the start /
  end block, is now re-routed automatically or refused cleanly instead of corrupting the structure.
- Connect mode: every cell on the path is checked against already-loaded chunks, so planning can no longer
  force-load or generate terrain, and the ghost preview matches the server's material/length rules.
- **Process mode: the "stay, then pop" queue remembers which item it queued** and is cancelled when the depot
  is unlocked / broken / blown up — it can no longer pop whatever happens to be on the depot by then.
- Process mode: filling no longer silently loses the input it took from the raw pile if the first fill fails.
- Process mode: a pile holding a foreign item can no longer be consumed without being credited.
- Client state: unfinished selections are also cleared when **changing dimension** (previously only on logout),
  and the toolbar highlight resets together with the mode.
- Deconstruct mode: reported count fixed, and the **Redstone only** filter now also matches tripwires,
  daylight detectors, hoppers and rails.
- Deconstruct mode: the block-break event is posted for the wrench-pickup branch, so protection plugins work.
- **Security**: connect / deconstruct payloads are validated server-side (volume cap, corner-count cap,
  `mayBuild`, spectator check, wrench-in-hand check); connection corners are checked for occupancy;
  the wrench-pickup branch now posts `BlockEvent.BreakEvent` so land-protection plugins can block it.
- **Security**: `AssemblePayload` now validates spectator / `mayBuild` / wrench in hand / distance / chunk,
  so a modified client can no longer lock or unlock someone else's depot remotely.
- Deconstructing is now **capped at 64³** and executed in **16³ sub-chunks, one per server tick**, so a
  large selection no longer freezes the server.
- Dedicated-server crash risk: the combat-mode sync payload no longer references an
  `@OnlyIn(Dist.CLIENT)` class from the common `network/` package (the whole jar is now free of
  client-class references outside the client package).
- Depot piles: a pile that has more items than one entity can hold is handled correctly
  (previously only the first entity was released, leaving the rest permanently unpickable).
- Depot piles: piles no longer get swallowed by the depot — they fall and rest on the depot surface.
- Piles are released when the depot is broken or blown up.
- Client state is now cleared on logout (mode, Ctrl options, unfinished selections, combat toggle),
  so it no longer leaks across worlds.
- HUD animation advances once per client tick instead of once per frame (was 3–10× too fast).
- The tooltip panel keeps Create's fixed height instead of growing with the line count.
- A bogus "conflicting power direction" rejection in Connect mode was removed (gearboxes reverse rotation,
  so comparing speed signs was wrong).

### Compatibility
- Minecraft 1.21.1 / NeoForge 21.1.249 / Create `[6.0.10, 6.1.0)`
- Java 21

## [0.3.0]
### Changed
- Added the license-compliance files (`LICENSE`, `THIRD_PARTY_NOTICES.md`, `licenses/Create-MIT.txt`),
  a `credits` entry in `neoforge.mods.toml`, and moved the package off `com.example.*`.

## [0.2.1]
### Added
- Initial public shape: independent wrench item, `c:tools/wrench` tag, ALT tool bar with mode switching,
  Connect and Deconstruct modes, and the mode descriptions.
