# Third-Party Notices / 第三方声明

本模组（Universal Wrench，mod id `create_better_wrench`）包含/衍生自下列第三方作品。

## Create (机械动力)
- Project: https://github.com/Creators-of-Create/Create
- Copyright (c) The Create Team / The Creators of Create
- **Code License: MIT** — 全文见 `licenses/Create-MIT.txt`
- **Assets License: All Rights Reserved**（适用于 Create 的 `src/main/resources/assets/**`）
- 本模组如何使用 Create：

  1. **代码衍生（MIT）**：`client/WrenchToolSelection.java` 由 Create 的 `ToolSelectionScreen` 复刻/改写而来。
     已在该文件头部保留版权声明；MIT 全文见 `licenses/Create-MIT.txt`。
     ⚠️ 该文件（及其衍生的展示部分）**仍受 MIT 许可约束**，本模组的 All Rights Reserved 声明不覆盖这部分。

  2. **运行时引用 Create 的资源（不再分发）**：
     - `client/WrenchHud.java` 与 `client/WrenchToolSelection.java` 引用
       `com.simibubi.create.foundation.gui.AllGuiTextures.HUD_BACKGROUND`（工具条底纹）；
     - `mode/WrenchMode.java` 引用 `com.simibubi.create.foundation.gui.AllIcons.I_TRASH`（拆除模式图标）。
     这些纹理由**玩家自行安装的 Create 模组自己的 JAR** 提供；本模组的 JAR 内**不包含**、
     也**不再分发**任何 Create 的资源文件。

  3. **运行时按引用继承 Create 的模型**：`models/item/better_wrench.json` 是 `neoforge:composite` 物品模型，
     其 `body` 子模型的 `parent` 指向 `create:item/wrench/item`。
     **几何数据在运行时由 Create 自己的 JAR 解析**，本模组只发布一条引用，
     **JAR 内不含任何 Create 的模型/贴图文件**；物品皮肤是我们自绘的 16×16 贴图。
     （我们**没有**复制 Create 的 `wrench/item.json` 或 `wrench.png`。）

  4. **仅在运行时调用 Create 的公开 API**（`FillingBySpout`、`GenericItemEmptying`、
     `SequencedAssemblyRecipe`、`RecipeApplier`、`AllRecipeTypes` 等）—— 属于正常依赖调用，
     不构成对 Create 代码的复制。

- 本模组是 **非官方** 的 Create 扩展，与 Create 团队无隶属关系，也未获其背书或维护。
- 本模组以 **required** 方式依赖 Create，**不内嵌**、**不打包** Create。

## Minecraft / NeoForge
- 本模组**不包含**任何 Minecraft 或 NeoForge 的资源文件；它们在运行时由玩家自己的游戏安装提供。
- 我们自绘的贴图在**取色**阶段参考过原版 `oak_log` / `gold_block` / `andesite` 的颜色数值与
  Create 黄铜件的颜色数值（**仅取样色值，未复制任何像素图案**），像素排布由本项目脚本自行生成。

## 我方自绘素材
- `textures/item/better_wrench.png`、`textures/item/wrench_gear.png`：由 `scripts/` 下的生成脚本程序化绘制。
- `textures/gui/mode_*.png`：由**用户提供的原始图**经脚本处理（黑→透明、蓝→黑）得到。
  ⚠️ **待确认**：这几张原始图需为用户原创或已获得相应授权（详见 docs/03 同日审计记录）。

> 若将来在本项目中新增对其它第三方代码/素材的使用，请在此文件补充对应条目。
