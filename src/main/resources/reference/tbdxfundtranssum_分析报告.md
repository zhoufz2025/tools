# tbdxfundtranssum 表使用场景及重要字段分析

## 一、表的基本信息

### 1.1 表名
`tbdxfundtranssum` - 代销基金交易汇总表

### 1.2 表的作用
用于汇总当日确认的交易数据，按产品、TA、业务代码、币种等维度进行统计，为资金划付表（tbdxfundpayment）提供数据支持：
1. **更新已有记录**：更新资金划付表的确认金额（cfm_amt）、认申购手续费（charge）、赎回手续费（red_charge）
2. **插入新记录**：对于当日确认的认申购产品，如果下一日没有返款数据，从交易汇总表插入新的资金划付记录

## 二、重要字段说明

| 字段名 | 中文名称 | 数据类型 | 说明 | 是否主键 |
|--------|---------|---------|------|---------|
| area_id | 片区号 | Integer | 数据分片标识，用于分库分表 | 是 |
| table_num | 分表号 | String | 分表编号，'0'表示汇总后的数据，'1'-'16'表示分表数据 | 是 |
| sum_date | 汇总日期 | Integer | 交易确认日期，格式：YYYYMMDD | 是 |
| prd_code | 产品代码 | String | 基金产品代码，'000000'表示TA级别汇总 | 是 |
| ta_code | TA代码 | String | 登记机构代码 | 是 |
| busin_code | 业务代码 | String | 业务类型代码，如：120-认购、122-申购、139-定投、887-其他认申购、124-赎回等 | 是 |
| curr_type | 币种 | String | 货币类型，如：156-人民币、840-美元等 | 是 |
| bank_no | 银行编号 | String | 银行代码 | 否 |
| tot_num | 总笔数 | Integer | 总交易笔数 | 否 |
| succ_num | 成功笔数 | Integer | 成功确认的交易笔数 | 否 |
| ta_num | TA笔数 | Integer | 来自TA的交易笔数 | 否 |
| cfm_amt | 确认金额 | Decimal(18,2) | 确认的交易金额，认申购业务使用 | 否 |
| cfm_vol | 确认份额 | Decimal(18,2) | 确认的交易份额 | 否 |
| charge | 手续费 | Decimal(18,2) | 认申购手续费 | 否 |
| back_fee | 后端收费 | Decimal(18,2) | 后端收费金额 | 否 |
| agency_fee | 代理费 | Decimal(18,2) | 代理费金额 | 否 |

### 2.1 关键字段详解

#### 2.1.1 prd_code（产品代码）
- **正常值**：具体的产品代码，如 'PRD001'
- **特殊值**：'000000' 表示TA级别的汇总数据，不区分具体产品
- **用途**：用于区分产品维度和TA维度的汇总数据

#### 2.1.2 busin_code（业务代码）
- **认申购业务**：'120'（认购）、'122'（申购）、'139'（定投）、'887'（其他认申购）
- **赎回业务**：'124'（赎回）、'163'、'142'、'125'等
- **其他业务**：'143'（分红）、'743'等

#### 2.1.3 table_num（分表号）
- **分表数据**：'1' 到 '16'，表示从分表汇总的数据
- **汇总数据**：'0'，表示所有分表汇总后的最终数据
- **处理流程**：先插入分表数据（table_num='1'-'16'），然后汇总为 table_num='0' 的数据，最后删除分表数据

#### 2.1.4 cfm_amt（确认金额）
- **数据来源**：从 `transVar.getTbTransCfmBak() + tableNum`（默认：`tbdxfundtranscfm00{tableNum}`）的 `cfm_amt` 字段汇总
- **特殊处理**：支持过滤智能投顾业务（advisor_flag = '1'）
- **用途**：用于更新 `tbdxfundpayment` 表的 `cfm_amt` 字段

#### 2.1.5 charge（手续费）
- **数据来源**：从 `transVar.getTbTransCfmBak() + tableNum`（默认：`tbdxfundtranscfm00{tableNum}`）的 `charge` 字段汇总
- **用途**：用于更新 `tbdxfundpayment` 表的 `charge` 字段
- **计算方式**：根据产品手续费分配比例计算

## 三、主要使用场景

### 3.1 日终清算场景（T210110 - TranssumPaymentFunc）

**核心功能**：在日终清算时，汇总生成交易汇总数据，用于后续的资金划付处理

**主要流程**：

1. **汇总产品数据**（按产品维度）：
   ```sql
   INSERT INTO tbdxfundtranssum 
   (table_num, area_id, sum_date, prd_code, ta_code, busin_code, curr_type, 
    tot_num, succ_num, ta_num, cfm_amt, cfm_vol, charge, back_fee, agency_fee)
   SELECT tableNum, areaId, sumDate, prd_code, ta_code, busin_code, curr_type,
          COUNT(1) tot_num,
          SUM(CASE WHEN status IN ('部分在途','部分完成','确认成功','提交确认') THEN 1 ELSE 0 END) succ_num,
          SUM(CASE WHEN from_flag = 'TA' THEN 1 ELSE 0 END) ta_num,
          SUM(cfm_amt) cfm_amt,
          SUM(cfm_vol) cfm_vol,
          SUM(charge) charge,
          SUM(back_fee) back_fee,
          SUM(agency_fee) agency_fee
   FROM {transVar.getTbTransCfmBak()}{tableNum}
   -- 表名示例（默认模式）：tbdxfundtranscfm001, tbdxfundtranscfm002, ..., tbdxfundtranscfm0016
   -- 表名示例（分TA模式）：tbdxfundtranscfm{TA代码}1, tbdxfundtranscfm{TA代码}2, ..., tbdxfundtranscfm{TA代码}16
   -- 表名示例（7*24模式）：tbdxfundtranscfm1, tbdxfundtranscfm2, ..., tbdxfundtranscfm16
   WHERE cfm_date = ?
     AND ta_code = ?
     AND status != '确认失败'
     AND busin_code != ' '
     AND busin_code IS NOT NULL
   GROUP BY prd_code, ta_code, busin_code, curr_type
   ```

2. **汇总TA数据**（按TA维度，prd_code='000000'）：
   ```sql
   INSERT INTO tbdxfundtranssum 
   (table_num, area_id, sum_date, prd_code, ta_code, busin_code, curr_type, 
    tot_num, succ_num, ta_num, cfm_amt, cfm_vol, charge, back_fee, agency_fee)
   SELECT tableNum, areaId, sumDate, '000000' prd_code, ta_code, busin_code, curr_type,
          COUNT(1) tot_num,
          SUM(CASE WHEN status = '确认成功' THEN 1 ELSE 0 END) succ_num,
          SUM(CASE WHEN from_flag = 'TA' THEN 1 ELSE 0 END) ta_num,
          0 cfm_amt, 0 cfm_vol, 0 charge, 0 back_fee, 0 agency_fee
   FROM {transVar.getTbTransCfmBak()}{tableNum}
   -- 表名示例（默认模式）：tbdxfundtranscfm001, tbdxfundtranscfm002, ..., tbdxfundtranscfm0016
   -- 表名示例（分TA模式）：tbdxfundtranscfm{TA代码}1, tbdxfundtranscfm{TA代码}2, ..., tbdxfundtranscfm{TA代码}16
   -- 表名示例（7*24模式）：tbdxfundtranscfm1, tbdxfundtranscfm2, ..., tbdxfundtranscfm16
   WHERE cfm_date = ?
     AND ta_code = ?
     AND status != '确认失败'
     AND busin_code != ' '
     AND busin_code IS NOT NULL
   GROUP BY ta_code, busin_code, curr_type
   ```

3. **汇总分表数据**（合并所有分表，生成 table_num='0' 的数据）：
   ```sql
   INSERT INTO tbdxfundtranssum 
   (sum_date, table_num, area_id, bank_no, prd_code, ta_code, busin_code, curr_type, 
    tot_num, succ_num, ta_num, cfm_amt, cfm_vol, charge, back_fee, agency_fee)
   SELECT sum_date, '0', area_id, bank_no, prd_code, ta_code, busin_code, curr_type,
          SUM(tot_num) tot_num,
          SUM(succ_num) succ_num,
          SUM(ta_num) ta_num,
          SUM(cfm_amt) cfm_amt,
          SUM(cfm_vol) cfm_vol,
          SUM(charge) charge,
          SUM(back_fee) back_fee,
          SUM(agency_fee) agency_fee
   FROM tbdxfundtranssum
   WHERE sum_date = ?
     AND ta_code = ?
     AND table_num != '0'
     AND busin_code != ' '
     AND busin_code IS NOT NULL
   GROUP BY bank_no, prd_code, ta_code, busin_code, curr_type
   ```

4. **删除分表数据**（仅保留汇总数据）：
   ```sql
   DELETE FROM tbdxfundtranssum 
   WHERE sum_date = ? 
     AND ta_code = ? 
     AND table_num != '0'
   ```

### 3.2 更新资金划付表场景

**核心功能**：使用交易汇总数据更新资金划付表的确认金额和手续费

**更新确认金额**：
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
  AND (prd_code, curr_type) IN (
    SELECT DISTINCT prd_code, curr_type 
    FROM tbdxfundtranssum 
    WHERE sum_date = ? 
      AND busin_code IN ('120', '122', '139', '887')
  )
```

**更新认申购手续费**：
```sql
UPDATE tbdxfundpayment 
SET charge = (
    SELECT SUM(charge * 手续费分配比例) 
    FROM tbdxfundtranssum 
    WHERE sum_date = ? 
      AND ta_code = ? 
      AND prd_code = ?
      AND busin_code IN ('120', '122', '139', '887')
      AND prd_code = tbdxfundpayment.prd_code 
      AND ta_code = tbdxfundpayment.ta_code 
      AND curr_type = tbdxfundpayment.curr_type
)
WHERE ta_code = ? 
  AND trans_date = ? 
  AND clear_date = ?
```

**更新赎回手续费**：
```sql
UPDATE tbdxfundpayment 
SET red_charge = (
    SELECT SUM(charge * 手续费分配比例) 
    FROM tbdxfundtranssum 
    WHERE sum_date = ? 
      AND ta_code = ? 
      AND busin_code IN ('124', '163', '125', '142')
      AND prd_code = tbdxfundpayment.prd_code 
      AND ta_code = tbdxfundpayment.ta_code 
      AND curr_type = tbdxfundpayment.curr_type
)
WHERE ta_code = ? 
  AND trans_date = ? 
  AND clear_date = ?
  AND (prd_code, curr_type) IN (
    SELECT DISTINCT prd_code, curr_type 
    FROM tbdxfundtranssum 
    WHERE sum_date = ? 
      AND busin_code IN ('124', '163', '125', '142')
  )
```

**更新手续费（中登TA，直接使用代理费）**：
```sql
-- 认申购手续费
UPDATE tbdxfundpayment 
SET charge = (
    SELECT SUM(agency_fee) 
    FROM tbdxfundtranssum 
    WHERE sum_date = ? 
      AND ta_code = ? 
      AND busin_code IN ('120', '122', '139', '887')
      AND prd_code = tbdxfundpayment.prd_code 
      AND curr_type = tbdxfundpayment.curr_type
)
WHERE ta_code = ? 
  AND trans_date = ? 
  AND clear_date = ?

-- 赎回手续费
UPDATE tbdxfundpayment 
SET red_charge = (
    SELECT SUM(agency_fee) 
    FROM tbdxfundtranssum 
    WHERE sum_date = ? 
      AND ta_code = ? 
      AND busin_code IN ('124', '163', '125', '142')
      AND prd_code = tbdxfundpayment.prd_code 
      AND curr_type = tbdxfundpayment.curr_type
)
WHERE ta_code = ? 
  AND trans_date = ? 
  AND clear_date = ?
```

### 3.3 插入资金划付表场景

**核心功能**：当日确认的认申购产品，如果下一日没有返款数据，需要从交易汇总表插入到资金划付表

**业务说明**：
- 对于当日确认的认申购业务（120-认购、122-申购、139-定投、887-其他认申购）
- 如果该产品在下一日没有返款数据（即 `tbdxfundpayment` 表中不存在对应记录）
- 需要从 `tbdxfundtranssum` 表插入新的资金划付记录

**插入SQL**：
```sql
INSERT INTO tbdxfundpayment 
(clear_date, square_date, prd_code, ta_code, curr_type, trans_date, deal_status, cfm_amt, charge, area_id) 
SELECT 
    nextTaWorkDate,  -- 清算日期
    nextTaWorkDate,  -- 入账日期
    a.prd_code,      -- 产品代码
    a.ta_code,       -- TA代码
    curr_type,       -- 币种
    initDate,        -- 交易日期
    '0',             -- 处理状态（待支付）
    SUM(a.cfm_amt) cfm_amt,  -- 确认金额汇总
    SUM(charge * (手续费分配比例)) charge,  -- 手续费汇总（按业务代码分配）
    area_id          -- 片区号
FROM tbdxfundtranssum a
WHERE a.sum_date = ? 
  AND a.ta_code = ? 
  AND a.busin_code IN ('120', '122', '139', '887')
  AND a.prd_code = ?
  AND NOT EXISTS (
      SELECT prd_code 
      FROM tbdxfundpayment 
      WHERE clear_date = ? 
        AND prd_code = a.prd_code 
        AND curr_type = a.curr_type 
        AND ta_code = a.ta_code 
        AND trans_date = ?
  )
GROUP BY a.prd_code, a.ta_code, curr_type
```

**关键点**：
1. **插入条件**：使用 `NOT EXISTS` 判断，确保只插入不存在的记录
2. **金额计算**：
   - `cfm_amt`：直接汇总 `tbdxfundtranssum` 的 `cfm_amt` 字段
   - `charge`：根据业务代码和手续费分配比例计算
3. **分组维度**：按 `prd_code`、`ta_code`、`curr_type` 分组
4. **业务代码**：只处理认申购业务（'120'、'122'、'139'、'887'）

**使用场景**：
- 当日有认申购确认，但下一日没有返款、分红等其他业务
- 需要为这些产品创建资金划付记录，以便后续划款处理

### 3.4 数据同步场景

**同步表**：`tbdxfundtranssumsync`
- 用于跨片区数据同步
- 同步条件：`ta_code`、`sum_date`、`area_id`
- 同步时机：日终清算完成后

## 四、数据来源

### 4.1 主要数据来源

**来源表**：`transVar.getTbTransCfmBak() + tableNum`（交易确认备份表及其分表）

**表名规则**：
- **默认表名**：`tbdxfundtranscfm00`（清算临时表公用模式）
- **分TA表名**：`tbdxfundtranscfm{TA代码}`（清算表临时表分TA创建模式）
- **7*24模式表名**：`tbdxfundtranscfm`（7*24或非日终清算模式）
- **分表命名**：在基础表名后直接追加分表号（1-16）
  - 默认模式：`tbdxfundtranscfm001`、`tbdxfundtranscfm002`、...、`tbdxfundtranscfm0016`
  - 分TA模式：`tbdxfundtranscfm{TA代码}1`、`tbdxfundtranscfm{TA代码}2`、...、`tbdxfundtranscfm{TA代码}16`
  - 7*24模式：`tbdxfundtranscfm1`、`tbdxfundtranscfm2`、...、`tbdxfundtranscfm16`

**表名确定逻辑**（参考 `Pub200007Service.java`）：
1. **7*24或非日终清算**：使用 `tbdxfundtranscfm`
2. **清算表临时表分TA创建**：使用 `tbdxfundtranscfm{TA代码}`
3. **清算临时表公用**（默认）：使用 `tbdxfundtranscfm00`

**数据范围**：当日确认的交易数据（`cfm_date = 当日`）

### 4.2 数据筛选条件

1. **基本条件**：
   - `cfm_date = 当日`
   - `ta_code = 当前TA`
   - `status != '确认失败'`
   - `busin_code != ' '` 且 `busin_code IS NOT NULL`

2. **特殊条件**：
   - **智能投顾过滤**：根据参数 `TRANSSUM_AND_PAYMENT_CFM_AMT_NOT_CONTAIN_AR` 决定是否过滤 `advisor_flag = '1'` 的业务
   - **余额宝产品**：根据参数 `AUTOTRANSFER_T1_PRD` 配置特殊划拨产品，排除特定业务代码
   - **客户类型**：根据参数 `PAY_CLT_TYPE` 决定是否区分机构/个人客户

### 4.3 数据汇总维度

1. **产品维度汇总**：
   - 分组字段：`prd_code`、`ta_code`、`busin_code`、`curr_type`
   - 统计字段：`tot_num`、`succ_num`、`ta_num`、`cfm_amt`、`cfm_vol`、`charge`、`back_fee`、`agency_fee`

2. **TA维度汇总**：
   - 分组字段：`ta_code`、`busin_code`、`curr_type`
   - `prd_code = '000000'`（表示TA级别汇总）
   - 统计字段：`tot_num`、`succ_num`、`ta_num`（金额字段为0）

3. **分表汇总**：
   - 分组字段：`bank_no`、`prd_code`、`ta_code`、`busin_code`、`curr_type`
   - `table_num = '0'`（表示汇总后的数据）

## 五、关键业务规则

### 5.1 数据生成规则

1. **分表处理**：
   - 先按分表（table_num='1'-'16'）汇总数据
   - 然后合并所有分表数据，生成 table_num='0' 的汇总数据
   - 最后删除分表数据，仅保留汇总数据

2. **数据分组维度**：
   - 产品维度：`prd_code`、`ta_code`、`busin_code`、`curr_type`
   - TA维度：`ta_code`、`busin_code`、`curr_type`（prd_code='000000'）

3. **数据删除规则**：
   - 重复操作时先删除当日数据（按 `sum_date`、`ta_code`）
   - 汇总完成后删除分表数据（table_num != '0'）

### 5.2 金额计算规则

1. **确认金额（cfm_amt）**：
   - 来源：`transVar.getTbTransCfmBak() + tableNum`（默认：`tbdxfundtranscfm00{tableNum}`）的 `cfm_amt` 字段
   - 汇总：按产品、业务代码、币种分组求和
   - 特殊处理：支持过滤智能投顾业务

2. **手续费（charge）**：
   - 来源：`transVar.getTbTransCfmBak() + tableNum`（默认：`tbdxfundtranscfm00{tableNum}`）的 `charge` 字段
   - 汇总：按产品、业务代码、币种分组求和
   - 分配：根据产品手续费分配比例计算销售商手续费

3. **代理费（agency_fee）**：
   - 来源：`transVar.getTbTransCfmBak() + tableNum`（默认：`tbdxfundtranscfm00{tableNum}`）的 `agency_fee` 字段
   - 用途：中登TA（98/99）直接使用代理费字段

### 5.3 状态统计规则

1. **成功笔数（succ_num）**：
   - 产品维度：`status IN ('部分在途','部分完成','确认成功','提交确认')`
   - TA维度：`status = '确认成功'`

2. **TA笔数（ta_num）**：
   - 条件：`from_flag = 'TA'`
   - 用途：统计来自TA的交易笔数

## 六、与其他表的关系

### 6.1 数据流向

```
transVar.getTbTransCfmBak() + tableNum (交易确认备份表)
    ↓ [汇总]
    ├─ 默认：tbdxfundtranscfm00{tableNum} (如：tbdxfundtranscfm001)
    ├─ 分TA：tbdxfundtranscfm{TA代码}{tableNum}
    └─ 7*24：tbdxfundtranscfm{tableNum}
    ↓ [汇总]
tbdxfundtranssum (交易汇总表)
    ↓ [更新/插入]
    ├─ 更新确认金额（cfm_amt）
    ├─ 更新认申购手续费（charge）
    ├─ 更新赎回手续费（red_charge）
    └─ 插入新记录（当日确认但下一日无返款的产品）
tbdxfundpayment (资金划付表)
    ↓ [同步]
tbdxfundtranssumsync (交易汇总同步表)
```

### 6.2 关联关系

1. **与 tbdxfundpayment 的关系**：
   - 通过 `prd_code`、`ta_code`、`curr_type` 关联
   - **更新操作**：
     - 更新 `cfm_amt`（确认金额）：业务代码 '120'、'122'、'139'、'887'（认申购业务）
     - 更新 `charge`（认申购手续费）：业务代码 '120'、'122'、'139'、'887'
     - 更新 `red_charge`（赎回手续费）：业务代码 '124'、'163'、'125'、'142'（赎回业务）
   - **插入操作**：
     - 当日确认的认申购产品，如果下一日没有返款数据，从 `tbdxfundtranssum` 插入新记录
     - 插入字段：`clear_date`、`square_date`、`prd_code`、`ta_code`、`curr_type`、`trans_date`、`deal_status`、`cfm_amt`、`charge`、`area_id`

2. **与交易确认备份表的关系**：
   - 数据来源表：`transVar.getTbTransCfmBak() + tableNum`
   - 默认表名示例：`tbdxfundtranscfm00{tableNum}`（如：tbdxfundtranscfm001）
   - 分TA表名示例：`tbdxfundtranscfm{TA代码}{tableNum}`（如：tbdxfundtranscfm0011）
   - 7*24表名示例：`tbdxfundtranscfm{tableNum}`（如：tbdxfundtranscfm1）
   - 通过 `cfm_date`、`ta_code`、`prd_code`、`busin_code`、`curr_type` 关联

## 七、注意事项

### 7.1 数据一致性

1. **分表数据一致性**：
   - 确保所有分表数据汇总后再生成 table_num='0' 的数据
   - 汇总完成后必须删除分表数据，避免数据重复

2. **事务处理**：
   - 汇总操作需要事务控制，确保数据一致性
   - 删除操作需要在汇总完成后执行

### 7.2 性能优化

1. **分表处理**：
   - 使用分表可以避免单表数据量过大
   - 汇总时按分表并行处理，提高性能

2. **索引优化**：
   - 建议在 `sum_date`、`ta_code`、`prd_code`、`busin_code`、`curr_type` 上建立索引
   - 查询时尽量使用主键字段

### 7.3 特殊业务处理

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

## 八、常见问题

### 8.1 数据重复问题

**问题**：汇总数据出现重复

**原因**：
- 分表数据未删除
- 重复执行汇总操作

**解决**：
- 确保汇总完成后删除分表数据
- 汇总前先删除当日数据

### 8.2 金额不一致问题

**问题**：汇总金额与明细金额不一致

**原因**：
- 过滤条件不一致
- 分组维度不匹配

**解决**：
- 检查过滤条件是否正确
- 确认分组维度是否一致

### 8.3 性能问题

**问题**：汇总操作执行缓慢

**原因**：
- 数据量过大
- 索引缺失

**解决**：
- 使用分表处理
- 建立合适的索引
- 优化查询条件

