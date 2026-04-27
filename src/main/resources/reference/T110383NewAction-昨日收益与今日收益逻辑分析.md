# T110383NewAction 昨日收益与今日收益取值与计算逻辑

## 1. 目标文件

- 文件路径：`app/lcpt-server/sale/lcpt-dxfund/lcpt-dxfund-trans/lcpt-dxfund-online-query-bootstrap-adapter/src/main/java/com/hundsun/lcpt/dxfund/online/adapter/T110383/T110383NewAction.java`
- 主要方法：`getProfitData(...)`
- 汇总方法：`setSummaryData(...)`

## 2. 相关输出字段

单产品维度（`getProfitData`）：

- `YstdyIncome`：昨日收益（按物理日期口径）
- `YstdyDate`：昨日收益对应日期
- `YstdyCompleteFlag`：昨日收益是否完成（`1/0`）
- `TodayIncome`：今日收益（按物理日期口径）
- `TodayDate`：今日收益对应日期
- `TodayCompleteFlag`：今日收益是否完成（`1/0`）
- `YstdyProfitLoss`：兼容字段，直接等于 `YstdyIncome`
- `yestransDate`：兼容日期字段，直接等于 `YstdyDate`

汇总维度（`setSummaryData`）：

- `SumYstdyIncome`
- `SumYstdyCompleteFlag`
- `SumTodayIncome`
- `SumTodayCompleteFlag`
- `SumYstdyProfitLoss`（来自明细 `YstdyProfitLoss` 的累加）

## 3. 参数开关与生效前提

收益字段计算要经过多层开关控制：

1. `PROFIT_LOSS_SUM_FLAG` 第 1 位必须为 `Y`，否则 `getProfitData` 直接返回。
2. `PROFIT_LOSS_INCONEW` 必须为 `Y`，否则不走“昨日/今日收益”计算逻辑（字段保持默认值）。
3. `ASSET_VIEW_NEW` 必须为 `Y`，才会进入“按物理日期计算收益”的主分支。
4. `NIGHTFILE_PROFIT` 控制日期选取口径（有夜行情/无夜行情）。
5. 汇总字段 `SumYstdyIncome/SumTodayIncome` 的返回还受 `YSTDY_DAYUPD=Y` 控制。

## 4. 日期取值逻辑

先定义基础日期：

- `phyDate = 当前物理日期`
- `prevPhyDate = phyDate - 1`
- `transDate = 上一 TA 工作日`（若等于 `phyDate`，再回退一天到 `prevPhyDate`）

在 `PROFIT_LOSS_INCONEW=Y` 且 `ASSET_VIEW_NEW=Y` 时：

### 4.1 有夜间行情（`NIGHTFILE_PROFIT=Y`）

- `todayIncomeDate = phyDate`
- `ystdyIncomeDate = prevPhyDate`
- 但会做 TA 工作日校验：
  - 若 `ystdyIncomeDate` 非 TA 工作日，则置 `0`（不查、不返）
  - 若 `todayIncomeDate` 非 TA 工作日，则置 `0`（不查、不返）

### 4.2 无夜间行情（`NIGHTFILE_PROFIT!=Y`）

- `todayIncomeDate = 0`（今日收益不计算）
- `ystdyIncomeDate = transDate`（按系统工作日口径）

## 5. 数据来源与取值规则

当日期大于 0 时，调用 `DxFundDailyProfitLoss` 服务：

- 昨日：按 `ystdyIncomeDate` 调 `calc(...)`
- 今日：按 `todayIncomeDate` 调 `calc(...)`

判定规则一致，均依赖 `reserve1`：

- 若数据集不为空且 `reserve1 == "1"`：
  - 收益值取 `IncomeNew`
  - 完成标识为 `"1"`
- 否则：
  - 收益值取 `0`
  - 完成标识为 `"0"`

对应赋值：

- `YstdyIncome = yestdDailyprofitloss.IncomeNew 或 0`
- `TodayIncome = todayDailyprofitloss.IncomeNew 或 0`
- `YstdyCompleteFlag/TodayCompleteFlag` 同上

并且：

- `YstdyProfitLoss = YstdyIncome`
- `yestransDate = YstdyDate`

## 6. 完成标志特殊处理

存在一条“昨日完成标识优化”：

- 条件：`NIGHTFILE_PROFIT=Y` 且 `prevPhyDate` 非 TA 工作日
- 结果：强制 `YstdyCompleteFlag = "1"`

说明：此时即使上日非 TA 工作日，也不会把昨日完成标识展示为未完成。

## 7. 汇总层（多产品合并）逻辑

在 `setSummaryData(...)` 中：

1. 明细逐行累加：
   - `sumYstdyIncome += YstdyIncome`
   - `sumTodayIncome += TodayIncome`
   - `sumYstdyProfitLoss += YstdyProfitLoss`
2. 完成标识采用“全为 1 才为 1”：
   - 任一明细 `YstdyCompleteFlag != "1"`，则 `sumYstdyCompleteFlag="0"`
   - 任一明细 `TodayCompleteFlag != "1"`，则 `sumTodayCompleteFlag="0"`
3. 仅当 `YSTDY_DAYUPD=Y` 时，才下发：
   - `SumYstdyIncome`
   - `SumYstdyCompleteFlag`
   - `SumTodayIncome`
   - `SumTodayCompleteFlag`

## 8. 逻辑流程简图

1. 开关校验：`PROFIT_LOSS_SUM_FLAG` -> `PROFIT_LOSS_INCONEW` -> `ASSET_VIEW_NEW`
2. 计算收益日期：受 `NIGHTFILE_PROFIT` 与 TA 工作日校验影响
3. 按日期调用 `DxFundDailyProfitLoss.calc(...)`
4. 用 `reserve1` 判定是否完成，取 `IncomeNew` 或 `0`
5. 回填 `YstdyIncome/TodayIncome` 与完成标志
6. 汇总时做求和与“全量完成”判定

## 9. 关键结论

- “昨日收益”“今日收益”本质上都来自 `DxFundDailyProfitLoss` 的 `IncomeNew` 字段。
- 是否有“今日收益”，核心取决于 `NIGHTFILE_PROFIT` 与 TA 工作日。
- `YstdyProfitLoss` 在当前实现中并非独立计算，而是直接复用 `YstdyIncome`。
- 汇总完成标识是严格口径：只要一条未完成，汇总即未完成。
