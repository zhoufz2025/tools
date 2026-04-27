# tbdxfundpayment 表使用场景及重要字段更新逻辑分析

## 一、表的基本信息

### 1.1 表名
`tbdxfundpayment` - 基金资金划付表

### 1.2 主要字段说明（基于 DxFundPayMent.java 实体类）

| 字段名 | 中文名称 | 数据类型 | 说明 |
|--------|---------|---------|------|
| clear_date | 清算日期 | String | 资金清算日期 |
| square_date | 入账日期 | String | 资金入账日期 |
| prd_code | 产品代码 | String | 基金产品代码 |
| ta_code | TA代码 | String | 登记机构代码 |
| curr_type | 币种 | String | 货币类型 |
| prd_manager | 产品管理人 | String | 产品管理人代码 |
| red_amt | 赎回金额 | String | 赎回业务金额 |
| div_amt | 分红金额 | String | 分红业务金额 |
| refund_amt | 比例退款 | String | 退款金额 |
| other_in | 其他到款 | String | 其他收入金额 |
| tot_amt | 总金额 | String | 总划付金额 |
| req_amt | 认申购申请款 | String | 认申购申请金额 |
| cfm_amt | 确认金额 | String | 确认的认申购金额 |
| charge | 手续费 | String | 认申购手续费 |
| red_charge | 赎回手续费 | String | 赎回业务手续费 |
| trans_charge | 基金转换费 | String | 转换手续费 |
| fail_amt | 失败金额 | String | 失败交易金额 |
| other_amt | 其他金额 | String | 其他业务金额 |
| entry_date | 录入日期 | String | 数据录入日期 |
| deal_status | 处理状态 | String | 0-待支付，1-已处理 |
| reserve | 预留字段 | String | 扩展字段 |
| bank_no | 银行编号 | String | 银行代码 |
| area_id | 片区号 | String | 数据分片标识 |
| batch_no | 批次号 | String | 批次编号 |
| trans_date | 交易日期 | String | 交易日期（业务日期） |

## 二、主要使用场景

### 2.1 日终清算场景（T210110 - TranssumPaymentFunc）

**核心功能**：在日终清算时，汇总生成下一日的资金划付数据

**主要流程**：
1. **数据初始化**：从 `tbdxfundsquare` 表汇总生成初始的划付记录
   - 汇总赎回金额（red_amt）：业务代码 124、163、142、125、149、151、150
   - 汇总分红金额（div_amt）：业务代码 143、743
   - 汇总退款金额（refund_amt）：业务代码 130、888

2. **确认金额更新**：从 `tbdxfundtranssum` 表更新认申购确认金额（cfm_amt）
   - 业务代码：120（认购）、122（申购）、139（定投）、887（其他认申购）

3. **手续费计算**：
   - **认申购手续费（charge）**：根据产品手续费分配比例计算
   - **赎回手续费（red_charge）**：根据赎回业务汇总计算
   - 支持中登TA（98/99）直接取代理费字段

4. **总金额计算**：`tot_amt = red_amt + div_amt + refund_amt + other_amt`

5. **币种更新**：从产品表（tbproduct）更新币种信息

6. **特殊业务处理**：
   - 募集失败（149）与认购成功（120）同时存在时的金额调整
   - 余额宝产品T+1划拨支持
   - 后收费产品手续费过滤

### 2.2 资金勾对场景（T210013 - T210013HSAdapter）

**核心功能**：资金录入表（tbdxfundreceipt）与资金划付表（tbdxfundpayment）进行勾对

**处理逻辑**：
- **正向勾对（默认不入账）**：更新 `deal_status` 从 "0"（待支付）到 "1"（已处理）
- **反向勾对（默认入账）**：根据勾对结果更新状态
- **分业务勾对**：支持按业务类型进行精确勾对

### 2.3 报表生成场景

**用途**：生成资金划付报表，供银行对账使用

**查询场景**：
- 按TA代码、交易日期、片区号查询
- 汇总总金额、手续费等信息
- 与产品银行账户表（tbdxfundprdbankacc）关联查询

### 2.4 数据同步场景

**同步表**：`tbdxfundpaymentsync`
- 用于跨片区数据同步
- 同步条件：ta_code、trans_date、area_id

## 三、重要字段更新逻辑详解

### 3.1 cfm_amt（确认金额）更新逻辑

**更新时机**：日终清算时

**更新来源**：`tbdxfundtranssum` 表

**更新条件**：
```sql
UPDATE tbdxfundpayment 
SET cfm_amt = (
    SELECT SUM(cfm_amt) 
    FROM tbdxfundtranssum 
    WHERE sum_date = ? 
      AND ta_code = ? 
      AND busin_code IN ('120', '122', '139', '887')
      AND prd_code = tbdxfundpayment.prd_code
      AND ta_code = tbdxfundpayment.ta_code
      AND curr_type = tbdxfundpayment.curr_type
)
WHERE ta_code = ? 
  AND trans_date = ? 
  AND clear_date = ?
```

**特殊处理**：
- 如果同一天同一条流水既有120成功又有149（募集失败），则从cfm_amt中扣除120的金额
- 条件：参数 `RETURN_AMT_FROM_ACCOUNT` = '1'（使用认申购归集户返款）

### 3.2 charge（认申购手续费）更新逻辑

**更新时机**：日终清算时

**计算方式**：
1. **普通TA**：根据产品手续费分配比例（prdFareDistribMap）计算
   ```sql
   charge = SUM(transsum.charge * sellerRate)
   ```
   其中 sellerRate 根据业务代码（120/122/139/887）从产品手续费分配配置中获取

2. **中登TA（98/99）**：直接取代理费字段
   ```sql
   charge = SUM(agency_fee)
   ```

**更新条件**：
- 业务代码：120、122、139、887
- 支持过滤后端收费产品（ShareClass = 'B'）

**空值处理**：如果 charge 为 NULL，更新为 0.0

### 3.3 red_charge（赎回手续费）更新逻辑

**更新时机**：日终清算时

**计算方式**：
1. **显示赎回手续费（REDFEESHOW = '1'）**：
   - 从临时表 `tbshsxf_tmp` 汇总
   - 或根据产品手续费分配比例计算

2. **不显示赎回手续费（REDFEESHOW = '0'）**：
   - 从 `tbdxfundtranssum` 表汇总
   - 业务代码：124、163、125、142

**更新条件**：
- 业务代码：124（普通赎回）、163（快速赎回）、125（预约赎回）、142（其他赎回）

**空值处理**：如果 red_charge 为 NULL，更新为 0.0

### 3.4 tot_amt（总金额）更新逻辑

**更新时机**：所有金额字段更新完成后

**计算公式**：
```sql
UPDATE tbdxfundpayment 
SET tot_amt = red_amt + div_amt + refund_amt + other_amt
WHERE ta_code = ? AND trans_date = ?
```

### 3.5 red_amt、div_amt、refund_amt（兑付金额）更新逻辑

**更新时机**：数据初始化时

**数据来源**：`tbdxfundsquare` 表

**汇总逻辑**：
```sql
INSERT INTO tbdxfundpayment 
(clear_date, square_date, prd_code, ta_code, curr_type, 
 red_amt, div_amt, refund_amt, trans_date, deal_status, area_id)
SELECT 
    clear_date, 
    clear_date,  -- 默认不入账时，square_date = clear_date
    prd_code, 
    ta_code, 
    curr_type,
    SUM(CASE WHEN busin_code IN ('124','163', '142', '125', '149', '151', '150') 
             THEN amt ELSE 0 END) red_amt,
    SUM(CASE busin_code 
            WHEN '143' THEN amt 
            WHEN '743' THEN amt 
            ELSE 0 END) div_amt,
    SUM(CASE WHEN busin_code IN ('130', '888') 
             THEN amt ELSE 0 END) refund_amt,
    ? trans_date, 
    '0' deal_status,
    ? area_id
FROM tbdxfundsquare
WHERE ta_code = ? 
  AND trans_date = ?
GROUP BY clear_date, prd_code, ta_code, curr_type
```

### 3.6 curr_type（币种）更新逻辑

**更新时机**：数据初始化后，如果币种为空

**更新来源**：`tbproduct` 表

**更新逻辑**：
```sql
UPDATE tbdxfundpayment 
SET curr_type = (
    SELECT curr_type 
    FROM tbproduct 
    WHERE prd_code = tbdxfundpayment.prd_code 
      AND ta_code = tbdxfundpayment.ta_code
)
WHERE ta_code = ? 
  AND trans_date = ? 
  AND (curr_type = ' ' OR curr_type IS NULL)
```

### 3.7 deal_status（处理状态）更新逻辑

**状态值**：
- '0'：待支付
- '1'：已处理（已勾对）

**更新场景**：
1. **初始化**：插入时默认为 '0'
2. **资金勾对**：勾对成功后更新为 '1'
3. **批量更新**：在入账调整时批量更新状态

**更新逻辑**（分业务勾对）：
```sql
UPDATE tbdxfundpayment a 
SET a.deal_status = '1' 
WHERE a.deal_status = '0'
  AND EXISTS (
      SELECT 1 
      FROM tbdxfundreceipt b 
      WHERE b.clear_date = a.clear_date 
        AND b.prd_code = a.prd_code 
        AND b.trans_date = a.trans_date 
        AND b.deal_status = '1' 
        AND b.square_date = ?
  )
  AND NOT EXISTS (
      SELECT 1 
      FROM tbdxfundreceipt b 
      WHERE b.clear_date = a.clear_date 
        AND b.prd_code = a.prd_code 
        AND b.trans_date = a.trans_date 
        AND b.deal_status = '0' 
        AND b.square_date = ?
  )
```

### 3.8 prd_manager（产品管理人）更新逻辑

**更新时机**：数据初始化后

**更新来源**：`tbproduct` 表

**更新逻辑**：
```sql
UPDATE tbdxfundpayment 
SET prd_manager = (
    SELECT prd_manager 
    FROM tbproduct 
    WHERE prd_code = tbdxfundpayment.prd_code 
      AND ta_code = tbdxfundpayment.ta_code
)
WHERE trans_date = ? AND ta_code = ?
```

## 四、关键业务规则

### 4.1 数据生成规则

1. **默认不入账/入账时**：`square_date = clear_date`
2. **数据分组维度**：clear_date、prd_code、ta_code、curr_type
3. **数据删除规则**：重复操作时先删除当日数据（按 ta_code、trans_date）

### 4.2 手续费计算规则

1. **产品手续费分配**：根据产品代码和业务代码从配置表获取分配比例
2. **中登TA特殊处理**：直接使用代理费字段，不进行比例分配
3. **后端收费产品过滤**：ShareClass = 'B' 的产品，手续费置为0

### 4.3 特殊业务处理

1. **募集失败处理**：
   - 如果149使用认申购归集户返款（RETURN_AMT_FROM_ACCOUNT = '1'）
   - 同一天同一条流水120成功且有149，则120不向TA划款
   - 从cfm_amt中扣除对应的120金额

2. **余额宝产品**：
   - 支持T+1划拨
   - 通过 prdCondition 条件过滤

3. **客户类型区分**：
   - 支持机构/个人客户类型区分
   - 通过 clientTypeCondition 条件过滤

### 4.4 数据同步规则

1. **同步表**：tbdxfundpaymentsync
2. **同步条件**：ta_code、trans_date、area_id
3. **同步时机**：日终清算完成后

## 五、相关表关联

### 5.1 主要关联表

| 表名 | 关联关系 | 用途 |
|------|---------|------|
| tbdxfundsquare | 数据源 | 提供初始的兑付金额数据 |
| tbdxfundtranssum | 数据源 | 提供确认金额和手续费数据 |
| tbdxfundreceipt | 勾对 | 资金录入表，用于勾对 |
| tbproduct | 参考 | 获取产品币种、管理人信息 |
| tbdxfundprdbankacc | 关联 | 产品银行账户信息 |
| tbdxfundpaymentdetail | 明细 | 资金划付明细表 |
| tbdxfundpaymentorg | 机构 | 机构客户资金划付表 |

### 5.2 临时表

- `tbshsxf_tmp{taCode}`：用于汇总赎回手续费

## 六、性能优化点

### 6.1 SQL优化

1. **避免死锁**：使用 EXISTS 子查询替代 JOIN，减少锁竞争
2. **批量更新**：按产品代码分组批量更新，减少更新次数
3. **索引优化**：主要查询条件（ta_code、trans_date、clear_date、prd_code、curr_type）需要建立索引

### 6.2 事务控制

1. **事务拆分**：将大事务拆分为多个小事务，减少锁持有时间
2. **事务边界**：每个更新步骤独立事务，失败可回滚

## 七、注意事项

1. **数据一致性**：更新 cfm_amt 时需要考虑募集失败的特殊情况
2. **手续费计算**：不同TA类型（普通TA vs 中登TA）计算方式不同
3. **空值处理**：charge 和 red_charge 需要将 NULL 更新为 0.0
4. **币种处理**：如果币种为空，需要从产品表补充
5. **状态管理**：deal_status 的状态变更需要与 tbdxfundreceipt 表同步

## 八、主键字段更新逻辑详解

### 8.1 主键字段组成

根据表结构定义，`tbdxfundpayment` 表的主键由以下7个字段组成（按顺序）：

1. **area_id** (Key 1) - 片区号
2. **table_num** (Key 2) - 分表号
3. **clear_date** (Key 3) - 清算日期
4. **prd_code** (Key 4) - 产品代码
5. **ta_code** (Key 5) - TA代码
6. **curr_type** (Key 6) - 币种
7. **trans_date** (Key 7) - 交易日期

### 8.2 主键字段赋值逻辑

#### 8.2.1 area_id（片区号）

**赋值方式**：
```java
ShardingUtil.getCurrentAreaId()
```

**说明**：
- 通过 `ShardingUtil.getCurrentAreaId()` 获取当前片区号
- 用于数据分片，支持多片区部署
- 所有插入操作都会设置此字段

**使用场景**：
- 插入数据时：`area_id = ShardingUtil.getCurrentAreaId()`
- 删除数据时：作为同步表的查询条件
- 数据同步时：按 `area_id` 进行片区数据同步

#### 8.2.2 table_num（分表号）

**赋值方式**：
- **普通业务**：不设置（默认为 NULL 或 '0'）
- **余额宝业务**：`'YebSub'`

**说明**：
- 用于区分不同类型的业务数据
- 普通资金划付：不设置此字段
- 余额宝T+1划拨：设置为 `'YebSub'`

**代码示例**：
```sql
-- 普通业务插入（不包含 table_num）
INSERT INTO tbdxfundpayment 
(clear_date, square_date, prd_code, ta_code, curr_type, ..., area_id)

-- 余额宝业务插入（包含 table_num）
INSERT INTO tbdxfundpayment 
(..., area_id, table_num) 
VALUES (..., 'YebSub')
```

**删除逻辑**：
```sql
-- 普通业务删除
DELETE FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ?

-- 余额宝业务删除
DELETE FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ? AND table_num = 'YebSub'
```

#### 8.2.3 clear_date（清算日期）

**赋值方式**：
1. **从源表获取**：从 `tbdxfundsquare` 表的 `clear_date` 字段获取
2. **使用下一TA工作日**：`nextTaWorkDate`

**计算逻辑**：
```java
// 默认：下一TA工作日
int nextTaWorkDate = PubApiFactory.getNextTaDate(
    transVar.getTaInfo().getTaCode(), 
    transVar.getSysArg().getInitDate()
);

// 如果参数 DX_AUTOTRANS_INITDAY = '1'，则使用当日
if (IDict.K_YORN.YORN_YES.equals(
    ParamCache.getInstance().getParamValue(
        transVar.getTaInfo().getTaCode(), 
        IParamConstant.DX_AUTOTRANS_INITDAY, 
        IDict.K_YORN.YORN_NO
    )
)) {
    nextTaWorkDate = transVar.getSysArg().getInitDate();
}
```

**使用场景**：
- **场景1**：从 `tbdxfundsquare` 汇总兑付数据
  ```sql
  SELECT clear_date, ... FROM tbdxfundsquare
  ```
  此时 `clear_date` 来自源表

- **场景2**：插入认申购确认数据
  ```sql
  INSERT INTO tbdxfundpayment 
  (clear_date, ...) 
  VALUES (nextTaWorkDate, ...)
  ```
  此时 `clear_date = nextTaWorkDate`（下一TA工作日）

- **场景3**：余额宝业务
  ```sql
  INSERT INTO tbdxfundpayment 
  (clear_date, ...) 
  VALUES (nextTaWorkDate, ...)
  ```

**业务含义**：
- `clear_date` 表示资金清算日期，即资金实际划付的日期
- 对于当日确认的业务，`clear_date` 通常是下一TA工作日

#### 8.2.4 prd_code（产品代码）

**赋值方式**：
- 从源表获取：`tbdxfundsquare.prd_code`、`tbdxfundtranssum.prd_code`、`tbtransreqbak.prd_code`

**说明**：
- 所有插入操作都从源表获取产品代码
- 分组维度：按 `prd_code` 进行分组汇总

**使用场景**：
```sql
-- 从 tbdxfundsquare 获取
SELECT prd_code, ... FROM tbdxfundsquare GROUP BY prd_code

-- 从 tbdxfundtranssum 获取
SELECT prd_code, ... FROM tbdxfundtranssum GROUP BY prd_code
```

#### 8.2.5 ta_code（TA代码）

**赋值方式**：
- 从源表获取：`tbdxfundsquare.ta_code`、`tbdxfundtranssum.ta_code`
- 或使用：`transVar.getTaInfo().getTaCode()`

**说明**：
- TA代码标识登记机构
- 在删除和查询时作为主要过滤条件

**使用场景**：
```sql
-- 删除时使用
DELETE FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ?

-- 更新时使用
UPDATE tbdxfundpayment 
SET ... 
WHERE ta_code = ? AND trans_date = ? AND clear_date = ?
```

#### 8.2.6 curr_type（币种）

**赋值方式**：
1. **初始插入**：从源表获取（`tbdxfundsquare.curr_type`、`tbdxfundtranssum.curr_type`）
2. **后续更新**：如果为空，从 `tbproduct` 表补充

**更新逻辑**：
```sql
-- 如果币种为空，从产品表更新
UPDATE tbdxfundpayment 
SET curr_type = (
    SELECT curr_type 
    FROM tbproduct 
    WHERE prd_code = tbdxfundpayment.prd_code 
      AND ta_code = tbdxfundpayment.ta_code
)
WHERE ta_code = ? 
  AND trans_date = ? 
  AND (curr_type = ' ' OR curr_type IS NULL)
```

**说明**：
- 币种是主键的一部分，确保同一产品同一币种的数据唯一
- 如果源表中币种为空，会在后续步骤中从产品表补充

#### 8.2.7 trans_date（交易日期）

**赋值方式**：
```java
transVar.getSysArg().getInitDate()
```

**说明**：
- `trans_date` 表示业务日期（交易日期），即数据录入日期
- 所有插入操作都使用当前业务日期
- 与 `clear_date` 的区别：
  - `trans_date`：业务日期（数据录入日期）
  - `clear_date`：清算日期（资金划付日期）

**使用场景**：
```sql
-- 插入时
INSERT INTO tbdxfundpayment 
(..., trans_date, ...) 
VALUES (..., transVar.getSysArg().getInitDate(), ...)

-- 删除时
DELETE FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ?

-- 查询时
SELECT * FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ? AND area_id = ?
```

### 8.3 主键字段在删除操作中的使用

#### 8.3.1 普通业务删除

**删除条件**：
```sql
DELETE FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ?
```

**说明**：
- 使用 `ta_code` 和 `trans_date` 作为删除条件
- 删除当日该TA的所有资金划付数据
- 删除发生在数据插入之前，确保重复操作时数据一致性

#### 8.3.2 余额宝业务删除

**删除条件**：
```sql
DELETE FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ? AND table_num = 'YebSub'
```

**说明**：
- 增加 `table_num = 'YebSub'` 条件
- 只删除余额宝相关的数据，不影响普通业务数据

### 8.4 主键字段在更新操作中的使用

#### 8.4.1 更新条件

更新操作通常使用主键字段作为WHERE条件：

```sql
-- 更新确认金额
UPDATE tbdxfundpayment 
SET cfm_amt = ...
WHERE ta_code = ? 
  AND trans_date = ? 
  AND clear_date = ? 
  AND (prd_code, curr_type) IN (...)
```

**说明**：
- 使用 `ta_code`、`trans_date`、`clear_date` 作为主要过滤条件
- 使用 `prd_code`、`curr_type` 进行精确匹配
- 避免使用 `area_id` 和 `table_num` 作为更新条件（除非特殊场景）

#### 8.4.2 更新时的主键匹配

```sql
-- 示例：更新手续费
UPDATE tbdxfundpayment 
SET charge = (
    SELECT SUM(charge) 
    FROM tbdxfundtranssum 
    WHERE sum_date = ? 
      AND ta_code = tbdxfundpayment.ta_code
      AND prd_code = tbdxfundpayment.prd_code
      AND curr_type = tbdxfundpayment.curr_type
)
WHERE ta_code = ? 
  AND trans_date = ? 
  AND clear_date = ?
```

**说明**：
- 子查询中使用主键字段进行关联匹配
- 确保更新数据的准确性

### 8.5 主键字段在查询操作中的使用

#### 8.5.1 DAO层查询

**完整主键查询**（DxFundPayMentDao）：
```sql
SELECT * FROM tbdxfundpayment 
WHERE clear_date = ? 
  AND prd_code = ? 
  AND ta_code = ? 
  AND curr_type = ? 
  AND entry_date = ? 
  AND bank_no = ? 
  AND area_id = ? 
  AND batch_no = ?
```

**说明**：
- DAO层使用完整主键进行精确查询
- 注意：DAO层使用的是旧的主键定义（包含 `entry_date`、`bank_no`、`batch_no`），可能与实际表结构不一致

#### 8.5.2 业务层查询

**常用查询条件**：
```sql
-- 按TA和交易日期查询
SELECT * FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ? AND area_id = ?

-- 按清算日期查询
SELECT * FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ? AND clear_date = ?

-- 余额宝业务查询
SELECT * FROM tbdxfundpayment 
WHERE ta_code = ? AND trans_date = ? AND table_num = 'YebSub'
```

### 8.6 主键字段的唯一性保证

#### 8.6.1 插入时的唯一性检查

**检查逻辑**：
```sql
-- 插入前检查是否存在
INSERT INTO tbdxfundpayment (...) 
SELECT ... 
WHERE NOT EXISTS (
    SELECT prd_code 
    FROM tbdxfundpayment 
    WHERE clear_date = ? 
      AND prd_code = a.prd_code 
      AND curr_type = a.curr_type 
      AND ta_code = a.ta_code 
      AND trans_date = ?
)
```

**说明**：
- 使用 `NOT EXISTS` 确保不插入重复数据
- 检查条件使用主键字段：`clear_date`、`prd_code`、`curr_type`、`ta_code`、`trans_date`

#### 8.6.2 分组维度

**数据分组**：
```sql
GROUP BY clear_date, prd_code, ta_code, curr_type
```

**说明**：
- 分组维度与主键字段基本一致（不包含 `area_id`、`table_num`、`trans_date`）
- 确保同一清算日期、同一产品、同一TA、同一币种只有一条汇总记录

### 8.7 主键字段的特殊处理

#### 8.7.1 币种为空时的处理

**问题**：如果源数据中 `curr_type` 为空，会导致主键不完整

**处理方式**：
1. 插入时允许 `curr_type` 为空
2. 后续步骤从产品表补充币种信息
3. 补充后再进行后续业务处理

#### 8.7.2 片区号的处理

**说明**：
- `area_id` 是主键的一部分，用于数据分片
- 不同片区的数据可以有不同的主键值
- 数据同步时需要指定 `area_id`

#### 8.7.3 分表号的处理

**说明**：
- `table_num` 用于区分业务类型
- 普通业务：`table_num` 为空或 '0'
- 余额宝业务：`table_num = 'YebSub'`
- 查询时需要根据业务类型指定 `table_num`

### 8.8 主键更新逻辑总结

| 主键字段 | 赋值来源 | 更新时机 | 特殊处理 |
|---------|---------|---------|---------|
| area_id | `ShardingUtil.getCurrentAreaId()` | 插入时 | 固定值，不更新 |
| table_num | 普通业务：不设置<br>余额宝：'YebSub' | 插入时 | 固定值，不更新 |
| clear_date | 源表 `clear_date` 或 `nextTaWorkDate` | 插入时 | 根据业务类型决定 |
| prd_code | 源表 `prd_code` | 插入时 | 不更新 |
| ta_code | 源表 `ta_code` 或 `transVar.getTaInfo().getTaCode()` | 插入时 | 不更新 |
| curr_type | 源表 `curr_type`，后续从产品表补充 | 插入时+后续更新 | 如果为空，从产品表更新 |
| trans_date | `transVar.getSysArg().getInitDate()` | 插入时 | 固定值，不更新 |

### 8.9 注意事项

1. **主键不可更新**：主键字段在插入后不应更新，如需修改应删除后重新插入
2. **删除操作**：删除时使用 `ta_code` + `trans_date`（+ `table_num`）作为条件，不是完整主键
3. **数据一致性**：插入前先删除当日数据，确保数据一致性
4. **币种处理**：如果币种为空，需要在后续步骤中补充，否则可能影响主键唯一性
5. **片区隔离**：不同片区的数据通过 `area_id` 隔离，查询时需要指定 `area_id`
6. **业务区分**：余额宝业务通过 `table_num = 'YebSub'` 区分，查询和删除时需要指定

## 九、tbdxfundpayment 表划款流程详解

### 9.1 划款流程概述

`tbdxfundpayment` 表是资金划付的核心数据表，划款流程主要包括以下几个步骤：

1. **数据汇总**：从 `tbdxfundpayment` 表汇总生成自动划拨表（`tbdxfundautotransfer`）
2. **划款校验**：校验内部账户余额是否充足
3. **划款执行**：通过联机报文或批量文件方式执行划款
4. **状态更新**：更新划款状态和结果

### 9.2 自动划拨表生成（T210025）

#### 9.2.1 功能说明

**交易代码**：T210025  
**实现类**：`T210025AutoTransfer`  
**核心方法**：`calAutTransferAmt()`

**功能**：从 `tbdxfundpayment` 表汇总生成自动划拨表（`tbdxfundautotransfer`），为后续划款提供数据基础。

#### 9.2.2 认申购款汇总（calBuyAmt）

**数据来源**：`tbdxfundpayment` 表的 `cfm_amt`（确认金额）和 `charge`（手续费）

**汇总逻辑**：

1. **认申购款汇总**（`amt_usage = '1'` 或 `'9'`）：
   ```sql
   INSERT INTO tbdxfundautotransfer (
       bank_no, clear_date, oper_no, ta_code, prd_code,
       bank_acc, bank_acc_name, open_bank, open_bank_name,
       targ_bank_acc, targ_bank_acc_name,
       curr_type, cash_flag, amt, amt_usage, ...
   )
   SELECT 
       b.bank_no, a.clear_date, oper_no, a.ta_code, a.prd_code,
       c.debit_account, -- 认申购归集户账号
       CASE d.busin_code 
           WHEN '120' THEN c.open_bank_ver ELSE c.open_bank_up 
       END, -- 开户行（认购用募集期，申购用开放期）
       CASE d.busin_code 
           WHEN '120' THEN c.bank_acc_ver ELSE c.bank_acc_up 
       END, -- 目标账户（认购用募集期，申购用开放期）
       a.curr_type, b.cash_flag,
       SUM(CASE c.square_way 
           WHEN '1' THEN a.cfm_amt - CASE d.busin_code WHEN '120' THEN 0 ELSE a.charge END 
           ELSE a.cfm_amt 
       END), -- 划款金额（如果square_way='1'，需要扣除手续费）
       CASE d.busin_code WHEN '120' THEN '1' ELSE '9' END, -- 资金用途：1-认购款，9-申购款
       ...
   FROM tbdxfundpayment a
   LEFT JOIN tbdxfundtranssum d ON a.trans_date = d.sum_date 
       AND a.prd_code = d.prd_code 
       AND a.ta_code = d.ta_code 
       AND a.curr_type = d.curr_type
   JOIN tbdxfundproduct b ON a.prd_code = b.prd_code AND a.ta_code = b.ta_code
   JOIN tbdxfundprdbankacc c ON a.prd_code = c.prd_code AND a.ta_code = c.ta_code
   WHERE a.trans_date = ? 
       AND a.cfm_amt > 0
       AND d.busin_code IN ('120', '122', '139', '887') -- 认购、申购、定投、其他认申购
   GROUP BY ...
   ```

2. **手续费汇总**（`amt_usage = '2'`）：
   ```sql
   INSERT INTO tbdxfundautotransfer (
       bank_no, clear_date, oper_no, ta_code, prd_code,
       bank_acc, bank_acc_name, open_bank, open_bank_name,
       targ_bank_acc, targ_bank_acc_name,
       curr_type, cash_flag, amt, amt_usage, remark, ...
   )
   SELECT 
       b.bank_no, a.clear_date, oper_no, a.ta_code, a.prd_code,
       c.debit_account, -- 认申购归集户账号
       c.charge_account, -- 手续费专户账号
       a.curr_type, b.cash_flag,
       SUM(a.charge), -- 手续费金额
       '2', -- 资金用途：2-手续费
       '申购手续费', -- 备注
       ...
   FROM tbdxfundpayment a
   LEFT JOIN tbdxfundtranssum d ON a.trans_date = d.sum_date 
       AND a.prd_code = d.prd_code 
       AND a.ta_code = d.ta_code 
       AND a.curr_type = d.curr_type
   JOIN tbdxfundproduct b ON a.prd_code = b.prd_code AND a.ta_code = b.ta_code
   JOIN tbdxfundprdbankacc c ON a.prd_code = c.prd_code AND a.ta_code = c.ta_code
   WHERE a.trans_date = ? 
       AND d.busin_code IN ('122', '139', '887') -- 申购、定投、其他认申购
       AND c.square_way = '1' -- 手续费单独划拨
       AND a.charge > 0
   GROUP BY ...
   ```

**关键字段说明**：

- **bank_acc**：划出账户（认申购归集户账号，来自 `tbdxfundprdbankacc.debit_account`）
- **targ_bank_acc**：划入账户（TA账户，认购用 `bank_acc_ver`，申购用 `bank_acc_up`）
- **amt**：划款金额（认申购款需要根据 `square_way` 判断是否扣除手续费）
- **amt_usage**：资金用途（'1'-认购款，'9'-申购款，'2'-手续费）

#### 9.2.3 兑付资金汇总

**说明**：兑付资金（赎回、分红、退款）的汇总逻辑不在 `T210025AutoTransfer` 中，而是在其他交易中处理。

**数据来源**：`tbdxfundpayment` 表的 `tot_amt`（总金额）或 `red_amt`、`div_amt`、`refund_amt`

**查询示例**（T219002 - 内部账户余额校验）：
```sql
SELECT 
    SUM(a.tot_amt) Amt, 
    b.crebit_account CrebitAccount 
FROM tbdxfundpayment a, tbdxfundprdbankacc b 
WHERE a.prd_code = b.prd_code 
    AND a.clear_date = ?
GROUP BY b.crebit_account
```

**说明**：
- 通过 `clear_date` 查询需要划款的记录
- 按 `crebit_account`（赎回归集户账号）汇总总金额
- 用于校验内部账户余额是否充足

### 9.3 资金划拨执行（T210026）

#### 9.3.1 功能说明

**交易代码**：T210026  
**实现类**：`T210026HSAdapter`  
**核心方法**：`toLocal()`、`toHost()`

**功能**：从 `tbdxfundautotransfer` 表读取划款数据，执行资金划拨。

#### 9.3.2 划款模式

**两种模式**：

1. **联机报文模式**（`PAY_MODE = '0'`）：
   - 逐笔发送划款请求到主机
   - 实时返回划款结果
   - 更新 `tbdxfundautotransfer.square_status` 状态

2. **批量文件模式**（`PAY_MODE = '1'`）：
   - 生成划款文件（`dxfundpaydetail.序列号`）
   - 上传文件到主机
   - 轮询主机处理状态
   - 导入结果文件更新状态

#### 9.3.3 划款数据查询

**查询条件**：
```sql
SELECT * FROM tbdxfundautotransfer a 
WHERE a.clear_date = ? 
    AND a.square_status IN ('0', '3') -- 0-未处理，3-未审批
    AND a.ta_code = ? -- 可选
    AND a.prd_code = ? -- 可选
    AND a.amt_usage = ? -- 可选：1-认购款，9-申购款，2-手续费
    ...
```

**关键字段**：
- `clear_date`：清算日期（从 `tbdxfundpayment` 继承）
- `bank_acc`：划出账户（认申购归集户或赎回归集户）
- `targ_bank_acc`：划入账户（TA账户）
- `amt`：划款金额
- `amt_usage`：资金用途
- `square_status`：划款状态（'0'-未处理，'3'-未审批，'Z'-处理中，'6'-划款中，'9'-成功，'F'-失败）

#### 9.3.4 划款执行流程

1. **数据查询**：从 `tbdxfundautotransfer` 表查询待划款记录
2. **状态更新**：更新状态为 'Z'（处理中）
3. **划款请求**：
   - 联机模式：逐笔调用主机接口（400011）
   - 文件模式：生成文件并上传
4. **结果处理**：
   - 成功：更新 `square_status = '6'`（划款中）或 '9'（成功）
   - 失败：更新 `square_status = 'F'`（失败）并记录错误信息
   - 超时：更新 `square_status = '7'`（超时）

### 9.4 内部账户余额校验（T219002）

#### 9.4.1 功能说明

**交易代码**：T219002  
**实现类**：`T219002HSAdapter`  
**核心方法**：`queryAndSend()`

**功能**：校验内部账户（赎回归集户）余额是否充足，确保能够完成划款。

#### 9.4.2 校验逻辑

**查询逻辑**：
```sql
SELECT 
    SUM(a.tot_amt) Amt, 
    b.crebit_account CrebitAccount 
FROM tbdxfundpayment a, tbdxfundprdbankacc b 
WHERE a.prd_code = b.prd_code 
    AND a.clear_date = ?
GROUP BY b.crebit_account
```

**校验流程**：
1. 按 `crebit_account`（赎回归集户账号）汇总 `tot_amt`（总金额）
2. 调用主机接口（400009）查询账户余额
3. 比较应划款金额与账户余额
4. 如果余额不足，抛出异常并中断流程

**说明**：
- 校验的是 `clear_date` 对应的划款金额
- 按产品归集户账号分组汇总
- 如果余额不足，会阻止后续划款操作

### 9.5 划款流程总结

#### 9.5.1 完整流程

```
1. 日终清算（T210110）
   └─> 生成 tbdxfundpayment 表数据
        ├─> 汇总赎回、分红、退款金额（tot_amt）
        ├─> 汇总认申购确认金额（cfm_amt）
        └─> 计算手续费（charge）

2. 自动划拨汇总（T210025）
   └─> 从 tbdxfundpayment 生成 tbdxfundautotransfer
        ├─> 认申购款汇总（amt_usage = '1'/'9'）
        └─> 手续费汇总（amt_usage = '2'）

3. 内部账户余额校验（T219002）
   └─> 校验赎回归集户余额是否充足
        └─> 查询 tbdxfundpayment.tot_amt

4. 资金划拨执行（T210026）
   └─> 从 tbdxfundautotransfer 读取数据
        ├─> 联机模式：逐笔发送划款请求
        └─> 文件模式：生成文件并上传
```

#### 9.5.2 关键表关系

```
tbdxfundpayment（资金划付表）
    │
    ├─> clear_date：清算日期（用于查询需要划款的记录）
    ├─> cfm_amt：确认金额（用于认申购款划拨）
    ├─> charge：手续费（用于手续费划拨）
    ├─> tot_amt：总金额（用于兑付资金划拨）
    └─> prd_code, ta_code, curr_type：用于关联产品银行账户表
         │
         └─> tbdxfundprdbankacc（产品银行账户表）
              ├─> debit_account：认申购归集户账号
              ├─> crebit_account：赎回归集户账号
              ├─> bank_acc_ver：募集期TA账户
              ├─> bank_acc_up：开放期TA账户
              └─> charge_account：手续费专户账号
                   │
                   └─> tbdxfundautotransfer（自动划拨表）
                        ├─> bank_acc：划出账户
                        ├─> targ_bank_acc：划入账户
                        ├─> amt：划款金额
                        └─> square_status：划款状态
```

#### 9.5.3 关键参数

| 参数代码 | 参数名称 | 说明 | 默认值 |
|---------|---------|------|--------|
| PAY_MODE | 划款模式 | 0-联机报文模式，1-批量文件模式 | 0 |
| AUTOTRANSFERMODE | 划拨方式 | 是否两步划拨 | N |
| PAY_CLT_TYPE | 客户类型划拨 | 是否区分个人/机构划拨 | N |
| AUTO_TRANSFER_ISCAL_FEEMAT | 是否汇总手续费 | 是否汇总手续费款 | Y |
| FUND_AUTOTRANSFER_USAGE_FLAG | 资金用途标识 | 是否区分认购款/申购款 | 0 |

### 9.6 注意事项

1. **划款日期**：使用 `clear_date` 作为划款日期，不是 `trans_date`
2. **金额计算**：认申购款需要根据 `square_way` 判断是否扣除手续费
3. **账户区分**：认购用募集期账户（`bank_acc_ver`），申购用开放期账户（`bank_acc_up`）
4. **状态管理**：划款状态从 '0'（未处理）→ 'Z'（处理中）→ '6'/'9'/'F'（最终状态）
5. **余额校验**：划款前必须校验内部账户余额，避免划款失败
6. **分步划拨**：支持两步划拨模式，第一步划到中间账户，第二步划到TA账户

## 十、tbdxfundsquare 表 clear_date 和 square_date 更新逻辑

### 9.1 字段说明

- **clear_date（清算日期）**：资金清算日期，表示资金实际划付的日期
- **square_date（入账日期）**：资金入账日期，表示资金实际入账的日期

### 9.2 clear_date 更新逻辑

#### 9.2.1 初始设置（transCfmBakToSquare 方法）

**位置**：`Pub200104Service.transCfmBakToSquare()`

**计算逻辑**：

1. **初始值**：`clear_date = 0`

2. **根据业务类型和产品配置计算**（优先级从高到低）：

   **a. 赎回业务（124/142/125）**：
   ```java
   if (业务代码 in ('124', '142', '125') 
       && product.getRedDays() >= 当前业务日期) {
       clear_date = product.getRedDays();  // 产品赎回资金到账日
   }
   ```

   **b. 快速赎回业务（150/151）**：
   ```java
   if (业务代码 in ('150', '151') 
       && product.getRedDays() >= 当前业务日期) {
       clear_date = product.getRedDays();
   }
   ```

   **c. 募集失败（149）**：
   ```java
   if (业务代码 = '149' 
       && product.getFailDays() >= 当前业务日期) {
       clear_date = product.getFailDays();  // 产品失败资金到账日
   }
   ```

   **d. 比例退款（130）**：
   ```java
   if (业务代码 = '130' 
       && 清算方向 = 'ZWFX_ADD'（上账）
       && product.getFailDays() >= 当前业务日期) {
       clear_date = product.getFailDays();
   }
   ```

   **e. 申购业务（122）**：
   ```java
   if (业务代码 = '122' 
       && 清算方向 = 'ZWFX_ADD'（上账）
       && product.getOpenBuyDays() >= 当前业务日期) {
       clear_date = product.getOpenBuyDays();  // 产品申购资金到账日
   }
   ```

3. **如果 clear_date 仍为 0，则根据产品控制标志计算**：

   **a. 系统工作日模式**（产品控制标志第28位 = '1'）：
   ```java
   clear_date = PubApiFactory.getNNextSysDate(
       cfm_date,  // 确认日期
       days       // 延后天数
   );
   ```

   **b. 产品工作日模式**（产品控制标志第28位 = '0'，默认）：
   ```java
   // 支持币种节假日（参数：redeem_with_curr_holiday）
   if (产品参数 redeem_with_curr_holiday = '1' 
       && 业务代码在配置范围内) {
       clear_date = PubDxFundApiFactory.getNWorkDateWithCurrencyHoliday(
           prd_code, cfm_date, days
       );
   } else {
       clear_date = PubDxFundApiFactory.getNWorkDate(
           prd_code, cfm_date, days
       );
   }
   ```

4. **特殊参数处理**：
   - **ADVSQUARCDT = '1'**（入账日提前一天，上海银行）：
     ```java
     days = (days > 0) ? (days - 1) : days;
     ```

5. **验证**：
   - 如果 clear_date < 0，抛出异常："取清算日期失败"
   - 如果指定了产品业务资金到账日，验证是否为系统工作日

#### 9.2.2 失败返款场景（transReqBakToSquare 方法）

**位置**：`Pub200104Service.transReqBakToSquare()`

**设置逻辑**：
```java
clear_date = transVar.getSysArg().getInitDate();  // 当前业务日期
```

**说明**：失败返款不需要受勾对模式影响，直接使用当前业务日期

#### 9.2.3 数据同步（SquareDealTask）

**位置**：`SquareDealTask.action()`

**说明**：
- 从备份表（tbtranssquarebak）同步到正式表（tbdxfundsquare）时，`clear_date` 保持不变
- 通过 `INSERT INTO tbdxfundsquare SELECT * FROM tbtranssquarebak` 方式同步

### 9.3 square_date 更新逻辑

#### 9.3.1 初始设置（transCfmBakToSquare 方法）

**位置**：`Pub200104Service.transCfmBakToSquare()`

**计算逻辑**：

1. **反向勾对模式**（squareMode = '1'，默认入账）：
   ```java
   square_date = clear_date;
   check_status = 'K_GDZT_NO';  // 未勾对
   ```

2. **正向勾对模式**（squareMode = '0'，默认不入账）：
   ```java
   square_date = 0;  // 默认不设置
   check_status = 'K_GDZT_NO';
   
   // 特殊处理：上海银行参数 ADVSQUARCDT = '1' 且 days = 0
   if (ADVSQUARCDT = '1' && days == 0) {
       square_date = clear_date;
   }
   ```

3. **失败返款场景**（transReqBakToSquare 方法）：
   ```java
   square_date = transVar.getSysArg().getInitDate();  // 当前业务日期
   ```

#### 9.3.2 入账日期调整（updateSquareDate 方法）

**位置**：`T210013HSAdapter.updateSquareDate()`

**触发时机**：生成主机批量入账文件时（T210013）

**更新逻辑**：

1. **正向勾对模式**（squareMode = '0'）：

   **a. 不分业务（默认）**：
   ```sql
   UPDATE tbdxfundsquare 
   SET check_status = 'K_GDZT_YES', 
       square_date = CASE 
           WHEN receiptInfo.getSquareDate() > 0 
           THEN receiptInfo.getSquareDate()  -- 使用资金录入表的入账日期
           ELSE clear_date                    -- 否则使用清算日期
       END
   WHERE check_status != 'K_GDZT_NONEED' 
     AND clear_date = ? 
     AND trans_date = ? 
     AND prd_code = ?
   ```

   **b. 分业务**：
   ```sql
   -- 根据业务类型（赎回/分红/退款）分别更新
   UPDATE tbdxfundsquare 
   SET check_status = 'K_GDZT_YES', 
       square_date = receiptInfo.getSquareDate()
   WHERE check_status != 'K_GDZT_NONEED' 
     AND clear_date = ? 
     AND prd_code = ? 
     AND trans_date = ?
     AND busin_code IN ('124', '142', '125', '149', '150', '151')  -- 赎回资金
   ```

2. **反向勾对模式**（squareMode = '1'）：

   **a. 更新入账日期**（如果与当前业务日期不同）：
   ```sql
   UPDATE tbdxfundsquare 
   SET square_date = receiptInfo.getSquareDate(),
       modify_timestamp = ?
   WHERE prd_code = ? 
     AND clear_date = ? 
     AND trans_date = ?
   ```

   **b. 更新勾对状态**（如果状态为待入账）：
   ```sql
   UPDATE tbdxfundsquare 
   SET square_date = receiptInfo.getSquareDate(),
       check_status = 'K_GDZT_YES',
       modify_timestamp = ?
   WHERE prd_code = ? 
     AND clear_date = ? 
     AND trans_date = ?
     AND check_status != 'K_GDZT_YES'
   ```

#### 9.3.3 数据来源

**资金录入表（tbdxfundreceipt）**：
- `receiptInfo.getSquareDate()`：资金录入表中配置的入账日期
- `receiptInfo.getClearDate()`：资金录入表中配置的清算日期
- `receiptInfo.getDealStatus()`：处理状态（'0'-待支付，'1'-已处理）

**查询方式**：
```java
// 正向勾对：查询待支付状态的记录
list = PubDxFundApiFactory.qryReceiptInfoByStatus("RZBZ_DOING");

// 反向勾对：查询所有状态的记录
list = PubDxFundApiFactory.qryReceiptInfoByStatus("");
```

### 9.4 关键参数说明

| 参数名 | 说明 | 默认值 | 影响字段 |
|--------|------|--------|---------|
| SQUAREMODE | 勾对模式：0-正向勾对（默认不入账），1-反向勾对（默认入账） | 0 | square_date 初始值 |
| ADVSQUARCDT | 入账日提前一天（上海银行） | 0 | clear_date 计算时的 days 调整 |
| redeem_with_curr_holiday | 清算顺延币种节假日（星展银行） | N | clear_date 计算方式 |
| REDEEM_WITH_CURR_HOLIDAY_BUSIN_CODES | 支持币种节假日的业务代码 | 124,142,143,150,151,120,130,149 | clear_date 计算方式 |

### 9.5 产品配置字段说明

| 产品字段 | 说明 | 使用场景 |
|---------|------|---------|
| red_days | 赎回资金到账日 | 赎回业务（124/142/125/150/151） |
| fail_days | 失败资金到账日 | 募集失败（149）、比例退款（130） |
| open_buy_days | 申购资金到账日 | 申购业务（122） |
| control_flag[28] | 清算入账日期计算方式：0-产品工作日，1-系统工作日 | clear_date 计算方式 |

### 9.6 业务规则总结

1. **clear_date 计算优先级**：
   - 产品配置的特定业务到账日（red_days/fail_days/open_buy_days）
   - 根据确认日期和延后天数计算（产品工作日或系统工作日）

2. **square_date 初始值**：
   - 反向勾对（默认入账）：`square_date = clear_date`
   - 正向勾对（默认不入账）：`square_date = 0`（后续通过资金勾对更新）

3. **square_date 更新时机**：
   - 生成入账文件时（T210013）：根据资金录入表（tbdxfundreceipt）更新
   - 支持分业务入账：根据业务类型分别更新

4. **日期关系**：
   - 默认情况下：`square_date = clear_date`（反向勾对模式）
   - 正向勾对模式：`square_date` 通过资金勾对确定，可能不等于 `clear_date`

### 9.7 注意事项

1. **clear_date 不能为负数**：如果计算失败（返回负数），会抛出异常
2. **产品工作日验证**：如果指定了产品业务资金到账日，需要验证是否为系统工作日
3. **币种节假日支持**：星展银行等支持清算顺延币种节假日
4. **入账日期调整**：通过资金录入表（tbdxfundreceipt）可以调整入账日期，不影响清算日期
5. **数据同步**：从备份表同步到正式表时，日期字段保持不变

## 十、主要代码文件

1. **核心更新逻辑**：
   - `TranssumPaymentFunc.java` - 日终清算资金划付汇总
   - `Pub200104Service.java` - 清算确认数据公共方法（clear_date 和 square_date 初始计算）

2. **状态更新逻辑**：
   - `T210013HSAdapter.java` - 资金勾对状态更新（square_date 更新）
   - `SquareDealTask.java` - 数据同步任务

3. **实体类**：
   - `DxFundPayMent.java` - 实体类定义
   - `DxFundPayMentDao.java` - DAO操作类

4. **相关常量**：
   - `IDxFundParamConstant.java` - 参数常量定义

