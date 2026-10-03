# libs/

`Ponder-Forge-1.20.1-1.0.91.jar`, `flywheel-forge-1.20.1-1.0.6-beta-266.jar` 与
`Registrate-MC1.20-1.3.3.jar` 是从 Create 1.20.1 的 `all` 产物内的 `META-INF/jars/` 解出的嵌入库。

* 用途: **仅编译期**。公开 maven 上没有 1.20.1 的独立坐标(`ponder-forge` 等只有 1.21.1 及以后),
  而本模组的思索与工具类需要这些类;
* 运行期: 由 Create 自身的 jar-in-jar 机制提供同一批库, 本模组不重新分发它们;
* 许可: Flywheel 与 Ponder 为 MIT, Registrate 为 MIT(以各自 jar 内的许可声明为准)。
