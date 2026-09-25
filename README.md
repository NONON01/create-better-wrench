# Create Better Wrench

An **unofficial** [Create](https://github.com/Creators-of-Create/Create) addon that adds a more
convenient wrench. Hold **ALT** to open a bottom tool-select bar, then **scroll** to switch modes.

> Not affiliated with, endorsed by, or maintained by the Create team.

**万能扳手** —— 一把更好用的机械动力扳手。按住 **ALT** 呼出底部工具条,**滚轮**切换模式。
非官方 Create 扩展,与 Create 团队无隶属关系。

---

## Modes / 模式

| Mode | id | What it does |
| --- | --- | --- |
| Wrench 扳手 | `wrench` | The standard Create wrench experience (rotate / pick up blocks) |
| Connect 连接 | `connect` | Right-click a start and an end block, add air-cell corners freely; the drivetrain (shafts, gearboxes or large cogwheels) is placed and the materials are consumed |
| Deconstruct 拆除 | `deconstruct` | Two-click area selection, then batch-remove everything a wrench can remove (Ctrl+Scroll filters: All / Create only / Redstone only) |
| Process 加工 | `assemble` | Lock a Depot, then right-click it while holding an item to act as a Deployer / Spout / axe. Handles sequenced assembly, item application, log stripping and fluid filling. Ctrl+Scroll adjusts how long a finished product stays on the depot |
| Mod Info 模组描述 | `coming_soon` | Placeholder info page (also hides a small easter egg) |

All player-facing text lives in the language files (`assets/create_better_wrench/lang/`),
and both `en_us` and `zh_cn` are kept in sync.

## Requirements / 依赖

| | |
| --- | --- |
| Minecraft | **1.21.1** |
| NeoForge | **21.1.249** or newer |
| Create | **6.0.10** up to (not including) 6.1.0 — **required** |

## Install / 安装

1. Install NeoForge 21.1.x for Minecraft 1.21.1.
2. Drop Create 6.0.10+ and `create_better_wrench-<version>.jar` into your `mods/` folder.
3. Works on both the client and a dedicated server.

Get the wrench in game with `/give @s create_better_wrench:better_wrench`, from Create's
**Base** creative tab, or by crafting it.

## Building from source / 从源码构建

```powershell
# Windows (PowerShell)
. .\scripts\setenv.ps1                                  # JDK 21 / Gradle 9.7.1 (workspace helper)
$env:GRADLE_USER_HOME = '<your gradle home>'
.\gradlew build                                          # -> build/libs/create_better_wrench-<version>.jar
```

Requires **JDK 21**. Dependencies resolve from `maven.createmod.net` (Create, Ponder) and
`maven.ithundxr.dev` (Registrate).

## License / 许可

**All Rights Reserved**, with a **Create-MIT exception** for the portions derived from Create.

- You may play, ship this jar unmodified in modpacks/servers, and feature it in videos.
- You may **not** redistribute modified versions or reuse the code elsewhere without permission.
- Portions derived from Create's MIT-licensed code **remain MIT**; the full MIT notice is
  reproduced **verbatim in Appendix A** of [`LICENSE.md`](LICENSE.md).

Everything lives in that **single file** — our terms, every third-party notice, and the
verbatim MIT text. It is also what the in-game mod menu links to via its `license` field.

---

## Credits / 署名

- **Derived from Create's `ToolSelectionScreen`** (MIT License, Copyright (c) The Create Team /
  The Creators of Create): `client/WrenchToolSelection.java`, and the HUD background texture
  `AllGuiTextures.HUD_BACKGROUND` which is referenced at runtime from the player's own Create
  installation (no Create asset is redistributed in this jar).
- The item model is a `neoforge:composite` whose geometry `parent` points at
  `create:item/wrench/item`; the geometry is resolved at runtime by Create, **not** copied.
- All mode icons and the item texture are our own work.

## Repository notes / 仓库说明

- This repository is the mod project itself; the wider development workspace (docs, tools,
  reference sources) lives outside it.
- **Release metadata is complete**: `displayName` / `authors` / `logoFile` / `displayURL` /
  `license` / `description` are all filled in `src/main/templates/META-INF/neoforge.mods.toml`,
  and `src/main/resources/icon.png` ships in the JAR. The release checklist lives in the
  workspace docs (`docs/guides/04-publishing.md`).
