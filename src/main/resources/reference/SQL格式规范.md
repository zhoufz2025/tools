# SQL格式规范

## 适用范围
本文档定义了机构报送说明文档中所有INSERT INTO ... SELECT语句的格式规范。

## 格式规则

### 1. 字段一一对应原则
- **INSERT INTO** 语句中的字段列表和 **SELECT** 语句中的字段列表必须一一对应
- INSERT字段和SELECT字段在相同位置，确保字段顺序一致
- **重要**：INSERT和SELECT的每一行必须相互对应，即第一行的INSERT字段数量必须等于第一行的SELECT字段数量，第二行对应第二行，以此类推

### 2. 字段格式
- 每行放置5-7个字段（根据字段名长度和屏幕宽度调整，此规则可适当不强制）
- **重要**：前后规整，确保INSERT和SELECT的每一行字段数量对应
- 字段名后跟逗号（最后一个字段除外）
- INSERT字段使用小写，SELECT字段使用小写（保持一致性）
- 字段之间用逗号和空格分隔

### 3. CASE WHEN语句处理
- 如果SELECT语句中包含CASE WHEN等复杂表达式，必须单独起一行
- CASE WHEN语句必须缩进，使其与上一行的字段对齐，能明显看出是同一个字段
- CASE WHEN语句的缩进应该与SELECT字段的缩进保持一致
- CASE WHEN语句单独占一行时，对应的INSERT行也应该只有对应数量的字段

### 4. 注释处理
- 字段注释放在字段所在行的末尾，使用 `--` 注释
- 复杂逻辑的注释可以单独起一行，放在相关字段之前

### 5. 缩进规范
- INSERT字段列表：使用2个空格缩进（相对于INSERT INTO）
- SELECT字段列表：使用10个空格缩进（相对于SELECT），确保与INSERT字段对齐
- CASE WHEN语句：与SELECT字段保持相同缩进

## 示例

### 示例1：基本字段对应（每行对应）
```sql
INSERT INTO table_name 
  (field1, field2, field3, field4, field5, 
   field6, field7, field8)
SELECT a.field1, a.field2, a.field3, a.field4, a.field5, 
       a.field6, a.field7, a.field8
FROM source_table a;
```

### 示例2：包含CASE WHEN的字段（行对应）
```sql
INSERT INTO table_name 
  (field1, field2, 
   field3, field4, field5)
SELECT a.field1, a.field2, 
       -- 复杂转换逻辑
       CASE WHEN condition 
            THEN value1 
            ELSE value2 END field3, 
       a.field4, a.field5
FROM source_table a;
```

### 示例3：多字段复杂SQL（每行对应）
```sql
INSERT INTO tbdxfundbscstranscfm 
  (ta_code, cfm_date, cfm_no, ori_cfm_no, from_flag, trans_date, trans_time, 
   clear_date, serial_no, trans_code, busin_code, branch_no, open_branch, 
   channel, term_no, oper_no, 
   in_client_no, 
   client_type, 
   asset_acc, bank_no, client_no, client_name, bank_acc, ta_client, 
   trans_account_type, trans_account, cash_flag, 
   prd_code, 
   share_class, nav, price, amt, curr_type, cfm_amt, vol, cfm_vol)
SELECT a.ta_code, a.cfm_date, a.cfm_no, a.ori_cfm_no, a.from_flag, a.trans_date, a.trans_time, 
       a.clear_date, a.serial_no, a.trans_code, a.busin_code, a.branch_no, a.open_branch, 
       a.channel, a.term_no, a.oper_no, 
       a.in_client_no, 
       a.client_type, 
       a.asset_acc, a.bank_no, a.client_no, a.client_name, a.bank_acc, a.ta_client, 
       a.trans_account_type, a.trans_account, a.cash_flag, 
       -- 分级基金转换为主代码
       CASE WHEN (substr(b.control_flag,82,1)='1' AND b.reserve2<>' ') 
            THEN b.reserve2 
            ELSE a.prd_code END prd_code, 
       a.share_class, a.nav, a.price, a.amt, a.curr_type, a.cfm_amt, a.vol, a.cfm_vol
FROM tbdxfundbscstranscfmsync a, tbdxfundproduct b
WHERE a.prd_code = b.prd_code;
```

## 检查清单
在编写或修改SQL时，请确保：
- [ ] INSERT字段和SELECT字段数量一致
- [ ] INSERT字段和SELECT字段顺序一致
- [ ] **INSERT和SELECT的每一行字段数量对应**（第一行对第一行，第二行对第二行）
- [ ] CASE WHEN语句正确缩进，能看出与上一行的关联
- [ ] 注释位置合理，不影响字段对应关系
- [ ] 前后规整，便于对照检查

## 文件位置
本规范文件位置：`/Users/zhoufz/hundsun/lcpt60/git/Sources/app/lcpt-server/sale/lcpt-dxfund/分析报告/SQL格式规范.md`

## 更新记录
- 2024-XX-XX：创建格式规范文档
- 2024-XX-XX：更新为每行5-7个字段格式，确保1屏能看全
- 2024-XX-XX：强调INSERT和SELECT每一行必须相互对应，前后规整
