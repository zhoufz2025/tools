# T2H9602HSAdapter 代码逻辑分析

## 一、类概述

### 1.1 基本信息
- **类名**: `T2H9602HSAdapter`
- **继承关系**: 继承自 `FinaBatchAdapter`
- **功能**: 处理解冻扣款结果文件（2H9602交易码）
- **版本**: 6.0.0.1
- **交易码**: 2H9602

### 1.2 核心功能
该类是一个批处理适配器，用于处理银行主机返回的解冻扣款处理结果文件，主要功能包括：
1. 接收解冻扣款结果文件
2. 将文件数据导入到结果表 `tbjdkkresult`
3. 更新结果表中的客户信息
4. 根据结果更新清算表和交易请求表的状态
5. 导出处理后的结果文件

## 二、主要流程分析

### 2.1 action() 方法 - 主流程入口

```java
protected void action(IContext context) throws Exception {
    BatchLog.writeLog(context, "开始处理");
    TransVar2H9602 transVar = new TransVar2H9602();
    transRequest(context, transVar);           // 步骤1: 处理请求参数
    getResultFile(context, transVar);          // 步骤2: 获取结果文件
    impFileToResultTable(context, transVar);   // 步骤3: 导入文件到结果表
    updateJdkkResult(context, transVar);       // 步骤4: 更新结果表
    updateReqAndSquare(context, transVar);     // 步骤5: 更新请求和清算表
    expJdkkResult(context, transVar);          // 步骤6: 导出结果文件
    BatchLog.writeLog(context, "处理完成");
}
```

**流程说明**：
1. **transRequest**: 从请求中提取参数，设置文件路径和文件名
2. **getResultFile**: 从服务器获取解冻扣款结果文件
3. **impFileToResultTable**: 将文件内容导入到 `tbjdkkresult` 表
4. **updateJdkkResult**: 根据原始清算记录更新结果表的客户信息
5. **updateReqAndSquare**: 根据结果更新清算表和交易请求表的状态
6. **expJdkkResult**: 导出处理后的结果文件

## 三、各方法详细分析

### 3.1 transRequest() - 处理请求参数

**功能**: 从请求中提取参数，初始化处理变量

**关键逻辑**:
1. 从请求中获取 `TACode`（TA代码）
2. 获取系统参数 `SysArgInfo`
3. 设置本地文件路径（根据分片ID）
4. 确定文件日期：
   - 默认使用 `sysArg.getPrevDate()`（上一交易日）
   - 如果参数 `T2H9602_SQUARERRFILE_DATE` 为 "1"，则使用 `sysArg.getInitDate()`（初始日期）
5. 生成文件名格式：`tbjdkkresult_{taCode}_{areaId}.{fileDate}`

**代码位置**: 432-448行

### 3.2 getResultFile() - 获取结果文件

**功能**: 从服务器下载解冻扣款结果文件到本地

**关键逻辑**:
1. 配置文件传输参数：
   - 服务器路径：`ResourceUtil.getInnerFileRemotePath(InnerFileType.SQUARE)`
   - 本地路径：`transVar.getLocalPath()`
   - 文件名：`transVar.getFileName()`
2. 使用 `GenericFileTransferUtil.receiveFile()` 接收文件

**代码位置**: 422-430行

### 3.3 impFileToResultTable() - 导入文件到结果表

**功能**: 将解冻扣款结果文件导入到 `tbjdkkresult` 表

**关键逻辑**:
1. **删除旧数据**: 根据 `square_date`、`ta_code`、`function_id` 和 `bank_no` 删除旧记录
2. **读取文件**:
   - 使用 UTF-8 编码读取文件
   - 第一行作为表头，包含字段名（用 `@%@` 分隔）
   - 从第二行开始为数据行
3. **生成SQL**: 根据表头动态生成 INSERT 语句
4. **插入数据**: 逐行解析数据并插入到表中
5. **异常处理**: 捕获文件读取、编码、SQL等异常，记录日志并回滚事务

**文件格式**:
- 字段分隔符: `@%@`
- 第一行: 字段名列表
- 后续行: 数据记录

**代码位置**: 326-408行

**关键方法**: `getSqlFromFileHead()` (410-420行)
- 根据表头字段动态生成 INSERT SQL 语句

### 3.4 updateJdkkResult() - 更新结果表

**功能**: 根据原始清算记录更新结果表中的客户信息

**关键逻辑**:
1. **查询结果表**: 查询 `tbjdkkresult` 表中指定日期的记录
2. **获取原始清算记录**: 通过 `TransSquareUtil.getOriSquare()` 获取原始清算记录
3. **更新字段**:
   - `in_client_no`: 内部客户号
   - `bank_acc`: 银行账号
   - `client_no`: 客户号
   - `amt`: 金额（如果金额<=0，则使用 `unfrozen_amt`）
   - `curr_type`: 币种
   - `prd_code`: 产品代码
   - `liqu_dir`: 清算方向
   - `bank_no`: 银行代码

**代码位置**: 290-324行

### 3.5 updateReqAndSquare() - 更新请求和清算表

**功能**: 根据解冻扣款结果更新清算表和交易请求表的状态

**关键逻辑**:

#### 3.5.1 错误记录处理
- 如果参数 `JDKK_TO_SQUAREERR` 为 "1"，将错误记录插入到 `tbsquareerr` 表
- 先删除旧记录，再插入新记录

#### 3.5.2 更新清算表状态
遍历所有分片表（`tbsquare1` 到 `tbsquareN`）：

1. **更新失败状态**:
   - 将存在错误（`err_code <> 'AAAAAAA'`）的清算记录状态更新为失败
   - 状态值根据参数 `T2H9600_SQUARE_ERR_REDEAL` 决定：
     - 为 "1" 时，状态设为 "6"（可重处理）
     - 否则设为 "5"（失败）
   - `square_status` 设为 "2"（失败）

2. **更新交易请求表**:
   - 将对应失败清算记录的交易请求的 `liqu_status` 更新为 "3"（失败）
   - `status` 更新为 "4"（失败）

3. **更新成功状态**:
   - 将状态为 `defStatus`（默认状态，根据 `SQUARE_DOING_STATUS` 参数决定）的清算记录更新为成功
   - `status` 设为 "2"（成功）
   - `square_status` 设为 "1"（成功）

#### 3.5.3 返还发行额度
对于失败的清算记录：
- 检查交易请求的控制位37位是否为 "1"（已返还额度）
- 如果未返还，调用 `returnIssAmt()` 返还产品发行额度
- 更新控制位37位为 "1"

#### 3.5.4 更新交易请求清算状态
- 将成功清算记录对应的交易请求的 `liqu_status` 更新为 "4"（成功）

**代码位置**: 165-253行

**关键方法**: `returnIssAmt()` (255-288行)
- 根据产品控制位和分支信息，调用 `PubFinaApiFactory.returnIssAmt()` 返还产品发行额度

### 3.6 expJdkkResult() - 导出结果文件

**功能**: 将处理后的结果表数据导出为文件

**关键逻辑**:
1. **构建查询SQL**:
   - 如果 `ta_code` 为 "000000"（全部TA），查询所有记录
   - 否则查询指定TA的记录
   - 如果 `bank_no` 不等于默认银行号，增加银行号条件
2. **生成文件**:
   - 文件名格式：
     - 默认银行: `tbjdkkresult_{taCode}_{fileDate}_{areaId}.txt`
     - 非默认银行: `tbjdkkresult~{taCode}~{bankNo}~{fileDate}~{areaId}.txt`
   - 文件格式：
     - 第一行：字段名（用 `@%@` 分隔）
     - 后续行：数据记录（用 `@%@` 分隔）
3. **上传文件**: 使用 `GenericFileTransferUtil.sendFile()` 上传到服务器

**代码位置**: 68-137行

**关键方法**:
- `getFileHead()` (139-150行): 生成文件头（字段名行）
- `getFileLine()` (152-163行): 生成数据行

## 四、SQL占位符（?）取值详细说明

### 4.1 impFileToResultTable() 方法中的SQL

#### 4.1.1 DELETE语句（341行）

```sql
delete from tbjdkkresult where square_date = ? and ta_code=? and function_id = ? 
```

**占位符取值**（按顺序）:
1. **第1个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer
   - 说明: 清算日期，来自系统参数 `sysArg.getPrevDate()` 或 `sysArg.getInitDate()`

2. **第2个`?`**: `transVar.getTaCode()`
   - 来源: `transVar.getTaCode()` - TA代码
   - 类型: String
   - 说明: 从请求参数中获取的TA代码

3. **第3个`?`**: `transCode`（值为 `"2H9602"`）
   - 来源: 方法内定义的常量
   - 类型: String
   - 说明: 功能ID，固定为 "2H9602"

**代码位置**: 341行

#### 4.1.2 INSERT语句（动态生成，353行）

```sql
insert into tbjdkkresult (field1, field2, ...) values (?, ?, ...)
```

**占位符取值**:
- **所有`?`**: `(Object[])fieldValue`
  - 来源: `readLine.split("@%@")` - 从文件行分割得到的字符串数组
  - 类型: String[]
  - 说明: 
    - 文件第一行是字段名（表头）
    - 从第二行开始是数据行
    - 每行用 `@%@` 分隔符分割成字段值数组
    - 数组中的每个元素按顺序对应INSERT语句中的每个字段
    - 字段顺序与表头中的字段顺序一致

**代码位置**: 352-353行

**示例**:
```
文件内容:
square_date@%@square_no@%@seq_no@%@bank_no
20240101@%@SQ001@%@1@%@001

fieldValue = ["20240101", "SQ001", "1", "001"]
对应SQL: insert into tbjdkkresult (square_date, square_no, seq_no, bank_no) values (?, ?, ?, ?)
参数值: ["20240101", "SQ001", "1", "001"]
```

### 4.2 updateJdkkResult() 方法中的SQL

#### 4.2.1 SELECT语句（296行）

```sql
select in_client_no,square_no from tbjdkkresult where square_date=? and function_id=? and ta_code=?
```

**占位符取值**（按顺序）:
1. **第1个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

2. **第2个`?`**: `transCode`（值为 `"2H9602"`）
   - 来源: 方法内定义的常量
   - 类型: String

3. **第3个`?`**: `transVar.getTaCode()`
   - 来源: `transVar.getTaCode()` - TA代码
   - 类型: String

**代码位置**: 296行

#### 4.2.2 UPDATE语句（300行）

```sql
update tbjdkkresult set in_client_no=?, bank_acc=?, client_no=?, amt=?, curr_type=?, prd_code=?, liqu_dir=?, bank_no=? where square_date=? and square_no=?
```

**占位符取值**（按顺序）:
1. **第1个`?`**: `square.getInClientNo()`
   - 来源: 从原始清算记录（Square对象）获取
   - 类型: String

2. **第2个`?`**: `square.getBankAcc()`
   - 来源: 从原始清算记录获取
   - 类型: String

3. **第3个`?`**: `square.getClientNo()`
   - 来源: 从原始清算记录获取
   - 类型: String

4. **第4个`?`**: `Double.valueOf((BigDecimalUtil.compare(square.getAmt(), 0.0D) > 0) ? square.getAmt() : square.getUnfrozenAmt())`
   - 来源: 从原始清算记录获取，如果金额>0则用amt，否则用unfrozen_amt
   - 类型: Double
   - 说明: 三元表达式判断金额是否大于0

5. **第5个`?`**: `square.getCurrType()`
   - 来源: 从原始清算记录获取
   - 类型: String

6. **第6个`?`**: `square.getPrdCode()`
   - 来源: 从原始清算记录获取
   - 类型: String

7. **第7个`?`**: `square.getLiquDir()`
   - 来源: 从原始清算记录获取
   - 类型: String

8. **第8个`?`**: `square.getBankNo()`
   - 来源: 从原始清算记录获取
   - 类型: String

9. **第9个`?`（WHERE条件）**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

10. **第10个`?`（WHERE条件）**: `rs.getString("square_no")`
    - 来源: 从查询结果集（ResultSet）中获取
    - 类型: String
    - 说明: 当前循环处理的清算编号

**代码位置**: 300-305行

**关键说明**: 
- `square` 对象通过 `TransSquareUtil.getOriSquare()` 方法获取（298行）
- 参数包括: `transVar.getFileDate()`, `rs.getString("square_no")`, `rs.getString("in_client_no")`

### 4.3 updateReqAndSquare() 方法中的SQL

#### 4.3.1 DELETE语句（197行）

```sql
delete from tbsquareerr where square_date = ? and trans_code = '2H9602' 
```

**占位符取值**:
1. **第1个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

**代码位置**: 197行

#### 4.3.2 INSERT INTO ... SELECT语句（198行）

```sql
insert into tbsquareerr(...) select ... from tbjdkkresult where square_date = ?
```

**占位符取值**:
1. **第1个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

**代码位置**: 199行

#### 4.3.3 UPDATE清算表失败状态（204行）

```sql
update tbsquare{tableNum} a set a.status=?, square_status=?, modify_timestamp=? where a.square_date=? and a.trans_code='2H9600' and exists (...) and b.err_code <> ?
```

**占位符取值**（按顺序）:
1. **第1个`?`**: `status`
   - 来源: 根据参数 `T2H9600_SQUARE_ERR_REDEAL` 决定
   - 类型: String
   - 取值逻辑:
     - 如果参数为 "1"，则 `status = "6"`（可重处理）
     - 否则 `status = "5"`（失败）

2. **第2个`?`**: `"2"`
   - 来源: 固定值
   - 类型: String
   - 说明: square_status失败状态

3. **第3个`?`**: `Double.valueOf(Double.parseDouble(DateUtil.getDateTime()))`
   - 来源: 当前系统时间戳
   - 类型: Double
   - 说明: 修改时间戳，格式化为Double类型

4. **第4个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

5. **第5个`?`**: `"AAAAAAA"`
   - 来源: 固定值
   - 类型: String
   - 说明: 错误代码，用于判断是否有错误（不等于此值表示有错误）

**代码位置**: 204-207行

#### 4.3.4 UPDATE交易请求表失败状态（208行）

```sql
update tbtransreq{tableNum} a set liqu_status=?, status=? where exists (...) and a.liqu_status=?
```

**占位符取值**（按顺序）:
1. **第1个`?`**: `"3"`
   - 来源: 固定值
   - 类型: String
   - 说明: liqu_status失败状态

2. **第2个`?`**: `"4"`
   - 来源: 固定值
   - 类型: String
   - 说明: status失败状态

3. **第3个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

4. **第4个`?`**: `status`
   - 来源: 同4.3.3中的status变量
   - 类型: String
   - 说明: 清算表的失败状态（"5"或"6"）

5. **第5个`?`**: `"Z"`
   - 来源: 固定值
   - 类型: String
   - 说明: 待清算状态，用于条件判断

**代码位置**: 208-210行

#### 4.3.5 UPDATE清算表成功状态（211行）

```sql
update tbsquare{tableNum} a set a.status=?, square_status=?, modify_timestamp=? where a.square_date=? and a.trans_code='2H9600' and a.status=?
```

**占位符取值**（按顺序）:
1. **第1个`?`**: `"2"`
   - 来源: 固定值
   - 类型: String
   - 说明: status成功状态

2. **第2个`?`**: `"1"`
   - 来源: 固定值
   - 类型: String
   - 说明: square_status成功状态

3. **第3个`?`**: `Double.valueOf(Double.parseDouble(DateUtil.getDateTime()))`
   - 来源: 当前系统时间戳
   - 类型: Double

4. **第4个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

5. **第5个`?`**: `defStatus`
   - 来源: 根据参数 `SQUARE_DOING_STATUS` 决定
   - 类型: String
   - 取值逻辑:
     - 如果参数为 "1"，则 `defStatus = "1"`（进行中）
     - 否则 `defStatus = "2"`（默认状态）

**代码位置**: 211-214行

#### 4.3.6 SELECT查询失败清算记录（220行）

```sql
select * from tbsquare{i} a where exists (...) and a.square_date=? and a.status=? and a.trans_code='2H9600'
```

**占位符取值**（按顺序）:
1. **第1个`?`**: `"3"`
   - 来源: 固定值
   - 类型: String
   - 说明: liqu_status失败状态

2. **第2个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

3. **第3个`?`**: `status`
   - 来源: 同4.3.3中的status变量
   - 类型: String
   - 说明: 清算表的失败状态（"5"或"6"）

**代码位置**: 220-222行

#### 4.3.7 UPDATE交易请求表成功状态（235行）

```sql
update tbtransreq{i} a set a.liqu_status=?, a.modify_timestamp=? where a.liqu_status=? and exists (...) and b.status=?
```

**占位符取值**（按顺序）:
1. **第1个`?`**: `"4"`
   - 来源: 固定值
   - 类型: String
   - 说明: liqu_status成功状态

2. **第2个`?`**: `Double.valueOf(Double.parseDouble(DateUtil.getDateTime()))`
   - 来源: 当前系统时间戳
   - 类型: Double

3. **第3个`?`**: `"Z"`
   - 来源: 固定值
   - 类型: String
   - 说明: 待清算状态，用于条件判断

4. **第4个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

5. **第5个`?`**: `"2"`
   - 来源: 固定值
   - 类型: String
   - 说明: status成功状态

**代码位置**: 235-239行

### 4.4 expJdkkResult() 方法中的SQL

#### 4.4.1 SELECT语句（全部TA，89行）

```sql
select * from tbjdkkresult a where a.square_date=? and function_id='2H9602'
```

**占位符取值**:
1. **第1个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

**代码位置**: 89行

#### 4.4.2 SELECT语句（指定TA，94行）

```sql
select * from tbjdkkresult a where a.square_date=? and a.function_id='2H9602' and ta_code=?
```

**占位符取值**（按顺序）:
1. **第1个`?`**: `Integer.valueOf(transVar.getFileDate())`
   - 来源: `transVar.getFileDate()` - 文件日期
   - 类型: Integer

2. **第2个`?`**: `transVar.getTaCode()`
   - 来源: `transVar.getTaCode()` - TA代码
   - 类型: String

**代码位置**: 94行

### 4.5 占位符取值总结表

| 方法 | SQL类型 | 占位符位置 | 取值来源 | 数据类型 | 说明 |
|------|---------|-----------|---------|---------|------|
| impFileToResultTable | DELETE | 第1个 | `transVar.getFileDate()` | Integer | 文件日期 |
| impFileToResultTable | DELETE | 第2个 | `transVar.getTaCode()` | String | TA代码 |
| impFileToResultTable | DELETE | 第3个 | `"2H9602"` | String | 功能ID |
| impFileToResultTable | INSERT | 所有 | `readLine.split("@%@")` | String[] | 文件行分割数组 |
| updateJdkkResult | SELECT | 第1个 | `transVar.getFileDate()` | Integer | 文件日期 |
| updateJdkkResult | SELECT | 第2个 | `"2H9602"` | String | 功能ID |
| updateJdkkResult | SELECT | 第3个 | `transVar.getTaCode()` | String | TA代码 |
| updateJdkkResult | UPDATE | 第1-8个 | `square对象属性` | String/Double | 原始清算记录字段 |
| updateJdkkResult | UPDATE | 第9个 | `transVar.getFileDate()` | Integer | 文件日期 |
| updateJdkkResult | UPDATE | 第10个 | `rs.getString("square_no")` | String | 结果集清算编号 |
| updateReqAndSquare | DELETE | 第1个 | `transVar.getFileDate()` | Integer | 文件日期 |
| updateReqAndSquare | INSERT | 第1个 | `transVar.getFileDate()` | Integer | 文件日期 |
| updateReqAndSquare | UPDATE | 第1个 | `status变量` | String | 失败状态（"5"或"6"） |
| updateReqAndSquare | UPDATE | 第2个 | `"2"` | String | square_status失败 |
| updateReqAndSquare | UPDATE | 第3个 | `DateUtil.getDateTime()` | Double | 当前时间戳 |
| updateReqAndSquare | UPDATE | 第4个 | `transVar.getFileDate()` | Integer | 文件日期 |
| updateReqAndSquare | UPDATE | 第5个 | `"AAAAAAA"` | String | 错误代码判断值 |
| updateReqAndSquare | SELECT | 第1个 | `"3"` | String | liqu_status失败 |
| updateReqAndSquare | SELECT | 第2个 | `transVar.getFileDate()` | Integer | 文件日期 |
| updateReqAndSquare | SELECT | 第3个 | `status变量` | String | 清算失败状态 |
| expJdkkResult | SELECT | 第1个 | `transVar.getFileDate()` | Integer | 文件日期 |
| expJdkkResult | SELECT | 第2个 | `transVar.getTaCode()` | String | TA代码 |

### 4.6 关键变量说明

#### 4.6.1 transVar对象
- `transVar.getFileDate()`: 文件日期，来自系统参数
- `transVar.getTaCode()`: TA代码，来自请求参数
- `transVar.getSysArg()`: 系统参数对象

#### 4.6.2 状态变量
- `status`: 失败状态值，根据参数 `T2H9600_SQUARE_ERR_REDEAL` 决定（"5"或"6"）
- `defStatus`: 默认状态值，根据参数 `SQUARE_DOING_STATUS` 决定（"1"或"2"）

#### 4.6.3 固定值
- `"2H9602"`: 功能ID/交易代码
- `"2H9600"`: 清算交易代码
- `"AAAAAAA"`: 错误代码判断值（表示无错误）
- `"Z"`: 待清算状态
- `"2"`: 成功状态或square_status失败
- `"1"`: square_status成功
- `"3"`: liqu_status失败
- `"4"`: liqu_status成功或status失败

## 五、关键数据表

### 5.1 tbjdkkresult - 解冻扣款结果表

**主要字段**:
- `square_date`: 清算日期
- `square_no`: 清算编号
- `seq_no`: 序号
- `bank_no`: 银行代码
- `client_no`: 客户号
- `in_client_no`: 内部客户号
- `bank_acc`: 银行账号
- `prd_code`: 产品代码
- `amt`: 金额
- `curr_type`: 币种
- `err_code`: 错误代码
- `err_msg`: 错误信息
- `liqu_dir`: 清算方向
- `ta_code`: TA代码
- `function_id`: 功能ID（固定为 "2H9602"）
- `host_date`: 主机日期
- `host_serial`: 主机流水号

### 5.2 tbsquare - 清算表（分片表）

**相关字段**:
- `square_date`: 清算日期
- `square_no`: 清算编号
- `serial_no`: 流水号
- `trans_code`: 交易代码（对应 "2H9600"）
- `status`: 状态（"2"=成功，"5"=失败，"6"=可重处理）
- `square_status`: 清算状态（"1"=成功，"2"=失败）

### 5.3 tbtransreq - 交易请求表（分片表）

**相关字段**:
- `serial_no`: 流水号
- `liqu_status`: 清算状态（"Z"=待清算，"3"=失败，"4"=成功）
- `status`: 状态（"4"=失败）

### 5.4 tbsquareerr - 清算错误表

**用途**: 存储清算错误记录

## 六、关键参数配置

### 6.1 系统参数

| 参数名 | 说明 | 默认值 | 用途 |
|--------|------|--------|------|
| `DEFAULTBANKNO` | 默认银行号 | "000" | 判断是否为默认银行 |
| `SQUARE_DOING_STATUS` | 清算进行中状态 | "0" | 确定默认清算状态 |
| `T2H9600_SQUARE_ERR_REDEAL` | 清算错误是否可重处理 | "0" | 决定失败状态值（"5"或"6"） |
| `JDKK_TO_SQUAREERR` | 是否将解冻扣款错误写入错误表 | "0" | 控制错误记录处理 |
| `T2H9602_SQUARERRFILE_DATE` | 清算错误文件日期类型 | "0" | 决定使用上一交易日还是初始日期 |

### 6.2 状态值说明

**清算状态（status）**:
- `"1"`: 进行中（根据 `SQUARE_DOING_STATUS` 参数决定）
- `"2"`: 成功
- `"5"`: 失败（不可重处理）
- `"6"`: 失败（可重处理）

**清算状态（square_status）**:
- `"1"`: 成功
- `"2"`: 失败

**清算状态（liqu_status）**:
- `"Z"`: 待清算
- `"3"`: 失败
- `"4"`: 成功

## 七、文件格式说明

### 7.1 输入文件格式

**文件编码**: UTF-8

**字段分隔符**: `@%@`

**格式示例**:
```
square_date@%@square_no@%@seq_no@%@bank_no@%@client_no@%@...
20240101@%@SQ001@%@1@%@001@%@C001@%@...
20240101@%@SQ002@%@1@%@001@%@C002@%@...
```

### 7.2 输出文件格式

**文件编码**: UTF-8

**字段分隔符**: `@%@`

**格式**: 与输入文件格式相同

**文件名规则**:
- 默认银行: `tbjdkkresult_{taCode}_{fileDate}_{areaId}.txt`
- 非默认银行: `tbjdkkresult~{taCode}~{bankNo}~{fileDate}~{areaId}.txt`

## 八、异常处理

### 8.1 文件操作异常
- `FileNotFoundException`: 文件不存在
- `UnsupportedEncodingException`: 编码不支持
- `IOException`: IO操作异常

### 8.2 数据库异常
- `SQLException`: SQL执行异常

### 8.3 业务异常
- `BizBussinessException`: 业务异常
  - 错误码 "1026": 文件读取异常
  - 错误码 "1900": 数据库操作异常
  - 错误码 "4696": 其他异常

## 九、关键依赖

### 9.1 工具类
- `TransSquareUtil`: 获取原始清算记录
- `FinaParamUtil`: 获取系统参数
- `ControlFlagUtil`: 控制位操作
- `BigDecimalUtil`: 金额比较
- `DateUtil`: 日期处理
- `ShardingUtil`: 分片工具
- `ResourceUtil`: 资源路径工具
- `GenericFileTransferUtil`: 文件传输工具

### 9.2 服务类
- `FinaServiceFactory.getTransReqService()`: 交易请求服务
- `PubFinaApiFactory.getProduct()`: 获取产品信息
- `PubFinaApiFactory.returnIssAmt()`: 返还发行额度
- `PubApiFactory.getBranch()`: 获取分支信息

## 十、处理流程总结

```
开始
  ↓
1. 处理请求参数（transRequest）
  - 获取TA代码、系统参数
  - 设置文件路径和文件名
  ↓
2. 获取结果文件（getResultFile）
  - 从服务器下载文件到本地
  ↓
3. 导入文件到结果表（impFileToResultTable）
  - 删除旧数据
  - 读取文件并插入到tbjdkkresult表
  ↓
4. 更新结果表（updateJdkkResult）
  - 根据原始清算记录更新客户信息
  ↓
5. 更新请求和清算表（updateReqAndSquare）
  - 处理错误记录（可选）
  - 更新清算表状态（成功/失败）
  - 更新交易请求表状态
  - 返还失败交易的发行额度
  ↓
6. 导出结果文件（expJdkkResult）
  - 查询结果表数据
  - 生成文件并上传到服务器
  ↓
结束
```

## 十一、注意事项

1. **分片表处理**: 需要遍历所有分片表（`tbsquare1` 到 `tbsquareN`）
2. **事务控制**: 关键操作需要事务控制，异常时回滚
3. **文件编码**: 统一使用 UTF-8 编码
4. **字段分隔符**: 文件字段使用 `@%@` 分隔
5. **银行号判断**: 需要区分默认银行和非默认银行，影响文件名和查询条件
6. **状态更新**: 需要根据参数配置决定状态值
7. **额度返还**: 失败交易需要返还产品发行额度，避免占用额度

## 十二、相关交易码

- **2H9602**: 解冻扣款结果处理（本交易）
- **2H9600**: 对应的清算交易码（在清算表中使用）

## 十三、代码位置

**文件路径**: `/Users/zhoufz/Desktop/1.txt`

**包路径**: `com.hundsun.lcpt.fina.batch.bank.hhlc.T2H9602.T2H9602HSAdapter`

---

**分析日期**: 2024年
**分析人员**: AI Assistant
