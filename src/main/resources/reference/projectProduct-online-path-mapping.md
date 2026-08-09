# 联机银行个性化代码：工程目录 vs 产品目录路径梳理

## 一、核心差异（需路径映射）

工程中银行 **Java** 常放在 `*-bootstrap-adapter` 微服务；产品中合并进对应的 `*-bootstrap`。
`src/main/resources/bank/{bankCode}/` 配置（SpringContext.xml）**两边均在 `*-bootstrap`**，不做 adapter 后缀转换。

| 子系统 | 工程模块（Java） | 产品模块（Java） | Java 包路径 |
|--------|------------------|------------------|-------------|
| 基金代销-交易批量 | `lcpt-dxfund-trans/lcpt-dxfund-batch-bootstrap-adapter` | `lcpt-dxfund-trans/lcpt-dxfund-batch-bootstrap` | `...dxfund.batch.bank.{code}.*` |
| 基金代销-联机交易 | `lcpt-dxfund-trans/lcpt-dxfund-online-trade-bootstrap-adapter` | `lcpt-dxfund-trans/lcpt-dxfund-online-trade-bootstrap` | `...dxfund.online.bank.{code}.*` |
| 基金代销-联机查询 | `lcpt-dxfund-trans/lcpt-dxfund-online-query-bootstrap-adapter` | `lcpt-dxfund-trans/lcpt-dxfund-online-query-bootstrap` | 同上 |
| 基金代销-公共批量 | `lcpt-dxfund-pub/lcpt-dxfund-pub-batch-bootstrap-adapter` | `lcpt-dxfund-pub/lcpt-dxfund-pub-batch-bootstrap` | `...dxfund.pub.batch.bank.{code}.*` |
| 基金代销-公共联机 | `lcpt-dxfund-pub/lcpt-dxfund-pub-online-bootstrap-adapter` | `lcpt-dxfund-pub/lcpt-dxfund-pub-online-bootstrap` | `...dxfund.pub.online.bank.{code}.*` |
| 理财-联机/批量 | `lcpt-fina-trans/lcpt-fina-*-bootstrap-adapter` | `lcpt-fina-trans/lcpt-fina-*-bootstrap` | `...fina.*.bank.{code}.*` |
| 银保-联机/批量 | `lcpt-insure-trans/lcpt-insure-*-bootstrap-adapter` | `lcpt-insure-trans/lcpt-insure-*-bootstrap` | 包名含 `adapter.bank.{code}` 保持不变 |
| 银保-公共批量 | `lcpt-insure-pub/lcpt-insure-pub-batch-bootstrap-adapter` | `lcpt-insure-pub/lcpt-insure-pub-batch-bootstrap` | 同上 |

**映射规则（代码已实现于 `OnlinePathMapper`）：**

- 工程→产品：`...-bootstrap-adapter/.../bank/{code}/...` → `...-bootstrap/...`
- 产品→工程：Java 且 `...-bootstrap/.../bank/{code}/...` → `...-bootstrap-adapter/...`；若 adapter 模块不存在则回退到 bootstrap

## 二、路径一致（无需映射）

| 类型 | 路径模式 |
|------|----------|
| 公共子系统 | `lcpt-pub/lcpt-pub-common/lcpt-pub-online-adapter/.../pub/online/bank/{code}/` |
| 公共批量 | `lcpt-pub/lcpt-pub-common/lcpt-pub-batch-adapter/.../pub/batch/bank/{code}/` |
| 公共 core | `lcpt-pub/lcpt-pub-common/lcpt-pub-core/.../pub/bank/{code}/` |
| 子系统 core | `lcpt-dxfund-trans/lcpt-dxfund-core/.../dxfund/bank/{code}/` |
| 子系统 pub-core | `lcpt-dxfund-pub/lcpt-dxfund-pub-core/.../pub/dxfund/bank/{code}/` |
| 管理台 | `lcpt-web/lcpt-web-manager-*/lcpt-web-manager-*-bank/.../bank/{code}/` |
| 管理台 bizframe | `lcpt-web/lcpt-web-bizframe/lcpt-web-bizframe-bank/.../bank/{code}/` |
| 常量类 | `pub/lcpt-base/lcpt-base-constant/.../constant/bank/{code}/` |

## 三、同步过滤

- **银行简称**：路径须含 `/bank/{bankCode}/` 或 `/resources/bank/{bankCode}/`
- **SQL**：`spsql`、`sql` 目录及 `.sql/.upd/.vm` 不同步
- **前端**：仅 `views/bank/{code}/*.vue`、`router/bank/{code}/*.js`、reConfirm 下银行路由
- **前端包名映射**（`FrontPathMapper`）：产品 `console-{biz}-vue` ↔ 工程 `console-{biz}-bank-vue`（如 `console-dxfund-vue` → `console-dxfund-bank-vue`）
- **前端内容改写**（产品→工程）：`@ConsoleXxxVue/views|router/bank/` → `@ConsoleXxxBankVue/...`；chunk 名 `console-xxx-vue/bank/` → `console-xxx-bank-vue/bank/`；`@ConsoleXxxVue/components|api` 保持不变

## 四、示例（mtsh_fundinsure）

工程：
```
sale/lcpt-dxfund/lcpt-dxfund-pub/lcpt-dxfund-pub-batch-bootstrap-adapter/
  src/main/java/.../pub/batch/bank/mtsh_fundinsure/impl/MTSH210210GetHostFileService.java
```

产品：
```
sale/lcpt-dxfund/lcpt-dxfund-pub/lcpt-dxfund-pub-batch-bootstrap/
  src/main/java/.../pub/batch/bank/mtsh_fundinsure/impl/MTSH210210GetHostFileService.java
```

仅微服务目录名 `-adapter` 差异，`bank/mtsh_fundinsure` 之后结构一致。
