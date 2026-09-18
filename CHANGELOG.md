# Changelog

All notable changes to Create Better Wrench are documented here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/); versions use
`<mod version>+create<Create version>`.

## [0.4.0] - unreleased

### Added
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
  Raw / working / done are shown as three spots around the depot.
- **Mod Info mode**: placeholder info page.
- Bottom tool-select HUD adapted from Create's blueprint bar (ALT to focus, scroll to cycle,
  multi-line descriptions, `[]`/`{}` spans rendered bold + hint-blue).
- Combat-mode easter egg behind the `cbw.battlemode` permission node (default: operators only).

### Changed
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
- Unlocking a depot now **returns everything to your inventory** — the item on the depot plus both
  piles, including products that had been popped out.
- The `create` dependency no longer forces a load order (`ordering` is now `NONE`).
- **Licensing consolidated into a single `LICENSE.md`** (previously `LICENSE` + `THIRD_PARTY_NOTICES.md`
  + `licenses/Create-MIT.txt`): our terms, every third-party notice, and Create's MIT notice
  (reproduced **verbatim** in Appendix A) now live in one file — which is exactly what the in-game
  mod menu's `license` field points at (`Read attached LICENSE.md`).
- `neoforge.mods.toml`: the `credits` field was removed and the description reduced to a single
  line, matching how Create itself presents its own mod entry.

### Fixed
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
