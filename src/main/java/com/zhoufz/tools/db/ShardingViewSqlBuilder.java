package com.zhoufz.tools.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 分表 UNION ALL 视图 SQL 构建
 * <p>
 * 物理表命名支持：
 * <ul>
 *   <li>普通：{@code tbclient} + {@code 1..N} → {@code tbclient1}</li>
 *   <li>基名含 00：{@code tbdxfundshare00} + {@code 1..N} → {@code tbdxfundshare001}</li>
 * </ul>
 * 生成形如：
 * <pre>
 * CREATE OR REPLACE VIEW `tbclient` AS
 * select t.* from (
 *  select 1 as o_table_num, t.* from `tbclient1` t
 *  union all
 *  select 2 as o_table_num, t.* from `tbclient2` t
 *  ...
 * ) t
 * </pre>
 *
 * @author zhoufz
 */
public class ShardingViewSqlBuilder {

    /**
     * 从物理表名集合中识别完整分表组（必须包含 1..shardCount 全部表）
     *
     * @param tableNames 库内 BASE TABLE 名称
     * @param shardCount tbareainfo.sharding_table_num
     * @return baseTable -> 分表号列表（升序）
     */
    public Map<String, List<Integer>> detectCompleteShardGroups(Set<String> tableNames, int shardCount) {
        if (tableNames == null || tableNames.isEmpty() || shardCount < 1) {
            return Collections.emptyMap();
        }

        Map<String, Set<Integer>> grouped = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            if (tableName == null || tableName.isEmpty()) {
                continue;
            }
            ShardSplit split = parseCanonicalShardSplit(tableName, shardCount);
            if (split == null) {
                continue;
            }
            grouped.computeIfAbsent(split.baseName, key -> new LinkedHashSet<>()).add(split.shardNo);
        }

        Map<String, List<Integer>> complete = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : grouped.entrySet()) {
            Set<Integer> shards = entry.getValue();
            if (shards.size() != shardCount) {
                continue;
            }
            boolean full = true;
            List<Integer> ordered = new ArrayList<>(shardCount);
            for (int i = 1; i <= shardCount; i++) {
                if (!shards.contains(i)) {
                    full = false;
                    break;
                }
                ordered.add(i);
            }
            if (full) {
                complete.put(entry.getKey(), ordered);
            }
        }
        return complete;
    }

    /**
     * 规范化用户指定的基表名：去掉尾部分表号。
     * <p>
     * {@code tbclient16} → {@code tbclient}；{@code tbdxfundshare001} → {@code tbdxfundshare00}。
     * 多切分合法时取最长数字后缀（避免 tbclient16 被拆成 tbclient1）。
     */
    public String normalizeBaseTableName(String tableName) {
        if (tableName == null) {
            return "";
        }
        String trimmed = tableName.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "";
        }
        ShardSplit split = parseCanonicalShardSplit(trimmed, Integer.MAX_VALUE);
        return split == null ? trimmed : split.baseName;
    }

    /**
     * 构建 CREATE OR REPLACE VIEW 语句（物理表 = 基名 + 分表号，无额外零填充）
     */
    public String buildCreateOrReplaceViewSql(String baseTableName, List<Integer> shardNumbers) {
        if (baseTableName == null || baseTableName.trim().isEmpty()) {
            throw new IllegalArgumentException("基表名不能为空");
        }
        if (shardNumbers == null || shardNumbers.isEmpty()) {
            throw new IllegalArgumentException("分表号列表不能为空: " + baseTableName);
        }

        String viewName = MysqlJdbcHelper.quoteIdentifier(baseTableName);
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE OR REPLACE VIEW ").append(viewName).append(" AS\n");
        sql.append("select t.* from (\n");
        for (int i = 0; i < shardNumbers.size(); i++) {
            int shardNo = shardNumbers.get(i);
            String physicalTable = MysqlJdbcHelper.quoteIdentifier(baseTableName + shardNo);
            if (i > 0) {
                sql.append(" union all\n");
            }
            sql.append(" select ").append(shardNo).append(" as o_table_num, t.* from ")
                    .append(physicalTable).append(" t");
        }
        sql.append("\n) t");
        return sql.toString();
    }

    /**
     * 枚举合法切分：尾部数字后缀解析为 shardNo，且 {@code base + shardNo} 必须等于原表名。
     * <p>
     * 例如 {@code tbdxfundshare001}：
     * <ul>
     *   <li>base={@code tbdxfundshare00}, shard=1 → 合法（基名含 00）</li>
     *   <li>base={@code tbdxfundshare}, 后缀 001 → 非法（拼接成 tbdxfundshare1）</li>
     * </ul>
     * 例如 {@code tbclient16}：{@code tbclient}+16 与 {@code tbclient1}+6 均数学合法，
     * 调用方应使用 {@link #parseCanonicalShardSplit} 取最长后缀。
     */
    List<ShardSplit> parseShardSplits(String tableName, int maxShardNo) {
        List<ShardSplit> result = new ArrayList<>();
        if (tableName == null || tableName.isEmpty() || maxShardNo < 1) {
            return result;
        }
        int length = tableName.length();
        int digitEnd = length;
        while (digitEnd > 0 && Character.isDigit(tableName.charAt(digitEnd - 1))) {
            digitEnd--;
        }
        if (digitEnd == 0 || digitEnd == length) {
            return result;
        }
        for (int suffixStart = length - 1; suffixStart >= digitEnd; suffixStart--) {
            String suffix = tableName.substring(suffixStart);
            String baseName = tableName.substring(0, suffixStart);
            if (baseName.isEmpty()) {
                continue;
            }
            int shardNo;
            try {
                shardNo = Integer.parseInt(suffix);
            } catch (NumberFormatException e) {
                continue;
            }
            if (shardNo < 1 || shardNo > maxShardNo) {
                continue;
            }
            if (!tableName.equals(baseName + shardNo)) {
                continue;
            }
            result.add(new ShardSplit(baseName, shardNo, suffix.length()));
        }
        return result;
    }

    /**
     * 取最长数字后缀的合法切分（tbclient16 → tbclient，而非 tbclient1）
     */
    ShardSplit parseCanonicalShardSplit(String tableName, int maxShardNo) {
        List<ShardSplit> splits = parseShardSplits(tableName, maxShardNo);
        if (splits.isEmpty()) {
            return null;
        }
        ShardSplit best = splits.get(0);
        for (ShardSplit split : splits) {
            if (split.suffixLength > best.suffixLength) {
                best = split;
            }
        }
        return best;
    }

    static final class ShardSplit {
        final String baseName;
        final int shardNo;
        final int suffixLength;

        ShardSplit(String baseName, int shardNo, int suffixLength) {
            this.baseName = baseName;
            this.shardNo = shardNo;
            this.suffixLength = suffixLength;
        }
    }
}
