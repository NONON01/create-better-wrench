# Create Better Wrench

An **unofficial** [Create](https://github.com/Creators-of-Create/Create) addon that turns the wrench
into a single tool for drivetrain laying, chain conveyor routes, bulk deconstruction, depot
processing and optional combat bonuses.

> Not affiliated with, endorsed by, or maintained by the Create team.

## Features

Hold **ALT** to open the bottom tool bar, then scroll to switch modes.

| Mode | Summary |
| --- | --- |
| Wrench | The standard Create wrench behaviour: rotate blocks and pick them up. |
| Connect | Right-click a start and an end block; corners are added freely, the drivetrain (shafts, gearboxes, large cogwheels) is laid automatically and the materials are consumed. |
| Chain Conveyor | Chain conveyor routes in three layouts: Standard, Straight and Semi-auto. The wrench places the missing chain conveyors along the planned path and links them with chains. |
| Deconstruction | Two-click area selection, then batch removal of everything a wrench can remove. Ctrl plus scroll cycles the filters: All, Create only, Redstone only. |
| Depot Processing | Locks a Depot so that right-clicking it with an item acts like a Deployer, Spout or axe. Covers sequenced assembly, item application, log stripping and fluid filling; sub-features cover assembly, filling, splash, blasting, smoking, haunting and forging. |
| Combat | Optional combat bonuses (damage and attack speed) while the mode is active, limited to players authorised on the server. |
| Mod Info | Placeholder information page. |

Player-facing text lives in `src/main/resources/assets/create_better_wrench/lang/`; `en_us` and
`zh_cn` are kept in sync.

## Requirements

| | |
| --- | --- |
| Minecraft | 1.20.1 |
| Loader | Forge 47.1.3 or newer (built against 47.1.3) |
| Create | 6.0.8 or newer, below 6.1.0 (required) |
| Java | 17 |

Flywheel, Ponder and Registrate are shipped inside the published Create jar, so Create is the only
extra installation step.

> **1.20.1 note.** Minecraft 1.20.1 has no vanilla hammer item (the mace arrives in 1.21), so the
> Forging sub-feature requires a hammer provided by another mod, or a data pack that adds an item to
> the `forge:tools/hammer` or `c:tools/hammer` item tag. Both tags are accepted, which covers the
> convention of this platform and of data packs ported from higher versions. The remaining features
> behave as on the 1.21.1 line.

## Install

1. Install Forge for Minecraft 1.20.1.
2. Place Create and `create_better_wrench-<version>.jar` into the `mods/` directory.
3. Client and dedicated server are both supported.

In game the wrench is obtained by crafting, from Create's Base creative tab, or with
`/give @s create_better_wrench:better_wrench`. Version to game version correspondence follows the
jar file name; exact builds are listed under the release tags of the repository and on the platform
pages.

## Configuration

`/cbw config` opens the in-game configuration screen. Settings are grouped as Connect,
Deconstruction, Processing, Chain Conveyor and Combat.

The Chain Conveyor group contains the master switch, the **total chain length limit** (default 256
blocks, summed over all segments of one layout) and the planner search budgets. The limit keeps a
single request from spanning a very long distance; Create's own per-segment limit is unaffected.
On a dedicated server the values are authoritative server side, and the client only mirrors them.

## Commands

| Command | Effect |
| --- | --- |
| `/cbw config` | Opens the configuration screen (client side). |
| `/cbw version` | Prints the installed mod version. |
| `/cbw combat <targets> <true\|false>` | Grants or revokes combat mode for the selected players; requires permission level 2. |

## Building

The Gradle wrapper in the repository root performs the build, and JDK 17 is required for this line.
The built jar lands in `build/libs/`.

## License

**Source code: MIT. Own assets (textures, models, sounds): All Rights Reserved.** Portions derived
from Create remain under Create's MIT. All terms and every third-party notice are collected in the
single [`LICENSE.md`](LICENSE.md) that is also shipped inside the jar and linked from the in-game mod
list.

## Credits

- The tool bar is derived from Create's `ToolSelectionScreen` (MIT, Copyright (c) The Create Team /
  The Creators of Create): `src/main/java/com/nonono/createbetterwrench/client/WrenchToolSelection.java`.
  The HUD background texture is referenced at runtime from the installed Create jar; no Create asset
  is redistributed.
- The item model is a `forge:composite` whose geometry `parent` points at `create:item/wrench/item`
  and is resolved at runtime by Create.
- Mode icons and the item texture are original work. Editable art sources are kept in `art-source/`
  inside the repository.

## Repository Notes

- This repository contains the mod project itself.
- Release metadata (display name, authors, logo, URL, license, description) is filled in
  `src/main/resources/META-INF/mods.toml`, and the icon ships from `src/main/resources/icon.png`.
