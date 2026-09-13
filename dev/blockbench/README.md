# 用 Blockbench 做「万能扳手」的 3D 物品模型

> 本目录只在工程里做参考,**不参与打包**:只有 `src/main/resources/**` 下的文件才会进 jar。

## 一、先看原版(Create)扳手是怎么拼的

我把 Create 6.0.10 jar 里的模型拆开看了(`assets/create/models/item/wrench.json` → `wrench/item.json` + `wrench/gear.json`),构造如下:

| 部分 | 作用 |
|---|---|
| `handle` ×2 | 手柄(下半段细、上半段粗) |
| `axle` | 连接柄与头的**斜脖子**(绕 Y 轴 -45° 旋转的元素) |
| `top thing` / `bottom thing` | **就是那两根爪**(上下两块,错开形成钳口) |
| `gear case top` / `gear case` | 齿轮外壳(黄铜块) |
| `wrench/gear.json` | 那个**会转的齿轮** —— 单独一个模型文件 |

### 三个直接能省你半天的结论

1. **模型是竖直的,不是斜的。**
   物品栏里那个 45° 斜着的观感,**100% 来自 `display.gui` 的三轴旋转**,不是几何。
   ⇒ 你在 Blockbench 里把扳手**正着竖起来建**即可,"斜着摆"放进 Display 面板调。这样几何好建、UV 好排。

2. **父模型必须是 `minecraft:block/block`,不能用 `item/generated` / `item/handheld`。**
   - `item/generated` 那系带 `gui_light: "front"` ⇒ 3D 方块在物品栏里是**正面平光**,看着又扁又假;
   - `block/block` 是 `gui_light: "side"` ⇒ 有方向性明暗,**才有立体感**。
   - 用 `block/block` 后 `display` 要**七个槽全写**,否则会继承方块那套默认值(手里会变成托着一块砖)。
   - 起手模型 `better_wrench.starter.json` 已经按这个配好了。

3. **被旋转过的元素必须加 `neoforge_data: { "calculate_normals": true }`。**
   这是 NeoForge 扩展字段。元素一旦带 `rotation`,不写这个法线就是错的 ⇒ 光照诡异/发黑。Create 的 `axle`、`gear case`、齿轮模型全都带这个字段。
   (Blockbench 原生不认识这个字段,它可能提示未知数据或直接丢弃 —— 导入后需要补回去。)

## 二、Blockbench 操作流程

1. **Format 选 `Java Block/Item`**(不要选 Bedrock/Entity)。
2. 纹理面板新建 **16×16**(和原版一致;要用更大就把 `texture_size` 一起改)。
3. 建模:一个方块 = 16 单位,物品空间原点在左下前角,`x/z` 中心是 8,`y` 从 0(柄底)到 16(爪尖)。
4. **Display 面板**照下面填(等价于起手模型里的值):

   | 槽位 | rotation | translation | scale |
   |---|---|---|---|
   | thirdperson_righthand | `[0,-90,55]` | `[0,4,0.5]` | `[0.85,0.85,0.85]` |
   | thirdperson_lefthand | `[0,90,-55]` | `[0,4,0.5]` | `[0.85,0.85,0.85]` |
   | firstperson_righthand | `[0,-90,25]` | `[1.13,3.2,1.13]` | `[0.68,0.68,0.68]` |
   | firstperson_lefthand | `[0,90,-25]` | `[1.13,3.2,1.13]` | `[0.68,0.68,0.68]` |
   | ground | `[0,0,0]` | `[0,3,0]` | `[0.25,0.25,0.25]` |
   | **gui** | **`[30,-135,45]`** | `[0,0,0]` | `[1,1,1]` |
   | fixed | `[0,180,0]` | `[0,0,0]` | `[1,1,1]` |

   `gui` 那一行就是"物品栏里斜着躺"的来源:**Z 轴 ~45° 是翻滚(斜向)**,X 轴 ~30° 是俯角,Y 轴 -135° 是把侧面转向镜头。三个数随便调,试到你满意为止。

5. **导出**:`File → Export → Export Java Block/Item Model`;纹理另外导出 PNG。
6. 导出后**手工补两处**(Blockbench 不会写):
   - 顶部加 `"parent": "minecraft:block/block",`
   - 旋转过的元素补 `"neoforge_data": { "calculate_normals": true }`

> 更省事的做法:直接 `File → Import → Java Block/Item Model` 导入本目录的 `better_wrench.starter.json`,它就是按上面配好的六件套骨架(柄 / 金属环 / 斜脖子 / 钳座 / 两根爪),你在它上面改形状和贴图。

## 三、放进工程的两个文件

| 内容 | 目标路径 |
|---|---|
| 模型 JSON | `src/main/resources/assets/create_better_wrench/models/item/better_wrench.json` |
| 纹理 PNG | `src/main/resources/assets/create_better_wrench/textures/item/better_wrench.png` |

模型里 `textures` 的键可以随便取(起手模型用 `"5"`,和原版一致),但**值**必须是 `create_better_wrench:item/better_wrench`。

放好后告诉我,我负责 `gradle build` + 部署到测试客户端。

## 四、要不要做会转的齿轮

- **不要**:把齿轮当成一个**静态黄铜圆盘/方块**建在模型里就行,零代码,90% 的观感已经有了。
- **要**:那就必须写自定义渲染器(`CustomRenderedItemModelRenderer` + `PartialModel`,像 Create 那样单独一个 `gear` 模型文件再叠加渲染)。⚠️ 早期我们试过"包一层 Create 的渲染器",因为递归自调用直接 StackOverflow —— 要做就**自己从零写**,不要去包装 Create 的。

## 五、之前画的 2D 图标怎么办

3D 模型上线后,物品栏显示的就是这个 3D 模型,**2D 图标对物品本身就没用了**。但它还能复用成:

- 模组列表 / Modrinth 的图标 ⇒ `mods.toml` 的 `logoFile`(建议另存 128×128),待办清单里本来就缺这一项;
- 文档插图 / 封面。

所以 `images/wrench_icon_*.png` 先留着,不删。
