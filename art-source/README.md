# art-source/ · 美术原始素材(不参与构建)

> 这个目录**不是** mod 资源目录 —— 它不参与 `gradle build`、**不会被打进 jar**。
> 打进 jar 的只有 `src/main/resources/**`(构建脚本里 `sourceSets.main.resources` 只含
> `src/main/resources` / `src/generated/resources` / `src/create/resources`)。

## 里面是什么

`手绘/` = **5 张 HUD 模式图标的手绘原图**(16×16 PNG, 作者原创), 也就是 `LICENSE.md` **§2.3**
那张"原图 → 出货贴图"对照表的**凭证**。

它们原先只存在于工作区 `E:/dsh/work/Java/images/手绘/`(**不在 git 里, 删了不可恢复**),
2026-09-23 复制到本目录以便版本保护; 工作区那份仍然保留, 两份内容逐字节一致(SHA256 已核对)。

## 与出货贴图的对应关系(逐像素实测, 差异见 LICENSE.md §2.3)

`scripts/gen_mode_wrench_icon.ps1` **只做换色**(黑→透明 / 蓝→黑 / 其余原样), 不画画:

| 本目录的原图 | 出货贴图(`src/main/resources/assets/create_better_wrench/textures/gui/`) | 比对 |
| --- | --- | --- |
| `手绘/扳手.png` | `mode_wrench.png` | 差异 0(精确同源) |
| `手绘/拆除.png` | `mode_deconstruct.png` | 差异 0(精确同源) |
| `手绘/工作.png` | `mode_assemble.png` | 差异 0(精确同源) |
| `手绘/mod描述.png` | `mode_coming_soon.png` | 差异 0(精确同源) |
| `手绘/连接.png` | `mode_connect.png` | 高度接近(脚本跑完后又手工动过几个像素) |
| `手绘/曲柄.png` | —(**未使用**) | — |
| `手绘/物流网络.png` | —(**未使用**) | — |

生成命令:

```powershell
powershell -File scripts/gen_mode_wrench_icon.ps1 -Source "art-source/手绘/扳手.png" `
    -Out "src/main/resources/assets/create_better_wrench/textures/gui/mode_wrench.png"
```

## 注意事项

1. ⚠️ **不要改文件名 / 不要移动** —— `LICENSE.md` §2.3 的对照表按这些文件名引用它们;
2. ⚠️ **不要放进 `src/main/resources`** —— 原图不需要随 jar 出货(出货的是换色成品), 放进去会让 jar 白白变大、
   也会让"原图不重分发"这句话变得不准确;
3. 若日后把本仓库**公开**(推 GitHub 等): 这些原图会随仓库一起公开。它们是**作者原创**,
   整个 mod 的许可是 **All Rights Reserved**(见 `LICENSE.md` §1), 所以一致、无冲突 —— 但你要清楚这一点;
4. 新增第三方素材时, 按 `LICENSE.md` §2 末尾那句"add its entry to §2"补登记, **不要**直接丢进本目录当作自有素材。
