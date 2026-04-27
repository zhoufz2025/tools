# tbdxfundtranscfm 表使用场景及重要字段分析

## 一、表的基本信息

### 1.1 表名
`tbdxfundtranscfm` - 代销基金交易确认表（交易类确认临时表）

### 1.2 表的作用
用于存储代销基金交易的确认数据，是交易确认的核心表。该表在日终清算时作为数据源，汇总生成交易汇总表（`tbdxfundtranssum`），进而用于资金划付处理。

### 1.3 表的分表规则
该表支持分表存储，根据不同的清算模式有不同的表名规则：

- **默认模式（清算临时表公用）**：`tbdxfundtranscfm00{tableNum}`（如：`tbdxfundtranscfm001`、`tbdxfundtranscfm002`、...、`tbdxfundtranscfm0016`）
- **分TA模式（清算表临时表分TA创建）**：`tbdxfundtranscfm{TA代码}{tableNum}`（如：`tbdxfundtranscfm0011`、`tbdxfundtranscfm0012`、...）
- **7*24模式（7*24或非日终清算）**：`tbdxfundtranscfm{tableNum}`（如：`tbdxfundtranscfm1`、`tbdxfundtranscfm2`、...、`tbdxfundtranscfm16`）

**分表数量**：支持1-16个分表，通过 `table_num` 字段标识分表号。

### 1.4 tbdxfundtranscfm 与 tbdxfundtranscfm00 的关系

#### 1.4.1 表的关系说明

**tbdxfundtranscfm**（正式表）和 **tbdxfundtranscfm00**（临时备份表）是同一套表结构的不同用途：

- **tbdxfundtranscfm{tableNum}**：正式表，用于存储日常交易确认数据
- **tbdxfundtranscfm00{tableNum}**：临时备份表，用于日终清算时的数据备份和汇总

**表名确定逻辑**（通过 `TransVar200007.getTbTransCfmBak()` 方法获取）：

1. **7*24模式或非日终清算**：
   - 返回：`tbdxfundtranscfm`
   - 实际表名：`tbdxfundtranscfm{tableNum}`（如：`tbdxfundtranscfm1`、`tbdxfundtranscfm2`）
   - 说明：7*24模式下，正式表和临时表使用相同的表名

2. **清算表临时表分TA创建模式**：
   - 返回：`tbdxfundtranscfm{TA代码}`（如：`tbdxfundtranscfm001`）
   - 实际表名：`tbdxfundtranscfm{TA代码}{tableNum}`（如：`tbdxfundtranscfm0011`、`tbdxfundtranscfm0012`）
   - 说明：每个TA有独立的临时表

3. **清算临时表公用模式**（默认）：
   - 返回：`tbdxfundtranscfm00`
   - 实际表名：`tbdxfundtranscfm00{tableNum}`（如：`tbdxfundtranscfm001`、`tbdxfundtranscfm002`）
   - 说明：所有TA共用临时表，通过 `ta_code` 区分

#### 1.4.2 数据流转过程

**完整的数据流转过程**（包含TA文件导入）：

```
阶段1：TA文件导入
TA确认文件（04类型文件）
    ↓ [PUB210103导入确认文件]
tbdxfundtranscfmtmp（文件导入临时表）
    ↓ [Action207000数据准备]
阶段2：转移到清算临时表
tbdxfundtranscfm00{tableNum}（清算临时备份表）
    ↓ [TransCfmDealTask数据备份]
阶段3：数据备份和合并
tbdxfundtranscfm{tableNum}（正式表）
    ↓ [备份已有数据]
tbdxfundtranscfm00{tableNum}（清算临时备份表，包含文件导入数据和正式表备份数据）
    ↓ [TransCfmDealTask数据回写]
阶段4：数据回写
tbdxfundtranscfm{tableNum}（正式表）
    ↓ [TranssumPaymentFunc数据汇总]
阶段5：数据汇总
tbdxfundtranssum（交易汇总表）
    ↓ [更新/插入]
tbdxfundpayment（资金划付表）
```

**详细流程**：

1. **TA文件导入阶段**（PUB210103）：
   - TA生成确认文件（04类型文件）
   - 系统取文件并导入到 `tbdxfundtranscfmtmp` 表
   - 目的：暂存从TA文件导入的原始数据

2. **数据准备阶段**（Action207000，DXFUND210104）：
   - 从文件导入临时表 `tbdxfundtranscfmtmp` 转移到清算临时表 `tbdxfundtranscfm00{tableNum}`
   - SQL：`INSERT INTO tbdxfundtranscfm00{tableNum} SELECT * FROM tbdxfundtranscfmtmp WHERE ta_code = ? AND table_num = ?`
   - 目的：将文件导入的数据转移到清算临时表，为后续清算做准备

3. **数据备份阶段**（TransCfmDealTask，DXFUND210110）：
   - 从正式表 `tbdxfundtranscfm{tableNum}` 备份当日确认数据到清算临时表 `tbdxfundtranscfm00{tableNum}`
   - SQL：`INSERT INTO tbdxfundtranscfm00{tableNum} SELECT ... FROM tbdxfundtranscfm{tableNum} WHERE cfm_date = ? AND ta_code = ? AND NOT EXISTS ...`
   - 目的：保护正式表中已有的确认数据，与文件导入数据合并

4. **数据合并阶段**：
   - 此时清算临时表 `tbdxfundtranscfm00{tableNum}` 包含：
     - 从文件导入的数据（阶段2转入）
     - 从正式表备份的数据（阶段3备份）
   - 目的：形成完整的确认数据集，统一处理

5. **数据汇总阶段**（TranssumPaymentFunc）：
   - 从清算临时表 `tbdxfundtranscfm00{tableNum}` 汇总数据到 `tbdxfundtranssum`
   - SQL：`INSERT INTO tbdxfundtranssum SELECT ... FROM tbdxfundtranscfm00{tableNum} WHERE cfm_date = ? AND ta_code = ? GROUP BY ...`
   - 目的：生成交易汇总数据，用于资金划付

6. **数据回写阶段**（TransCfmDealTask）：
   - 从清算临时表 `tbdxfundtranscfm00{tableNum}` 回写到正式表 `tbdxfundtranscfm{tableNum}`
   - SQL：`INSERT INTO tbdxfundtranscfm{tableNum} SELECT ... FROM tbdxfundtranscfm00{tableNum} WHERE ...`
   - 目的：更新正式表数据（包括新增字段、状态更新等）

7. **数据清理阶段**（TransCfmDealTask）：
   - 删除正式表中已备份的数据：`DELETE FROM tbdxfundtranscfm{tableNum} WHERE cfm_date = ? AND ta_code = ? AND EXISTS (SELECT 1 FROM tbdxfundtranscfm00{tableNum} ...)`
   - 临时表数据在汇总完成后可以删除（根据参数 `DELETE_TEMPORARY_TABLE` 决定）

#### 1.4.3 三个表的关系总结

**tbdxfundtranscfmtmp、tbdxfundtranscfm00、tbdxfundtranscfm 三个表的关系**：

| 表名 | 表的作用 | 数据来源 | 数据去向 | 生命周期 |
|------|---------|---------|---------|---------|
| **tbdxfundtranscfmtmp** | 文件导入临时表 | TA确认文件（04类型） | tbdxfundtranscfm00 | 文件导入后，转移到00表后可删除 |
| **tbdxfundtranscfm00{tableNum}** | 清算临时备份表 | 1. tbdxfundtranscfmtmp（文件导入数据）<br>2. tbdxfundtranscfm（正式表备份数据） | 1. tbdxfundtranscfm（正式表）<br>2. tbdxfundtranssum（汇总表） | 日终清算期间，汇总完成后可删除 |
| **tbdxfundtranscfm{tableNum}** | 正式表 | 1. tbdxfundtranscfm00（清算回写）<br>2. 实时确认（7*24模式） | tbdxfundtranscfm00（备份） | 永久存储 |

**数据流转的关键节点**：

1. **文件导入节点**（PUB210103）：
   - TA文件 → `tbdxfundtranscfmtmp`
   - 作用：暂存文件导入的原始数据

2. **数据准备节点**（Action207000）：
   - `tbdxfundtranscfmtmp` → `tbdxfundtranscfm00`
   - 作用：将文件导入数据转移到清算临时表

3. **数据备份节点**（TransCfmDealTask）：
   - `tbdxfundtranscfm` → `tbdxfundtranscfm00`
   - 作用：备份正式表已有数据，与文件导入数据合并

4. **数据汇总节点**（TranssumPaymentFunc）：
   - `tbdxfundtranscfm00` → `tbdxfundtranssum`
   - 作用：从清算临时表汇总数据

5. **数据回写节点**（TransCfmDealTask）：
   - `tbdxfundtranscfm00` → `tbdxfundtranscfm`
   - 作用：将处理后的数据回写到正式表

**tbdxfundtranscfm00表的核心作用**：
- **数据中转站**：作为文件导入数据和正式表数据的中转站
- **数据合并点**：合并文件导入数据和正式表备份数据
- **数据汇总源**：作为数据汇总的数据源，避免直接操作正式表
- **数据保护层**：保护正式表数据，确保清算过程不影响正式表

#### 1.4.4 TransVar200007.getTbTransCfmBak() 方法说明

**方法作用**：获取交易确认临时备份表的基础表名

**返回值**：
- 7*24模式：`tbdxfundtranscfm`
- 分TA模式：`tbdxfundtranscfm{TA代码}`
- 默认模式：`tbdxfundtranscfm00`

**使用方式**：
- 在代码中通过 `transVar.getTbTransCfmBak() + tableNum` 获取完整的临时表名
- 例如：`transVar.getTbTransCfmBak() + "1"` → `tbdxfundtranscfm001`（默认模式）

**主要使用场景**：
1. **Action207000**：从 `tbdxfundtranscfmtmp` 转移到 `tbdxfundtranscfm00`
2. **TransCfmDealTask**：数据备份和回写
3. **TranssumPaymentFunc**：数据汇总
4. **其他日终清算任务**：需要访问临时表数据的地方

## 二、重要字段说明

### 2.1 主键字段

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| ta_code | TA代码 | VARCHAR2(18) | 登记机构代码，主键之一 |
| cfm_date | 确认日期 | INTEGER | 交易确认日期，格式：YYYYMMDD，主键之一 |
| cfm_no | TA确认流水号 | VARCHAR2(32) | TA返回的确认流水号，主键之一（分红数据该字段可能为空） |
| bank_no | 银行代码 | VARCHAR2(32) | 银行代码，租户编号（多租户模式用），主键之一 |

**注意**：
- 数据库表结构定义中的主键为：`(ta_code, cfm_date, cfm_no)`
- 但在实际业务操作中（删除、更新、查询），还会使用 `bank_no`（银行代码）和 `in_client_no`（内部客户编号）作为条件，用于多租户和分表场景
- 因此，实际业务主键可以理解为：`(ta_code, cfm_date, cfm_no, bank_no, in_client_no)`

### 2.2 业务标识字段

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| serial_no | 流水序号 | VARCHAR2(32) | 系统内部流水号，TA发起时同cfm_no |
| ori_cfm_no | 原确认流水号 | VARCHAR2(32) | 指定赎回时使用 |
| from_flag | 发起方 | VARCHAR2(1) | [K_FQF] 0-本系统发起 1-TA发起 2-其他销售商发起 |
| trans_code | 交易代码 | VARCHAR2(32) | 交易代码 |
| busin_code | 业务代码 | VARCHAR2(6) | 业务类型代码，如：120-认购、122-申购、139-定投、887-其他认申购、124-赎回等 |
| status | 状态 | VARCHAR2(1) | [K_JYZT] 6-部分确认未全部返回 7-部分确认已全部返回 8-确认成功 9-确认失败 A-认购确认 B-份额调账 D-分红数据 |

### 2.3 日期时间字段

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| trans_date | 交易日期 | INTEGER | 交易日期，分红数据为权益登记日 |
| trans_time | 交易时间 | INTEGER | 交易时间 |
| clear_date | 清算日期 | INTEGER | 清算日期 |
| asso_date | 关联日期 | INTEGER | 预约赎回日期/冻结截止日期/自动申购日期/撤单，解冻时原请求日期 |
| host_date | 核心日期 | INTEGER | 主机日期 |
| bta_check_date | 分销对账日期 | INTEGER | 分销对账日期 |

### 2.4 客户信息字段

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| in_client_no | 内部客户编号 | VARCHAR2(20) | 系统内部客户编号，用于分表路由 |
| client_type | 客户类型 | VARCHAR2(1) | [K_KHLX] 0-机构 1-个人 |
| client_no | 银行客户号 | VARCHAR2(24) | 银行客户号 |
| client_name | 客户姓名 | VARCHAR2(250) | 客户名称 |
| asset_acc | 理财账号 | VARCHAR2(20) | 理财账号 |
| bank_acc | 资金账号 | VARCHAR2(64) | 资金账号 |
| entity_bank_acc | 实体卡号 | VARCHAR2(64) | 实体银行卡号（组合宝等业务使用） |
| ta_client | TA交易账号 | VARCHAR2(32) | TA交易账号 |
| bank_no | 银行代码 | VARCHAR2(32) | 银行代码，租户编号（多租户模式用） |

### 2.5 产品信息字段

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| prd_code | 产品代码 | VARCHAR2(32) | 基金产品代码 |
| share_class | 份额类别 | VARCHAR2(3) | 保留字段 |
| nav | 单位净值 | NUMBER(18,8) | 单位净值 |
| price | 交易价格 | NUMBER(22,12) | 交易价格，分红数据为单位基金分红金额（含税） |
| curr_type | 币种 | VARCHAR2(3) | 币种，如：156-人民币、840-美元、344-港元、978-欧元等 |

### 2.6 金额和份额字段（核心业务字段）

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| amt | 金额 | NUMBER(18,2) | 交易金额，分红数据表示红利总金额 |
| **cfm_amt** | **确认金额** | **NUMBER(18,2)** | **确认的交易金额，红利数据为实发红利。用于汇总到tbdxfundtranssum，进而更新tbdxfundpayment的cfm_amt字段** |
| vol | 份额 | NUMBER(18,3) | 交易份额，分红数据表示红利红股基数 |
| **cfm_vol** | **确认份额** | **NUMBER(18,3)** | **确认的交易份额，红利数据为红利再投资基金份数** |
| post_vol | 交易后份额 | NUMBER(18,3) | 交易后份额 |
| vol_cumulate | 份额累积积数 | NUMBER(18,3) | 二级清算中用于存放份额积数 |

### 2.7 费用字段（核心业务字段）

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| tot_fee | 总费用 | NUMBER(18,2) | 总费用 |
| **charge** | **手续费** | **NUMBER(18,2)** | **手续费，用于汇总到tbdxfundtranssum，进而更新tbdxfundpayment的charge和red_charge字段** |
| stamp_tax | 印花税 | NUMBER(18,2) | 印花税 |
| interest_tax | 利息税 | NUMBER(18,2) | 利息税 |
| transfer_fee | 过户费 | NUMBER(18,2) | 过户费 |
| **agency_fee** | **代理费** | **NUMBER(18,2)** | **代理费，中登TA（98/99）直接使用该字段作为手续费** |
| **back_fee** | **后收费用** | **NUMBER(18,2)** | **赎回交易的后端收费或基金转换的补收费，用于汇总到tbdxfundtranssum** |
| other_fee1 | 其它费用1 | NUMBER(18,2) | 其它费用1 |
| other_fee2 | 其他费用2 | NUMBER(18,2) | 其他费用2 |
| bank_charge | 银行手续费 | NUMBER(18,2) | 本笔交易手续费按销售商比例归银行所有的部分 |
| manage_charge | 外收手续费 | NUMBER(18,2) | 本笔交易手续费按销售商比例归银行所有的部分 |
| **manage_fee** | **管理费** | **NUMBER(18,2)** | **管理费用，BTA用于赎回或到期时浮动管理费计算及后续分配** |
| cfm_income | 确认收益 | NUMBER(18,2) | 确认收益 |
| achievement_pay | 业绩报酬 | NUMBER(16,2) | 业绩报酬（支付） |
| achievement_com | 业绩报酬 | NUMBER(16,2) | 业绩报酬（计算） |
| over_manage_fee | 超额管理费 | NUMBER(16,2) | 超额管理费 |
| con_manage_fee | 连续管理费 | NUMBER(16,2) | 连续管理费 |
| return_manage_fee | 返还管理费 | NUMBER(16,2) | 返还管理费 |

### 2.8 转换相关字段

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| conv_dir | 转换方向 | VARCHAR2(1) | [K_ZHFX] 0-转出 1-转入 |
| targ_prd_code | 目标产品代码 | VARCHAR2(32) | 目标产品代码 |
| targ_nav | 目标产品净值 | NUMBER(18,8) | 目标产品净值 |
| targ_price | 目标产品价格 | NUMBER(18,8) | 目标产品价格 |
| targ_cfm_vol | 目标产品确认份额 | NUMBER(18,3) | 目标产品确认份额 |

### 2.9 分红相关字段

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| div_mode | 分红方式 | VARCHAR2(1) | [K_FHFS] 0-红利转投 1-现金分红 2-利得现金增值再投资 3-增值现金利得再投资 4-部分再投资 5-赠送 |
| div_rate | 分红比例 | NUMBER(9,8) | 分红比例 |
| interest | 利息 | NUMBER(18,2) | 认购结果使用 |
| vol_of_int | 利息转份额 | NUMBER(18,3) | 认购结果使用 |

### 2.10 状态和控制字段

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| detail_flag | 明细标志 | VARCHAR2(1) | [K_MXBZ] 0-汇总数据 1-明细数据 |
| finish_flag | 结束标志 | VARCHAR2(1) | [K_JSBS] 0-中间过程 1-业务过程结束 |
| frozen_cause | 冻结原因 | VARCHAR2(1) | [K_DJYY] 0-司法冻结 1-柜台冻结 2-质押 3-质押 司法 4-柜台 司法 |
| larg_red_flag | 巨额赎回处理标志 | VARCHAR2(1) | [K_JESHBZ] 0-取消 1-顺延 |
| red_cause | 强行赎回原因 | VARCHAR2(1) | [K_SHYH] 0-小于最低持有数 1-司法执行 2-政策原因 |
| real_flag | 实时标志 | VARCHAR2(1) | 实时标志 |
| is_daily | 是否日间清算 | VARCHAR2(1) | 是否日间清算 |
| advisor_flag | 智能投顾标志 | VARCHAR2(1) | 智能投顾业务标识，用于过滤汇总 |

### 2.11 其他重要字段

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| table_num | 分表号 | VARCHAR2(10) | 分表编号，用于标识数据所在的分表 |
| modify_timestamp | 修改时间戳 | NUMBER(14,0) | 修改时间戳，用于数据同步 |
| summary | 摘要 | VARCHAR2(250) | 非交易过户/强行调增调减原因/强行赎回等原因 |
| err_code | 错误代码 | VARCHAR2(12) | 错误代码 |
| err_msg | 错误信息 | VARCHAR2(512) | 错误信息 |
| asso_serial | 关联流水号 | VARCHAR2(32) | 撤单/解冻/指定赎回/转托管入/非交易过户入交易的原流水号 |
| host_trans_code | 主机交易码 | VARCHAR2(6) | 主机交易码 |
| host_serial | 主机流水号 | VARCHAR2(32) | 本字段用于主机对账及撤单时使用 |
| ex_serial | 原始请求外部流水号 | VARCHAR2(32) | 原始请求外部流水号 |
| contract_no | 合同编号 | VARCHAR2(32) | 合约编号 |
| cont_frozen_amt | 继续冻结金额 | NUMBER(18,2) | BTA清盘时继续冻结(cfm_amt不包含该冻结金额) |
| seller_code | 销售商代码 | VARCHAR2(9) | 销售商代码 |
| client_manager | 客户经理 | VARCHAR2(32) | 客户经理 |
| branch_no | 分支机构编号 | VARCHAR2(16) | 分支机构编号 |
| open_branch | 所属机构 | VARCHAR2(80) | 所属机构 |
| channel | 渠道 | VARCHAR2(1) | [K_JYQD] 渠道 |
| term_no | 终端编号 | VARCHAR2(16) | 终端编号 |
| oper_no | 操作柜员 | VARCHAR2(32) | 操作柜员 |
| cash_flag | 钞汇标志 | VARCHAR2(1) | [K_CHBZ] 0-现钞 1-现汇 2-均可 |
| trans_account_type | 交易介质类型 | VARCHAR2(1) | [K_KHBSLX] 0-入账账号 1-客户号 2-证件 |
| trans_account | 交易账号 | VARCHAR2(32) | 交易介质 |
| agio | 折扣率 | NUMBER(5,4) | 折扣率 |
| amt1 | 备用金额1 | NUMBER(18,2) | 备用金额1 |
| amt2 | 备用金额2 | NUMBER(18,2) | 备用金额2 |
| amt3 | 备用金额3 | NUMBER(18,2) | 备用金额3 |
| reserve1 | 保留字段1 | VARCHAR2(250) | 保留字段1 |
| reserve2 | 保留字段2 | VARCHAR2(250) | 保留字段2 |
| reserve3 | 保留字段3 | VARCHAR2(250) | 保留字段3 |

## 三、重要操作说明

### 3.1 插入操作（INSERT）

**操作场景**：
1. **交易确认后插入**：从TA返回确认数据后，将确认结果插入到确认表
2. **日终清算备份**：日终清算时，将确认数据备份到临时表（如：`tbdxfundtranscfm001`）

**插入方式**：
- 通过 `DxFundTransCfmDao.addTransCfm()` 方法插入
- 支持指定表名插入（用于分表场景）
- 根据 `in_client_no` 自动路由到对应的分表

**关键字段**：
- 必须设置主键字段：`ta_code`、`cfm_date`、`cfm_no`、`bank_no`、`in_client_no`
- 必须设置金额字段：`cfm_amt`、`cfm_vol`、`charge`、`agency_fee`、`back_fee`
- 必须设置业务字段：`busin_code`、`prd_code`、`status`、`from_flag`

### 3.2 更新操作（UPDATE）

**操作场景**：
1. **确认状态更新**：当TA返回部分确认或全部确认时，更新确认状态
2. **确认金额调整**：确认金额发生变化时更新
3. **错误信息更新**：确认失败时更新错误代码和错误信息
4. **关联信息更新**：更新关联日期、关联流水号等

**更新方式**：
- 通过 `DxFundTransCfmDao.modifyTransCfm()` 方法更新
- 更新条件（业务主键）：`ta_code`、`cfm_date`、`cfm_no`、`bank_no`、`in_client_no`

**关键字段**：
- 状态字段：`status`（确认状态）
- 金额字段：`cfm_amt`、`cfm_vol`（确认金额和份额）
- 费用字段：`charge`、`agency_fee`、`back_fee`（各种费用）
- 错误字段：`err_code`、`err_msg`（错误信息）

### 3.3 删除操作（DELETE）

**操作场景**：
1. **撤单删除**：交易撤单时删除确认记录
2. **数据清理**：日终清算完成后清理临时表数据
3. **错误数据清理**：删除错误的确认数据

**删除方式**：
- 通过 `DxFundTransCfmDao.deleteTransCfm()` 方法删除
- 删除条件（业务主键）：`ta_code`、`cfm_date`、`cfm_no`、`bank_no`、`in_client_no`

**注意事项**：
- 删除前需要确认数据不再被使用
- 日终清算临时表数据在汇总完成后可以删除

### 3.4 查询操作（SELECT）

**操作场景**：
1. **单笔查询**：根据主键查询单笔确认记录
2. **汇总查询**：日终清算时汇总确认数据
3. **对账查询**：与TA对账时查询确认数据
4. **报表查询**：生成各类报表时查询确认数据

**查询方式**：
- 通过 `DxFundTransCfmDao.getTransCfm()` 方法查询单笔
- 通过SQL直接查询（用于汇总、对账、报表等场景）

**常用查询条件**：
- `cfm_date = ?`：按确认日期查询
- `ta_code = ?`：按TA代码查询
- `prd_code = ?`：按产品代码查询
- `busin_code = ?`：按业务代码查询
- `status != '确认失败'`：排除失败记录
- `advisor_flag = '1'`：智能投顾业务（可能需要过滤）

## 四、数据来源

### 4.1 主要数据来源

**数据来源**：TA（登记机构）返回的交易确认数据

**完整数据流程**：
```
TA系统
  ↓ [生成确认文件：04类型文件]
  ↓ [文件传输：PUB210101取确认文件]
TA确认文件（OFD_SSS_RRR_YYYYMMDD_TT.TXT）
  ↓ [文件导入：PUB210103导入确认文件]
tbdxfundtranscfmtmp（文件导入临时表）
  ↓ [数据准备：DXFUND210104-Action207000]
tbdxfundtranscfm00{tableNum}（清算临时备份表）
  ↓ [数据清算：DXFUND210104-TransCfmDealTask]
tbdxfundtranscfm{tableNum}（正式表）
  ↓ [日终清算：数据汇总]
tbdxfundtranssum（交易汇总表）
  ↓ [更新/插入]
tbdxfundpayment（资金划付表）
```

### 4.1.1 TA文件类型配置（tbfiletype表）

**文件类型配置**：通过 `tbfiletype` 表配置TA文件和系统临时表的对应关系

**确认文件配置**（file_type='04'）：
- **文件类型**：`04` - 交易确认数据文件
- **文件命名规则**：`OFD_SSS_RRR_YYYYMMDD_TT.TXT`
  - `SSS`：TA代码
  - `RRR`：销售商代码
  - `YYYYMMDD`：文件日期
  - `TT`：文件类型（04）
- **目标临时表**：`tbdxfundtranscfmtmp`（文件导入临时表）
- **区域类型**：`area_type='1'`（交易片区）
- **备注**：交易确认数据与业务

**配置示例**（来自AddTA_TA.sql.vm）：
```sql
insert into tbfiletype (ta_code, file_type, file_name_rule, file_dir, table_name, remark, sql_script, area_type) 
values ('${TACODE}', '04', 'OFD_SSS_RRR_YYYYMMDD_TT.TXT', '0', 'tbdxfundtranscfmtmp', '交易确认数据与业务', ' ', '1');
```

### 4.1.2 文件导入流程

**步骤1：取确认文件（PUB210101）**
- 从TA系统获取确认文件
- 文件命名：`OFD_SSS_RRR_YYYYMMDD_04.TXT`
- 文件存放位置：根据 `file_dir` 配置（'0'表示确认文件目录）

**步骤2：导入确认文件（PUB210103）**
- 解析TA确认文件
- 将文件数据导入到 `tbdxfundtranscfmtmp` 临时表
- 使用 `DxFundTransCfmTmpDao.addTransCfmTmp()` 方法插入数据
- 根据 `in_client_no` 自动路由到对应的分表（通过 `table_num` 字段标识）

**关键点**：
- 文件导入时，数据直接写入 `tbdxfundtranscfmtmp` 表
- 该表是文件导入的临时存储表，用于暂存从TA文件导入的原始数据
- 数据按分表规则存储（`table_num` 字段标识分表号）

### 4.2 数据流转的完整过程

#### 4.2.1 阶段1：TA文件导入到临时表（tbdxfundtranscfmtmp）

**数据来源**：TA确认文件（04类型文件）

**导入流程**：
1. **取文件**（PUB210101）：
   - 从TA系统获取确认文件
   - 文件命名：`OFD_SSS_RRR_YYYYMMDD_04.TXT`

2. **导入文件**（PUB210103）：
   - 解析TA确认文件
   - 将文件数据导入到 `tbdxfundtranscfmtmp` 表
   - 使用 `DxFundTransCfmTmpDao.addTransCfmTmp()` 方法插入
   - 根据 `in_client_no` 自动路由到对应的分表

**关键点**：
- `tbdxfundtranscfmtmp` 是文件导入临时表，用于暂存从TA文件导入的原始数据
- 数据按分表规则存储（通过 `table_num` 字段标识分表号）

#### 4.2.2 阶段2：临时表转移到清算临时表（tbdxfundtranscfmtmp → tbdxfundtranscfm00）

**数据来源**：文件导入临时表 `tbdxfundtranscfmtmp`

**转移流程**（在 `Action207000` 中执行，属于DXFUND210104确认数据清算任务）：
1. 清空清算临时表：`DELETE FROM tbdxfundtranscfm00{tableNum} WHERE ta_code = ?`
2. 从文件导入临时表转移数据：
   ```sql
   INSERT INTO tbdxfundtranscfm00{tableNum}
   SELECT * 
   FROM tbdxfundtranscfmtmp 
   WHERE ta_code = ? AND table_num = ?
   ```

**转移目的**：
- 将文件导入的数据转移到清算临时表
- 为后续的数据清算和汇总做准备
- 分离文件导入和清算处理，提高系统稳定性

**关键点**：
- `tbdxfundtranscfm00{tableNum}` 是清算临时备份表，用于日终清算处理
- 通过 `TransVar200007.getTbTransCfmBak()` 获取表名（默认：`tbdxfundtranscfm00`）

#### 4.2.3 阶段3：清算临时表转移到正式表（tbdxfundtranscfm00 → tbdxfundtranscfm）

**数据来源**：清算临时表 `tbdxfundtranscfm00{tableNum}` 和正式表 `tbdxfundtranscfm{tableNum}`

**转移流程**（在 `TransCfmDealTask` 中执行，属于DXFUND210110单TA清算结束任务）：

**步骤1：数据备份**（正式表 → 清算临时表）
- 从正式表备份当日确认数据到清算临时表
- SQL：
  ```sql
  INSERT INTO tbdxfundtranscfm00{tableNum}
  SELECT ...
  FROM tbdxfundtranscfm{tableNum}
  WHERE cfm_date = ? AND ta_code = ?
  AND NOT EXISTS (
      SELECT 1 FROM tbdxfundtranscfm00{tableNum} a 
      WHERE a.cfm_date = tbdxfundtranscfm{tableNum}.cfm_date 
        AND a.serial_no = tbdxfundtranscfm{tableNum}.serial_no 
        AND a.cfm_no = tbdxfundtranscfm{tableNum}.cfm_no 
        AND a.ta_code = tbdxfundtranscfm{tableNum}.ta_code
        AND a.bank_no = tbdxfundtranscfm{tableNum}.bank_no
  )
  ```

**备份的目的和作用**：

1. **保护7*24模式下的实时确认数据**：
   - 在7*24模式下，TA可能实时返回确认数据，这些数据直接写入正式表 `tbdxfundtranscfm{tableNum}`
   - 这些实时确认的数据不会出现在TA的确认文件中，因此不会进入 `tbdxfundtranscfmtmp` 表
   - 如果不备份，这些实时确认的数据将无法参与后续的数据汇总
   - **作用**：确保7*24模式下实时确认的数据也能参与汇总，生成正确的交易汇总表

2. **处理滚续产品和违约赎回等特殊情况**：
   - 滚续产品和违约赎回等业务场景下，可能存在"事实确认交易"（实际已确认但文件未包含的交易）
   - 这些交易已经在正式表中，但不在文件导入的临时表中
   - **作用**：将事实确认交易的流水补充回清算临时表，确保后续数据汇总的完整性

3. **数据去重保护**：
   - 备份条件中使用 `NOT EXISTS` 判断，只备份清算临时表中不存在的记录
   - 如果文件导入的数据已经在清算临时表中（通过Action207000转入），就不再备份正式表中的相同记录
   - **作用**：避免数据重复，确保每条确认记录只出现一次

4. **数据完整性保障**：
   - 确保清算临时表包含所有当日的确认数据：
     - 文件导入的数据（从 `tbdxfundtranscfmtmp` 转入）
     - 实时确认的数据（从正式表备份）
   - **作用**：形成完整的确认数据集，为后续汇总提供准确的数据源

5. **配合删除逻辑，保护7*24数据**：
   - 删除正式表数据时，使用 `EXISTS` 判断，只删除清算临时表中存在的记录
   - 这样可以保护7*24产品的交易记录不被误删（如果7*24数据不在清算临时表中，就不会被删除）
   - **作用**：修复"误删tbdxfundtranscfm表中7*24产品的交易记录"的缺陷

**为什么必须备份正式表数据？**

**核心原因**：正式表中可能存在文件导入流程中没有的数据

**典型场景**：

1. **7*24模式下的实时确认**：
   - 场景：TA在7*24模式下实时返回确认数据，直接写入正式表
   - 问题：这些数据不会出现在TA的确认文件中，因此不会进入 `tbdxfundtranscfmtmp` → `tbdxfundtranscfm00`
   - 解决：通过备份正式表，将这些实时确认的数据补充到清算临时表
   - 结果：确保所有确认数据都能参与汇总

2. **滚续产品和违约赎回**：
   - 场景：滚续产品和违约赎回等业务场景下，可能存在"事实确认交易"
   - 问题：这些交易已经在正式表中（可能是之前清算时写入的），但不在本次的确认文件中
   - 解决：通过备份正式表，将这些事实确认交易的流水补充回清算临时表
   - 结果：确保后续数据汇总的完整性（参考代码注释："将事实确认交易的流水补充回tbdxfundtranscfmbak,后续数据汇总要用到"）

3. **数据一致性保障**：
   - 场景：文件导入的数据和正式表中已有的数据可能存在差异
   - 问题：如果只使用文件导入的数据，可能会遗漏正式表中已有的确认数据
   - 解决：通过备份正式表，确保清算临时表包含所有当日的确认数据
   - 结果：形成完整的确认数据集，保证数据汇总的准确性

**备份策略的巧妙之处**：
- 使用 `NOT EXISTS` 判断，只备份清算临时表中不存在的记录
- 如果文件导入的数据已经在清算临时表中，就不再备份正式表中的相同记录
- 这样既保证了数据完整性，又避免了数据重复
- 配合删除逻辑（使用 `EXISTS` 判断），保护了7*24数据不被误删

**步骤2：数据合并**（文件导入数据已在阶段2转入清算临时表）
- 此时清算临时表包含：
  - 从文件导入的数据（阶段2转入，通过Action207000）
  - 从正式表备份的数据（步骤1备份，通过TransCfmDealTask）
- 目的：形成完整的确认数据集，统一处理

**数据合并的必要性**：
- **文件导入数据**：TA通过文件方式返回的确认数据（批量确认）
- **正式表备份数据**：7*24模式下实时确认的数据、滚续产品和违约赎回等特殊情况的数据
- **合并结果**：清算临时表包含所有当日的确认数据，无论是文件导入还是实时确认
- **统一处理**：所有确认数据在同一个表中，便于后续的汇总、回写等操作

**步骤3：数据回写**（清算临时表 → 正式表）

**回写流程**（在 `TransCfmDealTask` 中执行）：
1. **先删除正式表中已备份的数据**（避免重复）：
   ```sql
   DELETE FROM tbdxfundtranscfm{tableNum} a
   WHERE a.cfm_date = ? AND a.ta_code = ?
   AND EXISTS (
       SELECT 1 FROM tbdxfundtranscfm00{tableNum} 
       WHERE ta_code = a.ta_code 
         AND cfm_no = a.cfm_no 
         AND cfm_date = a.cfm_date 
         AND bank_no = a.bank_no
   )
   ```
   - 目的：删除正式表中已经在清算临时表中的数据，避免回写时主键冲突

2. **从清算临时表回写到正式表**：
   ```sql
   INSERT INTO tbdxfundtranscfm{tableNum} 
   (ta_code, cfm_date, cfm_no, ..., modify_timestamp, advisor_flag, entity_bank_acc, 
    achievement_pay, achievement_com, over_manage_fee, con_manage_fee, return_manage_fee)
   SELECT 
       COALESCE(ta_code, ' '), 
       COALESCE(cfm_date, 0), 
       COALESCE(cfm_no, ' '), 
       ...,
       CURRENT_TIMESTAMP,  -- modify_timestamp 使用当前时间戳
       COALESCE(advisor_flag, ' '), 
       COALESCE(entity_bank_acc, ' '),
       COALESCE(achievement_pay, 0), 
       COALESCE(achievement_com, 0), 
       COALESCE(over_manage_fee, 0), 
       COALESCE(con_manage_fee, 0), 
       COALESCE(return_manage_fee, 0)
   FROM tbdxfundtranscfm00{tableNum}
   WHERE ta_code = ?
   ```

**回写的目的和作用**：

1. **更新正式表数据**：
   - 将清算临时表中的所有数据（包括文件导入数据和正式表备份数据）回写到正式表
   - 更新 `modify_timestamp` 字段为当前时间戳，用于数据同步
   - **作用**：确保正式表包含最新的确认数据

2. **同步新增字段**：
   - 同步新增的字段到正式表，如：`advisor_flag`（智能投顾标志）、`entity_bank_acc`（实体卡号）、`achievement_pay`（业绩报酬）等
   - **作用**：确保正式表包含所有字段的最新数据

3. **数据统一处理**：
   - 将文件导入的数据和正式表备份的数据统一回写到正式表
   - **作用**：形成统一的正式表数据，便于后续业务使用

4. **数据完整性保障**：
   - 确保正式表包含所有当日的确认数据：
     - 文件导入的数据（从 `tbdxfundtranscfmtmp` → `tbdxfundtranscfm00`）
     - 实时确认的数据（从正式表备份到 `tbdxfundtranscfm00`）
   - **作用**：保证正式表数据的完整性和准确性

**回写的关键点**：
- **删除在前**：先删除正式表中已备份的数据，避免主键冲突
- **回写在后**：然后从清算临时表回写所有数据到正式表
- **时间戳更新**：回写时更新 `modify_timestamp` 字段为当前时间戳，用于数据同步
- **字段同步**：同步所有字段，包括新增字段（如：`advisor_flag`、`entity_bank_acc`、`achievement_pay` 等）

**为什么需要先删除再回写？**

1. **避免主键冲突**：
   - 正式表中可能已经存在相同主键的记录（`ta_code`、`cfm_date`、`cfm_no`）
   - 如果直接插入，会因为主键冲突而失败
   - **解决**：先删除已备份的数据，再回写，确保插入成功

2. **数据更新需求**：
   - 清算临时表中的数据可能包含更新的字段值（如状态更新、金额调整等）
   - 需要将更新后的数据写回正式表
   - **解决**：删除旧数据，插入新数据，实现数据更新

3. **保护7*24数据**：
   - 删除时使用 `EXISTS` 判断，只删除清算临时表中存在的记录
   - 如果7*24数据不在清算临时表中（因为不在文件中），就不会被删除
   - **解决**：保护7*24产品的交易记录不被误删

**回写的执行顺序**（在TransCfmDealTask中）：
```
1. 删除正式表中已备份的数据
   ↓
2. 从清算临时表回写所有数据到正式表
   ↓
3. 更新 modify_timestamp 字段
   ↓
4. 同步所有字段（包括新增字段）
```

**回写后的数据状态**：
- 正式表包含所有当日的确认数据（文件导入 + 实时确认）
- 所有字段都是最新的（包括新增字段）
- `modify_timestamp` 字段已更新，可用于数据同步

**步骤4：数据清理**
- 删除正式表中已备份的数据：
  ```sql
  DELETE FROM tbdxfundtranscfm{tableNum} a
  WHERE a.cfm_date = ? AND a.ta_code = ?
  AND EXISTS (
      SELECT 1 FROM tbdxfundtranscfm00{tableNum} 
      WHERE ta_code = a.ta_code 
        AND cfm_no = a.cfm_no 
        AND cfm_date = a.cfm_date 
        AND bank_no = a.bank_no
  )
  ```

**删除逻辑的关键点**：
- 使用 `EXISTS` 判断，只删除清算临时表中存在的记录
- **保护7*24数据**：如果7*24产品的交易记录不在清算临时表中（因为不在文件中），就不会被删除
- **修复缺陷**：解决了"误删tbdxfundtranscfm表中7*24产品的交易记录"的问题
- **数据一致性**：确保删除的数据都是已经备份到清算临时表的数据，避免数据丢失

**关键点**：
- `tbdxfundtranscfm00{tableNum}` 在数据流转中起到关键作用：
  1. **接收文件导入数据**：从 `tbdxfundtranscfmtmp` 接收TA文件导入的数据（在Action207000中）
  2. **备份正式表数据**：备份正式表中已有的确认数据（在TransCfmDealTask中）
     - **特别重要**：保护7*24模式下实时确认的数据
     - **特别重要**：处理滚续产品和违约赎回等特殊情况的数据
  3. **数据合并处理**：合并文件导入数据和正式表备份数据，形成完整的确认数据集
  4. **数据回写**：将处理后的数据回写到正式表（在TransCfmDealTask中）
  5. **数据汇总**：作为数据汇总的数据源，生成交易汇总表（在TranssumPaymentFunc中）

**tbdxfundtranscfm00表的核心价值**：
- **数据隔离**：将文件导入数据和正式表数据隔离，避免相互影响
- **数据保护**：备份正式表数据，确保数据安全，特别是7*24模式下的实时确认数据
- **数据合并**：统一处理文件导入数据和正式表已有数据，确保数据完整性
- **数据汇总**：作为汇总的数据源，避免直接操作正式表，提高性能
- **数据完整性**：确保所有确认数据（文件导入+实时确认）都能参与汇总，不会遗漏

#### 4.2.4 阶段4：数据汇总（tbdxfundtranscfm00 → tbdxfundtranssum）

**数据来源**：清算临时表 `tbdxfundtranscfm00{tableNum}` 中的确认数据

**汇总流程**（在 `TranssumPaymentFunc` 中执行）：
1. 从清算临时表 `tbdxfundtranscfm00{tableNum}` 汇总数据
2. 按产品、TA、业务代码、币种等维度分组统计
3. 插入到 `tbdxfundtranssum`（交易汇总表）
4. SQL示例：
   ```sql
   INSERT INTO tbdxfundtranssum 
   SELECT tableNum, areaId, sumDate, prd_code, ta_code, busin_code, curr_type,
          COUNT(1) tot_num,
          SUM(cfm_amt) cfm_amt,
          SUM(charge) charge,
          ...
   FROM tbdxfundtranscfm00{tableNum}
   WHERE cfm_date = ? AND ta_code = ?
   GROUP BY prd_code, ta_code, busin_code, curr_type
   ```

**汇总目的**：
- 生成交易汇总数据
- 用于资金划付处理
- 支持报表生成

### 4.3 数据来源的业务分类

1. **TA文件导入**（主要方式）：
   - TA通过文件方式返回确认数据
   - 文件类型：04（交易确认数据文件）
   - 导入流程：文件 → `tbdxfundtranscfmtmp` → `tbdxfundtranscfm00` → `tbdxfundtranscfm`

2. **实时确认**（7*24模式）：
   - TA实时返回确认数据
   - 直接插入或更新到正式表 `tbdxfundtranscfm{tableNum}`
   - 使用场景：7*24交易、实时交易确认

3. **TA主动发起**（`from_flag = '1'`）：
   - TA主动发起的交易（如分红、份额调账等）
   - 通过文件导入或实时接口插入

### 4.4 数据插入时机

1. **文件导入**（主要方式）：
   - TA通过文件方式返回确认数据
   - 导入时机：日终清算时（PUB210103导入确认文件）
   - 数据流转：文件 → `tbdxfundtranscfmtmp` → `tbdxfundtranscfm00` → `tbdxfundtranscfm`

2. **实时确认**（7*24模式）：
   - TA实时返回确认结果
   - 立即插入或更新正式表 `tbdxfundtranscfm{tableNum}`
   - 使用场景：7*24交易、实时交易确认

3. **日终清算数据准备**（Action207000）：
   - 从文件导入临时表 `tbdxfundtranscfmtmp` 转移到清算临时表 `tbdxfundtranscfm00{tableNum}`
   - 执行时机：确认数据清算任务（DXFUND210104）开始时

4. **日终清算数据备份**（TransCfmDealTask）：
   - 从正式表 `tbdxfundtranscfm{tableNum}` 备份到清算临时表 `tbdxfundtranscfm00{tableNum}`
   - 执行时机：单TA清算结束任务（DXFUND210110）开始时
   - 目的：保护正式表中已有的确认数据

5. **日终清算数据回写**（TransCfmDealTask）：
   - 从清算临时表 `tbdxfundtranscfm00{tableNum}` 回写到正式表 `tbdxfundtranscfm{tableNum}`
   - 执行时机：单TA清算结束任务（DXFUND210110）中
   - 目的：更新正式表数据（包括新增字段、状态更新等）

## 五、使用用途

### 5.1 核心用途

#### 5.1.1 交易确认数据存储
- **用途**：存储所有代销基金交易的确认数据
- **重要性**：是交易确认的核心数据表，记录了每笔交易的确认结果

#### 5.1.2 日终清算数据源
- **用途**：作为日终清算的数据源，汇总生成交易汇总表（`tbdxfundtranssum`）
- **汇总维度**：按产品、TA、业务代码、币种等维度汇总
- **汇总字段**：
  - `cfm_amt`（确认金额）→ 汇总到 `tbdxfundtranssum.cfm_amt`
  - `cfm_vol`（确认份额）→ 汇总到 `tbdxfundtranssum.cfm_vol`
  - `charge`（手续费）→ 汇总到 `tbdxfundtranssum.charge`
  - `back_fee`（后收费用）→ 汇总到 `tbdxfundtranssum.back_fee`
  - `agency_fee`（代理费）→ 汇总到 `tbdxfundtranssum.agency_fee`

#### 5.1.3 资金划付数据支持
- **用途**：通过交易汇总表间接支持资金划付表（`tbdxfundpayment`）的数据更新
- **更新内容**：
  - 更新确认金额（`cfm_amt`）
  - 更新认申购手续费（`charge`）
  - 更新赎回手续费（`red_charge`）
  - 插入新的资金划付记录（当日确认但下一日无返款的产品）

### 5.2 业务用途

#### 5.2.1 交易对账
- **用途**：与TA进行交易对账
- **对账依据**：`cfm_no`（TA确认流水号）、`cfm_date`（确认日期）、`cfm_amt`（确认金额）等

#### 5.2.2 份额管理
- **用途**：记录确认份额，用于份额表的更新
- **关键字段**：`cfm_vol`（确认份额）、`post_vol`（交易后份额）

#### 5.2.3 费用计算
- **用途**：记录各种费用，用于费用分配和结算
- **关键字段**：`charge`（手续费）、`agency_fee`（代理费）、`back_fee`（后收费用）、`manage_fee`（管理费）等

#### 5.2.4 分红处理
- **用途**：记录分红确认数据
- **关键字段**：`div_mode`（分红方式）、`div_rate`（分红比例）、`cfm_vol`（红利再投资基金份数）等

#### 5.2.5 转换处理
- **用途**：记录基金转换的确认数据
- **关键字段**：`conv_dir`（转换方向）、`targ_prd_code`（目标产品代码）、`targ_cfm_vol`（目标产品确认份额）等

#### 5.2.6 报表生成
- **用途**：作为各类报表的数据源
- **报表类型**：交易确认报表、资金划付报表、费用统计报表等

### 5.3 数据流向

```
tbdxfundtranscfm（交易确认表）
    ↓ [汇总]
tbdxfundtranssum（交易汇总表）
    ↓ [更新/插入]
tbdxfundpayment（资金划付表）
    ↓ [导出]
tbdxfundtranssumsync（交易汇总同步表）
tbdxfundpaymentsync（资金划付同步表）
    ↓ [文件导出]
公共库片区
```

## 六、关键业务规则

### 6.1 数据汇总规则

1. **汇总条件**：
   - `cfm_date = 当日`（确认日期为当日）
   - `ta_code = 当前TA`（当前处理的TA）
   - `status != '确认失败'`（排除失败记录）
   - `busin_code != ' '` 且 `busin_code IS NOT NULL`（业务代码不为空）

2. **特殊过滤**：
   - **智能投顾业务**：根据参数 `TRANSSUM_AND_PAYMENT_CFM_AMT_NOT_CONTAIN_AR` 决定是否过滤 `advisor_flag = '1'` 的业务
   - **余额宝产品**：根据参数 `AUTOTRANSFER_T1_PRD` 配置特殊划拨产品，排除特定业务代码
   - **客户类型**：根据参数 `PAY_CLT_TYPE` 决定是否区分机构/个人客户

3. **汇总维度**：
   - 产品维度：`prd_code`、`ta_code`、`busin_code`、`curr_type`
   - TA维度：`ta_code`、`busin_code`、`curr_type`（`prd_code='000000'`）

### 6.2 金额计算规则

1. **确认金额（cfm_amt）**：
   - 来源：TA返回的确认金额
   - 用途：汇总到 `tbdxfundtranssum.cfm_amt`，进而更新 `tbdxfundpayment.cfm_amt`
   - 业务代码：认申购业务（'120'、'122'、'139'、'887'）用于更新认申购确认金额

2. **手续费（charge）**：
   - 来源：TA返回的手续费
   - 用途：汇总到 `tbdxfundtranssum.charge`，进而更新 `tbdxfundpayment.charge`（认申购）或 `tbdxfundpayment.red_charge`（赎回）
   - 分配：根据产品手续费分配比例计算销售商手续费

3. **代理费（agency_fee）**：
   - 来源：TA返回的代理费
   - 用途：中登TA（98/99）直接使用代理费字段作为手续费
   - 汇总：汇总到 `tbdxfundtranssum.agency_fee`

4. **后收费用（back_fee）**：
   - 来源：TA返回的后端收费
   - 用途：赎回交易的后端收费或基金转换的补收费
   - 汇总：汇总到 `tbdxfundtranssum.back_fee`

### 6.3 状态处理规则

1. **确认状态（status）**：
   - `'6'`：部分确认未全部返回
   - `'7'`：部分确认已全部返回
   - `'8'`：确认成功
   - `'9'`：确认失败
   - `'A'`：认购确认
   - `'B'`：份额调账
   - `'D'`：分红数据

2. **汇总状态过滤**：
   - 汇总时排除 `status = '确认失败'` 的记录
   - 产品维度汇总：`status IN ('部分在途','部分完成','确认成功','提交确认')`
   - TA维度汇总：`status = '确认成功'`

### 6.4 分表处理规则

1. **分表路由**：
   - 根据 `in_client_no`（内部客户编号）路由到对应的分表
   - 分表数量：1-16个分表

2. **分表汇总**：
   - 先按分表汇总数据（`table_num='1'-'16'`）
   - 然后合并所有分表数据，生成汇总数据（`table_num='0'`）
   - 最后删除分表数据，仅保留汇总数据

## 七、与其他表的关系

### 7.1 数据流向关系

**完整数据流向**：
```
tbdxfundtransreq（交易申请表）
    ↓ [TA确认]
tbdxfundtranscfm{tableNum}（正式表）
    ↓ [日终清算：数据备份]
tbdxfundtranscfm00{tableNum}（临时备份表）
    ↓ [日终清算：数据汇总]
tbdxfundtranssum（交易汇总表）
    ↓ [更新/插入]
tbdxfundpayment（资金划付表）
    ↓ [日终清算：数据回写]
tbdxfundtranscfm{tableNum}（正式表）
```

**关键说明**：
- **正式表**：`tbdxfundtranscfm{tableNum}` 用于存储日常交易确认数据
- **临时表**：`tbdxfundtranscfm00{tableNum}` 用于日终清算时的数据备份和汇总
- **表名获取**：通过 `TransVar200007.getTbTransCfmBak()` 方法获取临时表基础表名，然后加上分表号

### 7.2 关联关系

#### 7.2.1 与 tbdxfundtransreq（交易请求表）的关联关系

**关联字段**：

1. **主要关联字段**：
   - **`serial_no`**（流水序号）：系统内部流水号，是 `tbdxfundtransreq` 和 `tbdxfundtranscfm` 之间的主要关联字段
   - **`cfm_no`**（确认流水号）：TA返回的确认流水号，TA发起时与 `serial_no` 相同
   - **`in_client_no`**（内部客户号）：用于定位客户，配合 `serial_no` 进行精确查询
   - **`ta_code`**（TA代码）：用于区分不同的TA
   - **`bank_no`**（银行代码）：用于区分不同的银行
   - **`trans_code`**（交易代码）：用于区分不同的交易类型
   - **`busin_code`**（业务代码）：用于区分不同的业务类型（如：120-认购、122-申购、124-赎回等）

**数据流转关系**：

```
1. 交易请求阶段
tbdxfundtransreq（交易请求表）
    ↓ [交易请求写入]
    serial_no, in_client_no, ta_code, bank_no, trans_code, busin_code, amt, vol, ...
    
2. TA确认阶段
TA确认文件（04类型文件）
    ↓ [PUB210103导入确认文件]
tbdxfundtranscfmtmp（文件导入临时表）
    ↓ [Action207000数据准备]
tbdxfundtranscfm00（清算临时备份表）
    ↓ [确认处理时关联]
tbdxfundtransreq（交易请求表，更新状态）
```

**关联查询逻辑**（在确认处理时）：

1. **通过 serial_no 和 in_client_no 查询交易请求**：
   ```java
   // 在 Action207122（申购确认）等确认处理类中
   transReqBak = DxFundServiceFactory.getTransReqService().getTransReqByInClientNo(
       transVar.getTransCfmBak().getSerialNo(),      // 从确认表获取 serial_no
       transVar.getTransCfmBak().getInClientNo(),  // 从确认表获取 in_client_no
       transVar.getTbTransReqBak()                 // 查询交易请求备份表
   );
   ```

2. **SQL查询示例**：
   ```sql
   SELECT * FROM tbdxfundtransreq{tableNum}
   WHERE serial_no = ? 
     AND in_client_no = ?
   ```

**数据同步关系**：

1. **确认表补充交易请求表信息**（`transTransReqBakToCfmBak` 方法）：
   - 将 `tbdxfundtransreq` 中的交易信息补充到 `tbdxfundtranscfm`
   - 同步字段包括：
     - `trans_date`（交易日期）
     - `trans_time`（交易时间）
     - `trans_code`（交易代码）
     - `branch_no`（机构号）
     - `open_branch`（开户机构）
     - `channel`（渠道）
     - `oper_no`（操作员号）
     - `client_no`（客户号）
     - `client_name`（客户名称）
     - `bank_acc`（银行账号）
     - `curr_type`（币种）
     - `client_manager`（客户经理）
     - `summary`（摘要）
     - `trans_account`（交易账户）
     - `contract_no`（合同号）
     - `manage_charge`（管理费）
     - `host_trans_code`（主机交易代码）
     - `host_date`（主机日期）
     - `host_serial`（主机流水号）
     - `cash_flag`（钞汇标志）
     - `trans_account_type`（交易账户类型）
     - `bank_no`（银行代码）
     - `div_mode`（分红方式）
     - `term_no`（终端号）

2. **交易请求表更新确认信息**：
   - 确认处理时，将确认信息回写到 `tbdxfundtransreq`
   - 更新字段包括：
     - `cfm_date`（确认日期）：从 `tbdxfundtranscfm.cfm_date` 更新
     - `cfm_no`（确认流水号）：从 `tbdxfundtranscfm.cfm_no` 更新
     - `status`（状态）：更新为确认成功（'8'）或确认失败（'9'）
     - `err_code`（错误代码）：更新确认结果
     - `err_msg`（错误信息）：更新确认信息
     - `reserve2`（保留字段2）：黄金ETF等特殊产品的成交克数

**确认处理流程**（以申购确认为例）：

1. **读取确认数据**：
   - 从 `tbdxfundtranscfm00`（清算临时备份表）读取确认数据
   - 获取 `serial_no`、`in_client_no`、`ta_code` 等关联字段

2. **查询交易请求**：
   - 通过 `serial_no` 和 `in_client_no` 查询 `tbdxfundtransreq00`（交易请求备份表）
   - 如果查询不到，抛出异常："取交易请求备份表失败"

3. **数据校验**：
   - 校验确认数据与交易请求数据的一致性：
     - `client_no`（客户号）必须一致
     - `prd_code`（产品代码）必须一致
     - `amt`（金额）必须一致

4. **数据同步**：
   - 调用 `transTransReqBakToCfmBak` 方法，将交易请求信息补充到确认表
   - 更新交易请求表的确认信息（`cfm_date`、`cfm_no`）

5. **状态更新**：
   - 确认成功：更新 `tbdxfundtransreq.status = '8'`（确认成功）
   - 确认失败：更新 `tbdxfundtransreq.status = '9'`（确认失败）
   - 部分确认：更新 `tbdxfundtransreq.status = '7'`（部分确认已全部返回）

**重复确认检查**：

- 在确认处理前，检查交易请求的状态：
  ```java
  if (IDict.K_JYZT.JYZT_CFM_SUCC.equals(transReqBak.getStatus())
      || IDict.K_JYZT.JYZT_CFM_FAIL.equals(transReqBak.getStatus())
      || IDict.K_JYZT.JYZT_PART_OVER.equals(transReqBak.getStatus())) {
      // 已确认，不再处理
      throw new BizBussinessException(IErrMsg.ERR_TRANSCLEAR, "");
  }
  ```

**清算数据准备阶段**（Action207000）：

1. **交易请求备份**：
   - 从 `tbdxfundtransreq{tableNum}` 备份待清算的交易请求到 `tbdxfundtransreq00{tableNum}`
   - SQL：
     ```sql
     INSERT INTO tbdxfundtransreq00{tableNum}
     SELECT * FROM tbdxfundtransreq{tableNum}
     WHERE ta_code = ? 
       AND trans_date <= ?
       AND status NOT IN ('待授权', '8', '9')  -- 排除已确认和待授权的流水
     ```

2. **确认数据准备**：
   - 从 `tbdxfundtranscfmtmp` 转移到 `tbdxfundtranscfm00{tableNum}`
   - 在确认处理时，通过 `serial_no` 关联 `tbdxfundtransreq00` 和 `tbdxfundtranscfm00`

**实时确认场景**（7*24模式）：

- 在7*24模式下，确认数据可能直接写入 `tbdxfundtranscfm` 正式表
- 确认处理时，同样通过 `serial_no` 关联 `tbdxfundtransreq` 更新状态
- SQL示例：
  ```sql
  UPDATE tbdxfundtransreq{tableNum}
  SET status = ?, err_code = ?, err_msg = ?
  WHERE serial_no = ? AND in_client_no = ?
  ```

**关联关系总结**：

| 关联方向 | 关联字段 | 关联目的 | 执行时机 |
|---------|---------|---------|---------|
| **tbdxfundtransreq → tbdxfundtranscfm** | `serial_no` + `in_client_no` | 查询交易请求，补充确认表信息 | 确认处理时 |
| **tbdxfundtranscfm → tbdxfundtransreq** | `serial_no` + `in_client_no` | 更新交易请求的确认信息（`cfm_date`、`cfm_no`、`status`） | 确认处理时 |
| **数据同步** | 多个字段 | 将交易请求信息同步到确认表 | `transTransReqBakToCfmBak` 方法 |
| **状态更新** | `status`、`err_code`、`err_msg` | 更新交易请求的确认状态 | 确认成功/失败时 |

2. **与 tbdxfundtranssum 的关系**：
   - 临时备份表 `tbdxfundtranscfm00{tableNum}` 是汇总表的数据源
   - 通过 `cfm_date`、`ta_code`、`prd_code`、`busin_code`、`curr_type` 关联汇总
   - 汇总时从临时表读取数据，避免影响正式表

3. **与 tbdxfundpayment 的关系**：
   - 通过汇总表间接关联
   - 通过 `prd_code`、`ta_code`、`curr_type` 关联更新

4. **与 tbdxfundshare 的关系**：
   - 通过 `in_client_no`、`prd_code` 关联
   - 确认表的确认份额用于更新份额表

## 八、注意事项

### 8.1 数据一致性

1. **主键唯一性**：
   - 数据库主键：确保 `ta_code`、`cfm_date`、`cfm_no` 组合唯一
   - 业务主键：在实际业务操作中，需要确保 `ta_code`、`cfm_date`、`cfm_no`、`bank_no`、`in_client_no` 组合唯一
   - 插入前需要检查是否存在重复记录

2. **金额一致性**：
   - 确保确认金额与TA返回的金额一致
   - 汇总时确保金额计算正确

3. **状态一致性**：
   - 确保状态字段与业务状态一致
   - 状态变更时需要更新相关字段

### 8.2 性能优化

1. **分表处理**：
   - 使用分表可以避免单表数据量过大
   - 汇总时按分表并行处理，提高性能

2. **索引优化**：
   - 建议在 `cfm_date`、`ta_code`、`prd_code`、`busin_code`、`status` 上建立索引
   - 查询时尽量使用主键字段

3. **数据清理**：
   - 日终清算临时表数据在汇总完成后可以删除
   - 根据业务需求定期清理历史数据

### 8.3 特殊业务处理

1. **智能投顾业务**：
   - 根据参数决定是否包含智能投顾业务的确认金额
   - 参数：`TRANSSUM_AND_PAYMENT_CFM_AMT_NOT_CONTAIN_AR`

2. **余额宝产品**：
   - 支持T+1划拨的特殊产品
   - 通过参数 `AUTOTRANSFER_T1_PRD` 配置
   - 排除特定业务代码的汇总

3. **客户类型区分**：
   - 根据参数 `PAY_CLT_TYPE` 决定是否区分机构/个人客户
   - 影响汇总数据的客户类型过滤条件

4. **中登TA特殊处理**：
   - 中登TA（98/99）直接使用 `agency_fee`（代理费）字段作为手续费
   - 不需要按手续费分配比例计算

## 九、常见问题

### 9.1 数据重复问题

**问题**：确认数据出现重复

**原因**：
- 重复插入确认数据
- 主键冲突

**解决**：
- 插入前检查是否存在重复记录
- 使用 `INSERT ... ON DUPLICATE KEY UPDATE` 或类似机制

### 9.2 金额不一致问题

**问题**：确认金额与TA返回金额不一致

**原因**：
- 数据录入错误
- 金额计算错误

**解决**：
- 与TA对账，确认金额是否正确
- 检查金额计算逻辑

### 9.3 状态不一致问题

**问题**：确认状态与业务状态不一致

**原因**：
- 状态更新不及时
- 状态更新逻辑错误

**解决**：
- 及时更新确认状态
- 检查状态更新逻辑

### 9.4 汇总数据缺失问题

**问题**：汇总时部分数据缺失

**原因**：
- 过滤条件过于严格
- 分表数据未完全汇总

**解决**：
- 检查过滤条件是否正确
- 确保所有分表数据都已汇总

## 十、总结

`tbdxfundtranscfm` 表是代销基金交易确认的核心数据表，主要特点：

1. **数据来源**：TA返回的交易确认数据
2. **核心用途**：存储交易确认数据，作为日终清算的数据源，汇总生成交易汇总表，进而支持资金划付处理
3. **重要字段**：
   - 主键字段：数据库主键（`ta_code`、`cfm_date`、`cfm_no`），业务主键（`ta_code`、`cfm_date`、`cfm_no`、`bank_no`、`in_client_no`）
   - 金额字段：`cfm_amt`（确认金额）、`cfm_vol`（确认份额）
   - 费用字段：`charge`（手续费）、`agency_fee`（代理费）、`back_fee`（后收费用）
   - 状态字段：`status`（确认状态）
4. **重要操作**：INSERT（插入确认数据）、UPDATE（更新确认状态和金额）、DELETE（删除确认数据）、SELECT（查询和汇总）
5. **数据流向**：确认表 → 汇总表 → 资金划付表 → 同步表 → 公共库片区

该表在整个代销基金交易流程中起到承上启下的关键作用，是日终清算和资金划付的重要数据基础。

## 十一、clear_date 字段更新逻辑分析

### 11.1 clear_date 字段说明

**字段定义**：
- **字段名**：`clear_date`
- **中文名称**：清算日期
- **数据类型**：INTEGER
- **字段含义**：资金清算入账日期，表示该笔确认交易的资金应该在哪个日期进行清算入账

### 11.2 clear_date 的更新来源和时机

#### 11.2.1 TA文件导入阶段（初始值来源）

**更新时机**：TA确认文件导入时（PUB210103）

**更新逻辑**：
- `clear_date` 的值从TA确认文件中读取
- 直接写入 `tbdxfundtranscfmtmp` 表
- 此时 `clear_date` 的值是TA在文件中提供的原始值

**代码位置**：
- `DxFundTransCfmTmpDao.addTransCfmTmp()` 方法
- 插入时使用 `obj.getClearDate()` 获取值

#### 11.2.2 确认处理阶段（主要更新逻辑）

**更新时机**：日终清算确认处理时（DXFUND210104，各个Action类如Action207122等）

**更新方法**：`Pub200104Service.transCfmBakToSquare()` 方法

**更新逻辑**（按优先级）：

1. **特殊业务的产品配置清算日期**（优先级最高）：
   - **赎回业务**（124、142、125）：
     - 判断条件：`product.getRedDays() >= 系统初始化日期`
     - **如果 `red_days` 是日期值**（>= 系统初始化日期，如20240101）：
       - 则 `clear_date = product.getRedDays()`（直接使用配置的日期）
     - **如果 `red_days` 是天数**（< 系统初始化日期，如2表示T+2）：
       - 不满足条件，走默认逻辑，根据确认日期和延后天数计算
   - **清盘业务**（150、151）：
     - 判断条件：`product.getRedDays() >= 系统初始化日期`
     - **如果 `red_days` 是日期值**：`clear_date = product.getRedDays()`
     - **如果 `red_days` 是天数**：走默认逻辑计算
   - **发行失败业务**（149）：
     - 判断条件：`product.getFailDays() >= 系统初始化日期`
     - **如果 `fail_days` 是日期值**：`clear_date = product.getFailDays()`
     - **如果 `fail_days` 是天数**：走默认逻辑计算
   - **认购结果业务**（130，只影响产品级）：
     - 判断条件：入账方向（`ZWFX_ADD`）且 `product.getFailDays() >= 系统初始化日期`
     - **如果 `fail_days` 是日期值**：`clear_date = product.getFailDays()`
     - **如果 `fail_days` 是天数**：走默认逻辑计算
   - **申购业务**（122，入账方向）：
     - 判断条件：`product.getOpenBuyDays() >= 系统初始化日期`
     - **如果 `open_buy_days` 是日期值**：`clear_date = product.getOpenBuyDays()`
     - **如果 `open_buy_days` 是天数**：走默认逻辑计算
   
   **说明**：
   - 产品表中的 `red_days`、`fail_days`、`open_buy_days` 字段有两种含义：
     - **日期值**：当值 >= 系统初始化日期时，表示具体的资金到账日期（如20240101）
     - **天数**：当值 < 系统初始化日期时，表示延后天数（如2表示T+2，即确认日期+2个工作日）
   - 只有当这些字段是日期值时，才会直接使用；如果是天数，则走默认逻辑，根据确认日期和延后天数计算

2. **根据确认日期和延后天数计算**（默认逻辑）：
   - 如果上述特殊业务配置都不满足（即产品配置的字段是天数而非日期值），则根据以下规则计算：
   - **延后天数来源**：
     - 如果产品配置的字段是天数（如 `red_days = 2`），则使用该天数作为延后天数
     - 否则，根据业务类型和产品配置确定延后天数（T+0、T+1、T+2等）
   
   **计算方式**（根据产品控制位第28位决定）：
   - **第28位 = 1**：使用系统工作日计算
     ```java
     clear_date = PubApiFactory.getNNextSysDate(cfm_date, days)
     ```
   - **第28位 = 0**（默认）：使用产品工作日计算
     ```java
     // 支持币种节假日顺延（星展银行等）
     if (产品配置了币种节假日顺延 && 业务代码在配置范围内) {
         clear_date = PubDxFundApiFactory.getNWorkDateWithCurrencyHoliday(
             prd_code, cfm_date, days)
     } else {
         clear_date = PubDxFundApiFactory.getNWorkDate(prd_code, cfm_date, days)
     }
     ```
   
   **计算参数**：
   - `cfm_date`：确认日期（从确认表中获取）
   - `days`：延后天数（根据业务类型和产品配置确定）
   - `prd_code`：产品代码（用于查询产品工作日历）

3. **特殊参数调整**：
   - **ADVSQUARCDT参数**（上海银行等）：
     - 如果参数值为 "1"，则入账日提前一天
     - `days = (days > 0) ? (days - 1) : days`

**更新到确认表**：
```java
// 计算完成后，将清算日期同步到确认表
transVar.getTransCfmBak().setClearDate(square.getClearDate());
```

**代码位置**：
- `Pub200104Service.transCfmBakToSquare()` 方法（第320-432行）

#### 11.2.3 TA发起的确认处理阶段

**更新时机**：处理TA发起的确认数据时（`Pub200104Service.addTransCfmBak()` 方法）

**更新逻辑**：
```java
transVar.getTransCfmBak().setClearDate(transVar.getSysArg().getInitDate());
```

**说明**：
- 直接设置为系统初始化日期（清算日期）
- 适用于TA主动发起的确认数据（如账户类确认等）

**代码位置**：
- `Pub200104Service.addTransCfmBak()` 方法（第1207行、1213行）

#### 11.2.4 数据回写阶段

**更新时机**：单TA清算结束任务（DXFUND210110，TransCfmDealTask）

**更新逻辑**：
- 从清算临时表 `tbdxfundtranscfm00{tableNum}` 回写到正式表 `tbdxfundtranscfm{tableNum}`
- `clear_date` 字段直接复制，不进行重新计算

**SQL示例**：
```sql
INSERT INTO tbdxfundtranscfm{tableNum} 
(..., clear_date, ...)
SELECT 
    ..., 
    COALESCE(clear_date, 0), 
    ...
FROM tbdxfundtranscfm00{tableNum}
WHERE ta_code = ?
```

**代码位置**：
- `TransCfmDealTask.java`（第271-300行）

### 11.3 clear_date 的计算规则总结

#### 11.3.1 计算优先级

```
1. 特殊业务的产品配置清算日期（最高优先级）
   ├─ 赎回业务（124、142、125）
   │   ├─ 如果 product.red_days >= 系统初始化日期 → clear_date = product.red_days（日期值）
   │   └─ 如果 product.red_days < 系统初始化日期 → 走默认逻辑，使用 red_days 作为延后天数
   ├─ 清盘业务（150、151）
   │   ├─ 如果 product.red_days >= 系统初始化日期 → clear_date = product.red_days（日期值）
   │   └─ 如果 product.red_days < 系统初始化日期 → 走默认逻辑，使用 red_days 作为延后天数
   ├─ 发行失败业务（149）
   │   ├─ 如果 product.fail_days >= 系统初始化日期 → clear_date = product.fail_days（日期值）
   │   └─ 如果 product.fail_days < 系统初始化日期 → 走默认逻辑，使用 fail_days 作为延后天数
   ├─ 认购结果业务（130）
   │   ├─ 如果 product.fail_days >= 系统初始化日期 → clear_date = product.fail_days（日期值）
   │   └─ 如果 product.fail_days < 系统初始化日期 → 走默认逻辑，使用 fail_days 作为延后天数
   └─ 申购业务（122）
       ├─ 如果 product.open_buy_days >= 系统初始化日期 → clear_date = product.open_buy_days（日期值）
       └─ 如果 product.open_buy_days < 系统初始化日期 → 走默认逻辑，使用 open_buy_days 作为延后天数

2. 根据确认日期和延后天数计算（默认逻辑）
   ├─ 产品控制位第28位 = 1 → 系统工作日计算
   └─ 产品控制位第28位 = 0 → 产品工作日计算（默认）
       ├─ 支持币种节假日顺延（星展银行等）
       └─ 不支持币种节假日顺延

3. 特殊参数调整
   └─ ADVSQUARCDT参数 → 入账日提前一天
```

#### 11.3.2 计算影响因素

| 影响因素 | 说明 | 影响范围 |
|---------|------|---------|
| **业务代码** | 不同业务代码使用不同的产品配置字段 | 124、142、125、150、151、149、130、122等 |
| **产品配置** | 产品的资金到账日配置 | `red_days`、`fail_days`、`open_buy_days`（可能是日期值或天数） |
| **确认日期** | TA返回的确认日期 | `cfm_date` |
| **延后天数** | 根据业务类型和产品配置确定 | `days`（T+0、T+1、T+2等） |
| **产品控制位** | 第28位决定使用系统工作日还是产品工作日 | 0-产品工作日（默认），1-系统工作日 |
| **币种节假日** | 是否支持币种节假日顺延 | 星展银行等特殊需求 |
| **系统参数** | ADVSQUARCDT参数 | 上海银行等特殊需求 |

### 11.4 clear_date 的数据流转

```
阶段1：TA文件导入
TA确认文件（clear_date字段）
    ↓ [PUB210103导入]
tbdxfundtranscfmtmp（clear_date = TA文件中的值）
    ↓ [Action207000数据准备]
阶段2：转移到清算临时表
tbdxfundtranscfm00（clear_date = 临时表中的值）
    ↓ [确认处理：transCfmBakToSquare]
阶段3：计算清算日期
计算 clear_date（根据业务类型、产品配置、确认日期等）
    ↓ [更新确认表]
tbdxfundtranscfm00（clear_date = 计算后的值）
    ↓ [TransCfmDealTask数据回写]
阶段4：回写到正式表
tbdxfundtranscfm（clear_date = 最终值）
```

### 11.5 clear_date 的使用场景

1. **资金清算入账**：
   - `clear_date` 用于确定资金应该在哪个日期进行清算入账
   - 与 `tbdxfundsquare`（清算入账表）的 `clear_date` 字段关联

2. **资金划付**：
   - 在汇总和资金划付时，`clear_date` 用于确定资金划付的日期
   - 与 `tbdxfundpayment`（资金划付表）的 `clear_date` 字段关联

3. **对账和报表**：
   - 用于生成按清算日期统计的报表
   - 用于与TA进行资金对账

### 11.6 clear_date 的注意事项

1. **数据一致性**：
   - `tbdxfundtranscfm.clear_date` 应该与 `tbdxfundsquare.clear_date` 保持一致
   - 如果两者不一致，可能导致资金清算日期错误

2. **工作日计算**：
   - 清算日期的计算需要考虑工作日历（系统工作日或产品工作日）
   - 特殊情况下需要考虑币种节假日

3. **产品配置优先级和含义**：
   - 特殊业务的产品配置清算日期优先级最高
   - **产品配置字段的双重含义**：
     - **日期值**（>= 系统初始化日期）：直接作为清算日期使用
     - **天数**（< 系统初始化日期）：作为延后天数，需要根据确认日期计算
   - 例如：如果 `red_days = 2`（天数），则 `clear_date = cfm_date + 2个工作日`
   - 例如：如果 `red_days = 20240101`（日期值），则 `clear_date = 20240101`

4. **参数影响**：
   - 某些银行（如上海银行）有特殊参数（ADVSQUARCDT）会影响清算日期的计算
   - 需要根据实际业务需求配置参数

5. **TA文件导入**：
   - TA文件中的 `clear_date` 值在导入时会被保留
   - 但在确认处理时，会根据业务规则重新计算，可能会覆盖TA文件中的值

