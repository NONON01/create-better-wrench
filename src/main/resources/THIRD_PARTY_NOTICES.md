# Third-Party Notices / 第三方声明

本模组（Universal Wrench，mod id `create_better_wrench`）包含/衍生自下列第三方作品。

## Create (机械动力)
- Project: https://github.com/Creators-of-Create/Create
- Copyright (c) The Create Team / The Creators of Create
- **Code License: MIT** — 全文见 `licenses/Create-MIT.txt`
- **Assets License: All Rights Reserved**（适用于 Create 的 `src/main/resources/assets/**`）
- 本模组如何使用 Create：
  1. `client/WrenchToolSelection.java` 由 Create 的 `ToolSelectionScreen` 复刻/改写而来
     （该代码为 MIT 许可）。已保留版权声明；全文见 `licenses/Create-MIT.txt`。
  2. `client/WrenchHud.java` 与 `client/WrenchToolSelection.java` 在**运行时**引用
     `com.simibubi.create.foundation.gui.AllGuiTextures.HUD_BACKGROUND` 绘制 HUD 底纹。
     该纹理由**玩家安装的 Create 模组自己的 JAR**提供；本模组的 JAR 内**不包含**、
     也**不再分发**任何 Create 的资源文件（`assets/create/**`）。
- 本模组是 **非官方** 的 Create 扩展，与 Create 团队无隶属关系，也未获其背书或维护。

> 若将来在本项目中新增对其它第三方代码/素材的使用，请在此文件补充对应条目。
