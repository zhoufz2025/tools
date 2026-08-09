# `TaFileReader.formatDataFile` 方法分析

> 源码位置：`F:\AppSource\Sources\app\lcpt-server\sale\lcpt-dxfund\lcpt-dxfund-pub\lcpt-dxfund-pub-batch-bootstrap-adapter\src\main\java\com\hundsun\lcpt\dxfund\pub\batch\adapter\pub\tafile\io\TaFileReader.java`  
> 关联类：`RecordFormatTask.java`、`FileFormatUtil`、`TaFileConfig` 等。  
> 类注释说明：从 TA 到销售方的文件读取与处理；`formatDataFile` 为**私有**方法，将“需按交易片区拆分的”数据文件**不直接入业务 tmp 表**，而是**多线程按片区写出中间文件**，再合并为按片区落盘的最终文件，供后续发到各交易片区导入。

---

## 一、在类中的位置与调用链

### 1.1 入口：`formatDataFileRecord`

```text
formatDataFileRecord(context, dataFileList)
  foreach 文件路径 in dataFileList
    ├ 校验文件存在
    ├ 根据文件名匹配 config.fileTypeMap → FileType
    └ formatDataFile(tmpFileName, fileType, config.fileFieldmap.get(key), context)
        └ 成功后校验 tbfileinfo：rec_num 与格式化条数一致
```

**`formatDataFileRecord`**（`public`）负责对列表中的每个数据文件做格式化处理；每条配置对应一次 **`formatDataFile`**。处理完成后用 **`tbfileinfo`** 中的 **`rec_num`** 与返回值 **`formatRecNum`** 比对，不一致则抛出 **`ERR_DBUPDATE`**（“成功格式化的数据条数和文件中的数据条数不一致”）。

### 1.2 与其它读取路径的区别

- **`loadDataFile` / `loadDataFileRecord`**：通常走 **`importDataFile`**，按字段映射**直接导入数据库临时表**。  
- **`formatDataFile`**：典型用于 **`area_type = 公共片区`** 收到 TA 文件后，需要按 **`ta_client`（或电子合同场景下的 `asset_acc` 推导）** 路由到**多个交易片区**的场景：先在本地拆成**按片区的临时二进制/文本片段**，再 **`createFinalFile`** 合并成每个片区一个（或多个）可下发的文件。

---

## 二、`formatDataFile` 方法概要

```java
private int formatDataFile(String vFileName, FileType ft, List<Field> fieldList, IContext context)
```

| 项目 | 说明 |
|------|------|
| **参数 `vFileName`** | 磁盘上的数据文件全路径（已由前置步骤从 TA 落地）。 |
| **参数 `ft`** | `FileType`：含目标表名 `tableName`、文件类型 `fileType` 等。 |
| **参数 `fieldList`** | `tbfieldmap` 等配置的字段列表，与固定宽文件解析一致。 |
| **返回值** | 成功格式化的**记录条数**（多线程汇总的成功计数），用于回写/校验 `tbfileinfo`。 |

---

## 三、TA 固定宽文本文件的头部结构（与 `RecordFormatTask` 一致）

主线程与每个工作线程都会按相同规则跳过文件头：

1. **`skip(br, 9)`**：跳过前 **9 行**（类常量 **`fileHeadLines = 9`**，与 `RecordFormatTask` 一致）。  
2. **第 10 行**：整数 **`fieldNum`**，表示字段定义占用行数。  
3. **`skip(br, fieldNum)`**：跳过字段定义行。  
4. **下一行**：整数 **`recordCount`**，文件声明的记录总数（主线程读取；实际拆分任务仍以读到文件尾或结束标记为准）。  

编码：**GBK**（`InputStreamReader(..., "GBK")`）。

私有方法 **`skip(BufferedReader vbr, int vSkipLine)`**：连续读取 `vSkipLine` 行，若未读够则抛 **`SKIP行出错`**。

---

## 四、主流程分步说明

### 4.1 文件合法性

- `File.isFile()`、`exists()`，否则 **`ERR_FILENOTEXIST`**。

### 4.2 读取头部并得到记录总数声明

- 打开 **`BufferedReader`** 后执行上述 **`skip` + 读取 `fieldNum` + `skip(fieldNum)` + 读取 `recordCount`**。  
- **`recordCount`** 在主线程中用于计算线程规模（见下），**不用于截断读记录**（实际记录在 **`RecordFormatTask`** 中循环 `readLine`）。

### 4.3 线程池参数

从 **`ParamCache`**（可按 TA）读取：

| 参数键 | 默认值 | 含义 |
|--------|--------|------|
| **`TAFILE_THREAD_CNT`** | `5` | 工作线程数 **`procNum`**（若为空则后续逻辑可能落到 `"20"` 的兜底，代码中对空串另有判断）。 |
| **`TAFILE_QUEUE_CNT`** | `5` | 线程池队列长度 **`queueSize`**（空串时同样有兜底分支）。 |

代码中还有：**若 `recordCount <= procNum * 5`**（常量 **`numStep=5`**），则 **`procNum = 1`**，即记录很少时**退化为单线程**，避免过多线程空转。

线程池类型：**`RestlessThreadExcutor(procNum, queueSize, "TaFileReader-processor")`**，启动后提交 **`procNum`** 个任务。

### 4.4 任务提交：`RecordFormatTask`

对每个 **`stratPosition`** 从 **`0`** 到 **`procNum - 1`**：

```java
task = new RecordFormatTask(stratPosition, procNum - 1, vFileName, ft, fieldList, config, conMap);
processor.tryExecute(task);  // 队列满则 sleep(300) 重试
```

| 构造参数 | 含义 |
|----------|------|
| **`startPosition`** | 本线程从“数据区第一条记录”开始，先 **`skip(br, startPosition)`**，只处理属于自己的那一“列”行（见下）。 |
| **`skipCount`** | 传入 **`procNum - 1`**。每处理完**一条**业务记录对应的物理行后，再 **`skip(br, skipCount)`**，从而跳过其它线程负责的行，实现**轮询分片**。 |
| **`conMap`** | **`ConcurrentHashMap`**，用于跨线程去重（如电子合同 **32/44** 文件合同号重复时跳过）。 |

即：**第 `k` 条数据行（从 0 计数）由线程 `k % procNum` 处理**，每条逻辑记录在文件中占 **`procNum` 行**（若不存在多行扩展则等价于一行一条；若有合并行逻辑则以 `RecordFormatTask` 内 **`TAFILE_DEALNUM`** 等为准）。

### 4.5 等待结束与失败聚合

- **`processor.stop(0)`**：停止线程池并等待任务结束。  
- 遍历线程列表：累加每个线程上的 **`failNum` / `succNum`**，合并 **`filePathMap`**、**`recordNumMap`**（按表名、按片区下标累加记录数）。  
- 若 **`dealFailNum > 0`**：打日志并抛 **`ERR_OTHEREXP`**（“导入中有（n）个线程出错”）。

### 4.6 合并输出：`createFinalFile`

**`createFinalFile(filePathMap, recordNumMap, fieldList, vFileName)`**：

- **`filePathMap`**：`tableName → String[]`，数组长度为**片区数**，每个元素为 **`TaTableUtil.VETICAL`（竖线 `|`）** 分隔的多个临时文件路径（同一片区多段合并）。  
- 对每个表、每个 **`areaNum`**：  
  - 用 **`FileFormatUtil.formatFileHead(fieldList, tableName)`** 生成列头字符串；  
  - 写临时 **`{table}_{areaNum}_header.TXT`**：内容为 **表名行、记录数行、字段头行**；  
  - 将 header 与各分段文件路径拼成 **`mergeFiles`** 的输入列表；  
  - 输出文件名为 **`vFileName + "." + areaNum`**, 特殊表 **`tbdxfunduntradetransfer00`** 会再加后缀 **`.dxfunduntradetransfer`**，最后统一 **`.TXT`**；  
  - **`FileFormatUtil.mergeFiles`** 合并；  
  - **`addAreaFileListMap(areaFileName, areaNum)`**：把合并后的路径记入 **`config.areaFileListMap`**，供后续**按片区号发送文件**。

### 4.7 返回值

返回 **`dealSuccessNum`**（各线程成功条数之和），供 **`formatDataFileRecord`** 与 **`tbfileinfo.rec_num`** 校验。

### 4.8 异常与资源

- **`IOException`**：记日志，尝试 **`session.rollback()`**，抛 **`ERR_DBINSERT`**。  
- **`finally`**：关闭 **`BufferedReader`**（主线程只读头部，**实际业务读在子线程各自打开同一文件**）。

---

## 五、`RecordFormatTask` 核心逻辑（`formatDataFile` 的实际工作者）

`formatDataFile` 自身**不写库表**，只调度线程与合并文件；**片区判定、写临时文件、过滤规则**均在 **`RecordFormatTask.run()`** 中完成。要点如下。

### 5.1 再次打开文件并定位到本分片起始行

与主线程相同：跳过 9 行头 → 读字段数 → 跳过字段定义 → 读记录数行 → **`skip(br, startPosition)`**，进入本分片的第一条记录行。

### 5.2 按 `FileType.tableName` 打开输出流

- 默认：为每个**片区**创建一个临时文件 **`{localDirect}/{tableName}_{areaNum}_{startPosition}`**，**`DataOutputStream`** 写入。  
- **`tbdxfundtranscfmtmp`**：除主表外，还为 **`tbdxfunduntradetransfer00`** 各片区再建一套文件（非交易过户拆对方片区）。  
- **`tbdxfundpretranscfmtmp`**：仅主表多片区文件。

片区数量：**`ShardingUtil.getTotAreaNum(IDict.K_CPLX.CPLX_FUND)`**。

### 5.3 主循环：`while ((strLine = br.readLine()) != null)`

- 若等于 **`config.endFlag`**，结束。  
- **`tmpMap = getString(fieldList, strLine)`**：按固定宽 **`Field`** 解析一行（与 `TaFileReader.getString` 一致：字典转换、销售商映射、数值精度等）。  
- **`TAFILE_DEALNUM`**（默认 **3**）：可批量攒 **`dealNum`** 条再 **`getClientTableNums`** 批量解析客户，减少 RPC/查询次数。

### 5.4 路由键：`ta_client` 或 `asset_acc`

- 若字段映射中存在 **`sys_field = ta_client`**：直接用文件中 **`ta_client` 对应接口字段** 取值。  
- 否则若存在 **`asset_acc`** 且文件类型在 **`44,43,31,32`**（电子合同等）：用 **`asset_acc`** 在 **`tbassetacc{n}` / `tbtamutiacc{n}`** 或 **`tbacccfmtmp`** 查 **`ta_client`**；**查不到且无账号**时可能 **`skip(skipCount)` 后 continue**（日志提示电子合同无法拆分）。  
- 两者都无：抛 **“交易数据文件导入必须有交易账号信息”**。

### 5.5 典型过滤与兼容（节选）

- **`MERGE_FUND_ASSET`**：产品合并场景，产品在缓存中不存在则跳过本记录并 **`skip(skipCount)`**。  
- **`SKIP_REPEAT_CONTRNO`**：**32 文件**用 **`ContractSerialNo`**，**44 文件**用 **`SerialNo`**，跨线程用 **`conMap`** 去重。  
- **05 份额对账**：**`DetailFlag`** 与 **`ta_client` 空 + A** 等组合跳过；**`SHARE_COMP_DEL_DETAIL`**、**`SKIP_05_NOCLIENT`** 等与公共片区导入策略一致。  
- **133/198 非交易过户**：结合 **`targ_ta_client`**，可能写 **`tbdxfunduntradetransfer00`** 转出/转入两侧。  
- **并行改造**：**`TWO_VERSION_PARALLEL`**、**`CFM_FILE_SPLIT_MODE`** 下 **`ta_client` 不在本系统**时可 **continue** 而非报错。

### 5.6 写入：`FileFormatUtil.formatIntoFile`

根据 **`ta_client`** 算出片区 **`areaNum`**，向对应 **`DataOutputStream`** 写入格式化后的行内容，并维护 **`recordNumMap`**。

### 5.7 处理完一行后：`skip(br, skipCount)`

保证同一物理文件被 **`procNum`** 个线程按“轮流一行”的方式完整覆盖且无重复读取。

### 5.8 线程结束时

把 **`succNum`/`failNum`**、**`filePathMap`**、**`recordNumMap`** 设回 **`RestlessThread`**，供 **`formatDataFile`** 汇总。

---

## 六、与 `importDataFile` 的对比（便于理解何时走 format）

| 维度 | `formatDataFile` | `importDataFile`（典型） |
|------|------------------|---------------------------|
| 目的 | 按片区**拆分生成文件**，供下发交易片区 | **直接 insert** 临时表 |
| 数据库 | 主流程几乎不写库（子任务可能查 **`tbassetacc`** 等） | 批量 **`insert`** |
| 并行 | **`RestlessThreadExcutor` + RecordFormatTask** | 另有 **`RecordImportTask`** 等 |
| 输出 | **`config.areaFileListMap` + 本地合并 TXT** | 表数据 |

---

## 七、配置与运维关注点

1. **`TAFILE_THREAD_CNT` / `TAFILE_QUEUE_CNT`**：线程数过大且同一文件多线程读，磁盘与 CPU 竞争上升；过小则拆分慢。  
2. **`TAFILE_DEALNUM`**：批量解析客户信息条数，影响内存与单次查询批量大小。  
3. **电子合同 43/44/31/32**：必须能在文件中或通过 **`asset_acc`** 解析出 **`ta_client`**，否则线程内跳过可能导致与 **`tbfileinfo.rec_num`** 不一致，需关注 **`SKIP_REPEAT_CONTRNO`**、TA 实际行结构。  
4. **`createFinalFile`** 中有一处日志使用 **`LcptLog.getTransLogCache().error`** 打印“合并文件：…”（语义实为信息），排查问题时注意不要被误导为错误。

---

## 八、小结

**`formatDataFile`** 负责：校验文件 → 解析 TA 固定宽文件头 → 按配置启动 **`procNum`** 个 **`RecordFormatTask`**，以**轮询方式**将每条记录分配到不同线程 → 各线程按 **`ta_client`/账号** 写入**分片区临时文件** → 汇总失败数 → **`createFinalFile`** 按表、按片区 **merge** 并登记 **`areaFileListMap`** → 返回成功处理条数供 **`tbfileinfo`** 校验。  

深入理解拆分规则时，应**同步阅读** **`RecordFormatTask.java`**（数据行循环、`skip(skipCount)`、电子合同与 05 文件特殊分支）。

---

*文档依据：`TaFileReader.java`（约 1130–1410、2647–2669 行）、`RecordFormatTask.java`（约 70–520 行）；若源码变更请以仓库为准。*
