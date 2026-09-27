# Blockbench 工作流 ·「万能扳手」3D 物品模型

> 本目录仅供建模参考, **不参与构建**: 进入 jar 的只有 `src/main/resources/**`。
> 起手模型 `better_wrench.starter.json` 已按本文档配置好父模型与 display 全套参数。

## 1. 参考模型: Create 扳手的构成

Create 6.0.10 的扳手模型由 `assets/create/models/item/wrench.json` 拆分为 `wrench/item.json`
与 `wrench/gear.json` 两部分, 结构如下:

| 部件 | 作用 |
| --- | --- |
| `handle` ×2 | 手柄: 下半段细、上半段粗 |
| `axle` | 连接柄与头部的斜颈, 绕 Y 轴旋转 -45° 的元素 |
| `top thing` / `bottom thing` | 上下两根爪, 错开形成钳口 |
| `gear case top` / `gear case` | 齿轮外壳(黄铜块) |
| `wrench/gear.json` | 可转动的齿轮, 独立模型文件 |

## 2. 三条关键结论

1. **模型几何是竖直的, 不是斜的。**
   物品栏中 45° 的观感完全来自 `display.gui` 的三轴旋转, 与几何无关。
   因此建模时按竖直方向构建, 倾斜交由 Display 面板的旋转参数产生 —— 这样几何与 UV 都更易维护。

2. **父模型必须是 `minecraft:block/block`, 不能使用 `item/generated` 或 `item/handheld`。**
   - `item/generated` 系列带 `gui_light: "front"`, 3D 方块在物品栏中呈正面平光, 缺乏立体感;
   - `block/block` 为 `gui_light: "side"`, 具有方向性明暗。
   - 采用 `block/block` 后必须**写全 display 的七个槽位**, 否则会继承方块默认值(手持时表现为托着一块砖)。
   - 起手模型已按此配置。

3. **被旋转过的元素必须加 `neoforge_data: { "calculate_normals": true }`。**
   该字段为 NeoForge 扩展: 元素一旦带 `rotation` 而不写此字段, 法线不正确, 光照会异常或发黑。
   Create 的 `axle`、`gear case` 与齿轮模型均带该字段。
   Blockbench 原生不识别该字段, 可能提示未知数据或直接丢弃 ⇒ 导入后需补回。

## 3. 建模流程

1. Format 选 `Java Block/Item`(不使用 Bedrock / Entity)。
2. 纹理面板新建 16×16(与原版一致; 使用更大尺寸时需同步修改 `texture_size`)。
3. 建模比例: 一个方块 = 16 单位; 物品空间原点位于左下前角; `x/z` 中心为 8; `y` 从 0(柄底)到 16(爪尖)。
4. Display 面板按下表填写(与起手模型一致):

   | 槽位 | rotation | translation | scale |
   |---|---|---|---|
   | thirdperson_righthand | `[0,-90,55]` | `[0,4,0.5]` | `[0.85,0.85,0.85]` |
   | thirdperson_lefthand | `[0,90,-55]` | `[0,4,0.5]` | `[0.85,0.85,0.85]` |
   | firstperson_righthand | `[0,-90,25]` | `[1.13,3.2,1.13]` | `[0.68,0.68,0.68]` |
   | firstperson_lefthand | `[0,90,-25]` | `[1.13,3.2,1.13]` | `[0.68,0.68,0.68]` |
   | ground | `[0,0,0]` | `[0,3,0]` | `[0.25,0.25,0.25]` |
   | **gui** | **`[30,-135,45]`** | `[0,0,0]` | `[1,1,1]` |
   | fixed | `[0,180,0]` | `[0,0,0]` | `[1,1,1]` |

   `gui` 行三个旋转角的含义: Z 轴约 45° 为翻滚(形成斜向观感), X 轴约 30° 为俯角,
   Y 轴 -135° 将侧面转向镜头。三个值均按观感调整。

5. 导出: `File → Export → Export Java Block/Item Model`; 纹理另行导出 PNG。
6. 导出后需手工补充两处(Blockbench 不会写入):
   - 顶部补 `"parent": "minecraft:block/block",`
   - 旋转过的元素补 `"neoforge_data": { "calculate_normals": true }`

> 简化做法: 直接 `File → Import → Java Block/Item Model` 导入本目录的
> `better_wrench.starter.json`。该文件已按上述配置提供骨架(柄 / 金属环 / 斜颈 / 钳座 / 两根爪),
> 在其基础上调整形状与贴图即可。

## 4. 产物落位

| 内容 | 目标路径 |
|---|---|
| 模型 JSON | `src/main/resources/assets/create_better_wrench/models/item/better_wrench.json` |
| 纹理 PNG | `src/main/resources/assets/create_better_wrench/textures/item/better_wrench.png` |

模型 `textures` 的键可任意命名(起手模型沿用原版的 `"5"`), 但**值**必须为
`create_better_wrench:item/better_wrench`。

落位后由构建流程执行 `gradle build` 并部署到测试客户端。

## 5. 可选项: 会转动的齿轮

- **不实现**: 将齿轮建成静态黄铜圆盘/方块即可, 无需代码, 观感已接近成品。
- **实现**: 需编写自定义渲染器(`CustomRenderedItemModelRenderer` + `PartialModel`, 与 Create 的做法一致,
  单独的 `gear` 模型文件再叠加渲染)。已知限制: 曾尝试包装 Create 的渲染器, 因递归自调用导致
  `StackOverflowError`; 如需该效果应自行实现, 不应包装 Create 的渲染器。

## 6. 既有 2D 图标的去向

3D 模型上线后, 物品栏显示的是该模型, 2D 图标不再用于物品本身, 但仍可复用为:

- 模组列表与 Modrinth 的项目图标 ⇒ `mods.toml` 的 `logoFile`(建议另存为 128×128, 该项在待办清单中尚未完成);
- 文档插图与封面。

因此 `images/wrench_icon_*.png` 保留, 不删除。
