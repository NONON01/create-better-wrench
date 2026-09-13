# 用 Blockbench 做「万能扳手」的 3D 物品模型

> 这份目录只在工程里做参考,**不参与打包**:只有 `src/main/resources/**` 下的文件才会进 jar。

## 一、为什么不需要写 Java 代码

| 事实 | 说明 |
|---|---|
| 模组里没有自定义物品渲染器 | `CustomRenderedItemModel` / `IClientItemExtensions` / `ItemModelProvider` 全部为 0 命中 |
| 物品模型是手写资源 | `src/main/resources/assets/create_better_wrench/models/item/better_wrench.json`,没有 datagen 会覆盖它 |
| 注册名 = 模型路径 | 物品注册名 `create_better_wrench:better_wrench` ⇒ 模型固定取 `models/item/better_wrench.json` |

⇒ **把 Blockbench 导出的 JSON 覆盖到那个路径即可生效**,不用碰 Java。

> ⚠️ 反面教材:早期试过「包一层 Create 的扳手渲染器」来做手持外观,结果 `CustomRenderedItemModel` 递归自调用 → StackOverflow,已回滚。
> 原因是 Create 的扳手**不是普通 JSON 模型**,而是 `WrenchItemRenderer extends CustomRenderedItemModelRenderer` + `PartialModel.of("item/wrench/gear")` 的**拼装 + 动画(齿轮会转)**模型。
> **静态 3D 模型走普通模型路径,天然绕开这个坑。**

## 二、Blockbench 操作流程

1. **Format 选 `Java Block/Item`**(不要选 Bedrock/Entity)。
2. 打开纹理面板,新建一张 **32×32**(或 64×64)纹理。Blockbench 会自动维护 UV 图集。
   - 贴图尺寸用 2 的幂更稳(16/32/64/128)。
3. 建模:用 cube / mesh 把扳手搭出来。
   - 坐标参考:一个方块 = 16 单位,物品模型常用 `0..16` 区间,原点在左下前角。
4. **Display 面板必须设**,否则在 GUI / 手里会错位或巨大。
   - 懒人做法:直接**导入本目录的 `better_wrench.starter.json`**(`File → Import → Java Block/Item Model`),显示参数已经按原版 `item/handheld` 配好了,你在它基础上改几何就行。
   - 或者建完模型后手工填下面这套(等价于 `minecraft:item/handheld`):

     | 槽位 | rotation | translation | scale |
     |---|---|---|---|
     | thirdperson_righthand | `[0,-90,55]` | `[0,4,0.5]` | `[0.85,0.85,0.85]` |
     | thirdperson_lefthand | `[0,90,-55]` | `[0,4,0.5]` | `[0.85,0.85,0.85]` |
     | firstperson_righthand | `[0,-90,25]` | `[1.13,3.2,1.13]` | `[0.68,0.68,0.68]` |
     | firstperson_lefthand | `[0,90,-25]` | `[1.13,3.2,1.13]` | `[0.68,0.68,0.68]` |
     | ground | `[0,0,0]` | `[0,3,0]` | `[0.25,0.25,0.25]` |
     | gui / fixed | `[0,0,0]` | `[0,0,0]` | `[1,1,1]` |

5. **导出**:`File → Export → Export Java Block/Item Model`。
6. 另外把纹理导出成 PNG。

## 三、放进工程的两个文件

| 内容 | 目标路径 |
|---|---|
| 模型 JSON | `src/main/resources/assets/create_better_wrench/models/item/better_wrench.json` |
| 纹理 PNG | `src/main/resources/assets/create_better_wrench/textures/item/better_wrench.png` |

模型里 `textures` 应指向 `create_better_wrench:item/better_wrench`,和上表一致。

放好后告诉我,我负责 `gradle build` + 部署到测试客户端。

## 四、两个可选的小尾巴

- **`display` 可以省掉**:在导出的 JSON 顶部加一行 `"parent": "minecraft:item/handheld",` 并删掉整个 `display` 块,效果等价(靠父模型继承)。两种写法留一种即可,别同时改得互相打架。
- **`gui_light`**:建议保留 `"gui_light": "front"`,让物品栏里是正面打光,3D 模型在 GUI 里才不会发暗发灰。
- 想要**会转的齿轮**(像 Create 原版那样),就必须写自定义渲染器了,那是另一条路,到时候再说。

## 五、之前画的 2D 图标怎么办

3D 模型上线后,物品栏显示的就是这个 3D 模型,**2D 图标对物品本身就没用了**。但它还能复用成:

- 模组列表 / Modrinth 的图标 ⇒ `mods.toml` 的 `logoFile`(建议另存 128×128),待办清单里本来就缺这一项;
- 文档插图 / 封面。

所以 `images/wrench_icon_*.png` 先留着,不删。
