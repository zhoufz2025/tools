# Cursor 提示词：产品包按工程包 GAV 版本 install 到本地仓

> 用法：在 Cursor 打开工作区 `/Users/zhoufz/hundsun/tools`，把下面「提示词正文」整段发给 Agent 执行。  
> 背景文档：`/Users/zhoufz/hundsun/学习文档/产品包改完工程包联调-Jar未更新排查手册.md`

---

## 提示词正文（复制从这里开始）

```text
请在本仓库 /Users/zhoufz/hundsun/tools 新增功能：「产品包按工程包版本 install 到本地 Maven 仓」。

## 业务目标

产品源码版本常固定（如 7.4.0-SNAPSHOT），工程包 BOM 跟最新（如 7.5.0 → 解析为 7.5.0-SNAPSHOT）。
需要一键：
1) 清空本地仓中「目标 GAV 版本」目录（去掉私服时间戳包）
2) 对产品模块 versions:set 成工程包需要的 version
3) mvn clean install -DskipTests 装进本地仓
4) versions:set 改回产品原版本（源码不脏）
5) 回显日志与结果校验（本地是否存在 …-SNAPSHOT.jar，是否已无 20*.jar 时间戳包）

这是本机联调能力，不要改产品/工程包业务代码。

## 对齐现有工程风格

- 后端：Spring Boot，`com.zhoufz.tools.*`，统一返回 `com.zhoufz.tools.dto.Result`
- API 前缀：`/hundsun/...`（参考 PackageCopyController）
- 前端：Vue3 + Element Plus；菜单加在 `web/src/layouts/MainLayout.vue`；路由 `web/src/router/index.js`
- 复用已有路径选择组件（如 PathFolderInput）若合适
- 更新根目录 README.md「前端菜单」一节
- 可在 `application.properties` 增加默认配置项

## 默认本机参数（可配置、可在页面改）

- Maven settings：`/opt/homebrew/Cellar/maven/3.9.9/libexec/conf/settings_hs.xml`
- 同时传 `--global-settings` 与 `-s` 指向同一文件
- 本地仓库：`/opt/homebrew/Cellar/maven/3.9.9/libexec/repo`
- 产品模块示例 pom：
  `/Users/zhoufz/hundsun/lcpt60/git/Sources/app/lcpt-server/pub/lcpt-jres/lcpt-core/pom.xml`
- 默认目标版本：`7.5.0-SNAPSHOT`
- groupId 默认从模块 pom 读，一般为 `com.hundsun.lcpt`

## 页面（建议路由 `/maven-install-product`）

标题：产品包 Install（对齐工程包版本）

表单字段：
1. 产品模块目录或 pom.xml 路径（必填；若给目录则用其下 pom.xml）
2. 目标版本 targetVersion（必填，如 7.5.0-SNAPSHOT）——工程包正在用的版本
3. 原版本 originalVersion（可选；不填则从当前 pom 的 project.version / parent 推断，install 后改回此版本）
4. Maven settings 路径
5. 本地仓库 localRepo 路径
6. 勾选：执行前清空目标版本目录（默认勾选）
7. 勾选：install 成功后改回原版本（默认勾选）
8. 高级可选：额外 Maven 参数（默认 `-DskipTests`；不要默认加 -o，除非用户勾选离线）

按钮：
- 「预览」：只解析 GAV、计算将清空的目录、将执行的命令列表，不改磁盘、不跑 mvn
- 「执行」：按步骤真正执行；按钮防重复提交；展示实时/分段日志

结果区展示：
- groupId / artifactId / 原版本 / 目标版本
- 清空的目录路径
- 最终 jar 路径与文件大小
- 是否还存在时间戳包 `*-YYYYMMDD.HHMMSS-*.jar`
- 源码 pom 当前 version（应已改回 originalVersion）
- 完整命令与退出码；失败时保留已产生日志，并尽量把 pom 版本恢复回 originalVersion（finally）

## 后端流程（必须严格按序）

对「执行」：

1. 校验路径存在；解析 artifactId、groupId、当前 version
2. 计算本地目标目录：
   `{localRepo}/{groupId划路径}/{artifactId}/{targetVersion}/`
   例：`.../repo/com/hundsun/lcpt/lcpt-core/7.5.0-SNAPSHOT/`
3. 安全校验（硬性）：
   - localRepo 必须等于配置的仓库根，或至少是其规范化后的子路径
   - 只允许删除「该 artifactId + targetVersion」这一层目录内容，禁止删到 artifactId 上级或整个 repo
   - groupId 建议限制为 `com.hundsun.lcpt`（若用户填了别的，二次确认或直接拒绝并提示）
4. 若勾选清空：`rm -rf` 该 targetVersion 目录后 `mkdir -p` 重建
5. 在模块目录执行：
   `mvn -s SETTINGS --global-settings SETTINGS versions:set -DnewVersion={targetVersion} -DgenerateBackupPoms=false`
6. 同目录：
   `mvn -s SETTINGS --global-settings SETTINGS -DskipTests clean install`
7. 若勾选改回：
   `mvn -s SETTINGS --global-settings SETTINGS versions:set -DnewVersion={originalVersion} -DgenerateBackupPoms=false`
8. 校验：存在 `{artifactId}-{targetVersion}.jar`；尽量不存在 `-{targetVersion 去掉 -SNAPSHOT 后}-20*.jar` 形态的时间戳包；若 install 后又出现 maven-metadata-nexus.xml，可再删 nexus 元数据与时间戳包一次（可选增强）
9. 任一 mvn 非 0：标记失败；finally 中若 pom 版本不是 originalVersion 则尝试改回

实现建议：
- Service 用 ProcessBuilder，工作目录=模块目录，合并 stdout/stderr，编码 UTF-8
- 超时可配置（默认如 30 分钟）
- 不要在工具里改产品业务 Java；只跑 Maven + 清理本地仓指定目录

## API 建议

- POST `/hundsun/maven/product-install/preview`
- POST `/hundsun/maven/product-install/execute`
请求体字段与表单一致；响应用 Result，data 含 commands、paths、log、checks。

## 验收

1. 用 lcpt-core：目标 7.5.0-SNAPSHOT，原 7.4.0-SNAPSHOT，勾选清空+改回
2. 执行后本地仅有（或至少包含）`lcpt-core-7.5.0-SNAPSHOT.jar`，无 `lcpt-core-7.5.0-20*.jar`
3. 产品 pom.xml 版本仍为 7.4.0-SNAPSHOT
4. README 与菜单可从 UI 进入该页

先读现有 Controller/Result/前端菜单路由再改；最小改动完成功能；不要引入无关重构。
```

## 提示词正文（复制到这里结束）

---

## 补充（可选，粘贴提示词后追加）

若 Agent 完成后你还想加能力，可再发：

- 支持一次勾选多个产品模块（fina-core、pub-core…）串行执行  
- 从工程包 `lcpt.product.version` / BOM 自动推断 targetVersion  
- 执行后提示：「IDEA 请关闭 Always update snapshots 并 Reload」
