# 恒生本地工具台

前后端分离的本机开发辅助工具：后端 Spring Boot REST，前端 Vue3 + Vite + Element Plus。

## 结构

| 路径 | 说明 |
|------|------|
| `src/` | Spring Boot 后端（入口 `com.zhoufz.tools.StartApp`，端口 **18080**） |
| `web/` | Vue3 前端（Vite 开发端口 **5173**） |

## 启动后端

要求：JDK 8+、Maven。

```bash
# 在仓库根目录
mvn -DskipTests spring-boot:run
# 或 IDE 直接运行 StartApp
```

成功后访问示例：`http://127.0.0.1:18080/hundsun/db/categories`

本地库相关配置见 `src/main/resources/application.properties`（MySQL 路径、账号等）。

## 启动前端

要求：Node.js 18+（建议）。

```bash
cd web
npm install
npm run dev
```

或用本机脚本（启停与 HUI nginx 脚本同目录）：

```bash
/Users/zhoufz/hundsun/lcpt60/scripts/start-tools-web.sh start   # 默认
/Users/zhoufz/hundsun/lcpt60/scripts/start-tools-web.sh stop
/Users/zhoufz/hundsun/lcpt60/scripts/start-tools-web.sh status
```

浏览器打开 **http://127.0.0.1:5173/**（日志在 `lcpt60/scripts/.run/tools-web.log`）。

Vite 已将 `/hundsun` 代理到 `http://127.0.0.1:18080`，前端请求同源路径即可，无需改后端地址。

## 前端菜单

- `/db` — 本地库初始化
- `/git` — Git / SVN 批量更新、切换分支、大包到小包代码替换
- `/config` — 配置备份/还原

## 说明

- 不使用 HUI / 公司内部前端包，仅用开源 Vue 生态。
- 生产构建：`cd web && npm run build`，产物在 `web/dist/`（当前以本地开发代理为主）。
