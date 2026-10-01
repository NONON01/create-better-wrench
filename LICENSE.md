# Create Better Wrench — License / 许可

`Create Better Wrench` — mod id `create_better_wrench` — Copyright (c) 2026 nonono.

> **This single file is the source of truth for this mod's licensing *and* for every
> third-party notice.** It is deliberately one file: the in-game mod menu's `license`
> field points here (`Read attached LICENSE.md`), so everything a player needs to see
> is reachable from one place.

---

## 1. Our license / 本模组的许可

本模组是**组合许可**：源代码 MIT、自有素材（贴图/模型/音效等非代码内容）保留所有权利、
改编自 Create 的部分仍依 MIT。三者的适用范围见 §1.1 至 §1.3。

### 1.1 Source code — MIT License / 源代码：MIT 许可

Copyright (c) 2026 nonono

```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

**简体中文**：本模组的**源代码**按 MIT 许可发布（版权归 nonono，2026）。任何人都可以自由使用、
复制、修改、合并、发布、再许可与销售本模组的源代码（含商业用途），只需在所有副本或实质性部分中
保留上述版权与许可声明。源代码按"原样"提供，不附带任何形式的担保。

### 1.2 Assets — All Rights Reserved / 自有素材：保留所有权利

Textures, models, sounds and other non-code assets **created by this project** remain
**All Rights Reserved**. You are free to play this mod, include it (unmodified) in
modpacks and on servers, and feature it in videos/streams, without prior written permission.
You may NOT redistribute modified versions of those assets, nor reuse them in other
projects, without prior written permission from the copyright holder.

**简体中文**：本模组**自有的美术与音频素材**（贴图、模型、音效等非代码内容）仍**保留所有权利**。
你可以自由游玩本模组、在整合包/服务器中使用并分发**未修改**的 jar、以及制作视频与直播，无需事先
取得书面许可；未经版权持有人书面许可，不得再分发修改后的素材，也不得将素材用于其它项目。

### 1.3 Scope — portions derived from Create / 适用范围：改编自 Create 的部分

Portions of this mod are derived from **Create** and remain under the **MIT License**.
The terms in §1.1 and §1.2 **do not override** that. The MIT copyright and permission notice
is reproduced **verbatim in [Appendix A](#appendix-a--creates-mit-license-verbatim)**,
exactly as the MIT License requires.

本模组部分代码衍生自 **Create**，这些部分**仍按 MIT 许可**；§1.1 与 §1.2 的条款**不改变**这一点。
MIT 要求的版权与许可声明已**全文逐字收录在本文档附录 A**。

> 组合说明: 代码 MIT、自有素材 ARR、Create 改编部分 MIT。jar 内不含任何 Create 命名空间下的资源文件；
> 模式图标的绘制在运行期引用玩家自己安装的 Create 贴图，不构成再分发（见 §2.1）。

---

## 2. Third-Party Notices / 第三方声明

### 2.1 Create (机械动力)

- Project: <https://github.com/Creators-of-Create/Create>
- Copyright (c) The Create Team / The Creators of Create
- **Code license: MIT** — full text in [Appendix A](#appendix-a--creates-mit-license-verbatim)
- **Assets license: All Rights Reserved** (applies to Create's `src/main/resources/assets/**`)

How this mod uses Create:

1. **Derived code (MIT).** `client/WrenchToolSelection.java` is a re-implementation of Create's
   `ToolSelectionScreen`. The copyright notice is kept in that file's header.
   That file (and the display code derived from it) **remains under the MIT License**, with Create's
   copyright notice; the MIT text is reproduced verbatim in Appendix A.

2. **Runtime reference to a Create asset (no redistribution).**
   `client/WrenchToolSelection.java` references
   `com.simibubi.create.foundation.gui.AllGuiTextures.HUD_BACKGROUND` (the mode-bar background).
   That texture is supplied by the **player's own Create installation**; **no Create asset file
   is included in, or redistributed with, this mod's JAR.** The five mode icons are script-recoloured
   from the author's **own hand-drawn originals**, and the item textures are drawn procedurally by
   this project's own scripts — see §2.3 for the file-by-file record.

3. **Models resolved at runtime.** `models/item/better_wrench.json` is a `neoforge:composite`
   item model whose `body` sub-model `parent` points at `create:item/wrench/item`.
   The geometry is parsed **at runtime from Create's own JAR** — this mod ships only the
   reference and contains no Create model or texture file. The item skin is our own 16×16
   texture. (We did **not** copy Create's `wrench/item.json` or `wrench.png`.)

4. **Public API calls only.** `FillingBySpout`, `GenericItemEmptying`, `SequencedAssemblyRecipe`,
   `RecipeApplier`, `AllRecipeTypes`, etc. are ordinary runtime dependency calls, not copies of
   Create's code.

- This mod is an **unofficial** Create addon. It is **not affiliated with, endorsed by, or
  maintained by** the Create team.
- This mod depends on Create as a **required** dependency; it does **not** bundle or repackage Create.

### 2.2 Minecraft / NeoForge

- This mod contains **no** Minecraft or NeoForge resource files; they are provided at runtime by
  the player's own game installation.
- Our own textures sampled **colour values** from vanilla `oak_log` / `gold_block` / `andesite`
  and from Create's brass parts **during colour picking only** (no pixel pattern was copied);
  the pixel layout is generated by this project's own scripts.

### 2.3 Our own assets / 我方自绘素材

- `textures/item/better_wrench.png`, `textures/item/wrench_gear.png` — drawn procedurally by the
  scripts under `scripts/`.
- `textures/gui/mode_*.png` — the five HUD mode icons are produced **by script** from 16×16
  **originals hand-drawn by the author** (workspace `images/手绘/*.png`; the originals are **not**
  redistributed — only the recoloured results ship inside the JAR).
  `scripts/gen_mode_wrench_icon.ps1` only recolours them: **black → transparent, blue → black**,
  everything else kept as-is. Provenance verified pixel-exact on 2026-09-23:

  | original (`images/手绘/`) | shipped icon | match |
  | --- | --- | --- |
  | `扳手.png` | `textures/gui/mode_wrench.png` | exact (0 diff) |
  | `拆除.png` | `textures/gui/mode_deconstruct.png` | exact (0 diff) |
  | `工作.png` | `textures/gui/mode_assemble.png` | exact (0 diff) |
  | `mod描述.png` | `textures/gui/mode_coming_soon.png` | exact (0 diff) |
  | `连接.png` | `textures/gui/mode_connect.png` | near-exact (hand-tweaked afterwards) |
  | `曲柄.png`, `物流网络.png` | — | unused, not shipped |

  Command form: `powershell -File scripts/gen_mode_wrench_icon.ps1 -Source "<original>" -Out "<texture>"`.
  Author confirmed the originals on 2026-09-23 (audit trail: `docs/log/01-operations.md`).
- `icon.png` — composed by `scripts/gen_mod_icon.ps1` from a render exported in-game.

> If a future change introduces another third-party work, add its entry to §2 of this file.

---

## Appendix A — Create's MIT License (verbatim)

```
MIT License

Copyright (c) The Create Team / The Creators of Create

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Disclaimer / 免责声明

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT.
