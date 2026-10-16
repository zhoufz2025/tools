package com.zhoufz.tools.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 根据 tbareainfo.sharding_table_num 与库内物理分表，生成 UNION ALL 视图
 * <p>
 * 支持：
 * <ul>
 *   <li>自动识别完整分表组（如 tbclient1..16 → 视图 tbclient）</li>
 *   <li>指定基表名（如 tbclient）仅生成对应视图</li>
 *   <li>CREATE OR REPLACE VIEW，可重复执行</li>
 * </ul>
 *
 * @author zhoufz
 */
public class ShardingViewService {

    private static final Logger log = LoggerFactory.getLogger(ShardingViewService.class);

    private final LocalDbInitProperties properties;

    private final MysqlJdbcHelper jdbcHelper;

    private final ShardingViewSqlBuilder sqlBuilder = new ShardingViewSqlBuilder();

    public ShardingViewService(LocalDbInitProperties properties) {
        this.properties = properties;
        this.jdbcHelper = new MysqlJdbcHelper(properties);
    }

    /**
     * 按配置默认种类，自动识别并生成全部分表视图
     */
    public ShardingViewResult createViews() {
        return createViews(properties.getCategorySet(), null);
    }

    /**
     * 按种类自动识别并生成分表视图
     */
    public ShardingViewResult createViews(Set<DbInitCategory> categories) {
        return createViews(categories, null, null);
    }

    /**
     * 在指定库上自动识别并生成分表视图
     *
     * @param databases 库名列表；为空则按种类解析
     */
    public ShardingViewResult createViewsForDatabases(List<String> databases) {
        return createViews(null, databases, null);
    }

    /**
     * 按种类 + 指定表生成视图
     */
    public ShardingViewResult createViews(Set<DbInitCategory> categories, List<String> tableNames) {
        return createViews(categories, null, tableNames);
    }

    /**
     * 在指定库上，按指定基表名生成视图
     */
    public ShardingViewResult createViews(String database, List<String> tableNames) {
        if (!StringUtils.hasText(database)) {
            throw new IllegalArgumentException("database 不能为空");
        }
        return createViews(null, Collections.singletonList(database.trim()), tableNames);
    }

    /**
     * 统一入口：categories / databases 二选一（databases 优先），tables 为空则自动识别
     */
    public ShardingViewResult createViews(Set<DbInitCategory> categories,
                                          List<String> databases,
                                          List<String> tableNames) {
        return createViewsInternal(resolveDatabases(categories, databases), tableNames);
    }

    private ShardingViewResult createViewsInternal(List<String> databases, List<String> tableNames) {
        long start = System.currentTimeMillis();
        ShardingViewResult result = new ShardingViewResult();
        result.setDatabases(String.join(",", databases));

        if (databases.isEmpty()) {
            result.addError("未解析到任何目标数据库");
            result.setElapsedMs(System.currentTimeMillis() - start);
            return result;
        }

        List<String> normalizedTables = normalizeRequestedTables(tableNames);

        for (String database : databases) {
            try {
                if (!jdbcHelper.databaseExists(database)) {
                    String msg = database + ": 数据库不存在，跳过";
                    log.warn(msg);
                    result.addSkippedView(msg);
                    continue;
                }
                createViewsInDatabase(database, normalizedTables, result);
            } catch (Exception e) {
                String error = database + ": " + e.getMessage();
                log.error(error, e);
                result.addError(error);
            }
        }

        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    private void createViewsInDatabase(String database, List<String> requestedTables,
                                       ShardingViewResult result) throws SQLException {
        Set<String> physicalTables = listBaseTables(database);
        int shardCount = readShardCount(database);
        if (shardCount < 1) {
            shardCount = inferShardCount(physicalTables, requestedTables);
            if (shardCount < 1) {
                String msg = database + ": tbareainfo 无有效分表数，且无法从物理表推断，跳过";
                log.warn(msg);
                result.addSkippedView(msg);
                return;
            }
            log.warn("库 {} tbareainfo 无有效分表数，已从物理表推断为 {}", database, shardCount);
        }
        result.setShardCount(Math.max(result.getShardCount(), shardCount));

        Map<String, List<Integer>> completeGroups =
                sqlBuilder.detectCompleteShardGroups(physicalTables, shardCount);

        Set<String> targets;
        if (requestedTables == null || requestedTables.isEmpty()) {
            targets = new LinkedHashSet<>(completeGroups.keySet());
        } else {
            targets = new LinkedHashSet<>(requestedTables);
        }

        if (targets.isEmpty()) {
            result.addSkippedView(database + ": 未发现可生成的分表组(分表数=" + shardCount + ")");
            return;
        }

        log.info("库 {} 分表数={}，待生成视图 {} 个", database, shardCount, targets.size());

        for (String baseTable : targets) {
            String label = database + "." + baseTable;
            try {
                List<Integer> shards = resolveShards(baseTable, completeGroups, physicalTables, shardCount,
                        requestedTables != null && !requestedTables.isEmpty());
                if (shards == null || shards.isEmpty()) {
                    result.addSkippedView(label + ": 未找到完整分表 1.." + shardCount);
                    continue;
                }
                if (physicalTables.contains(baseTable)) {
                    result.addSkippedView(label + ": 已存在同名实体表，跳过（避免覆盖）");
                    continue;
                }

                String sql = sqlBuilder.buildCreateOrReplaceViewSql(baseTable, shards);
                jdbcHelper.execute(database, sql);
                result.addCreatedView(label + " (" + shards.size() + " 分表)");
                log.info("已生成视图 {}", label);
            } catch (Exception e) {
                String error = label + ": " + e.getMessage();
                log.error(error, e);
                result.addError(error);
            }
        }
    }

    private List<Integer> resolveShards(String baseTable,
                                        Map<String, List<Integer>> completeGroups,
                                        Set<String> physicalTables,
                                        int shardCount,
                                        boolean requestedExplicitly) {
        List<Integer> fromComplete = completeGroups.get(baseTable);
        if (fromComplete != null) {
            return fromComplete;
        }
        if (!requestedExplicitly) {
            return null;
        }
        // 指定表时：尽力收集 1..N 中已存在的分表；缺一不可则返回空
        List<Integer> found = new ArrayList<>(shardCount);
        for (int i = 1; i <= shardCount; i++) {
            if (physicalTables.contains(baseTable + i)) {
                found.add(i);
            }
        }
        if (found.size() == shardCount) {
            return found;
        }
        return null;
    }

    private int readShardCount(String database) throws SQLException {
        try {
            // 取 MAX，兼容多业务类型多行配置；表不存在时返回 0
            return jdbcHelper.queryInt(database,
                    "SELECT IFNULL(MAX(sharding_table_num), 0) FROM tbareainfo WHERE sharding_table_num > 0",
                    0);
        } catch (SQLException e) {
            log.warn("库 {} 读取 tbareainfo 失败: {}", database, e.getMessage());
            return 0;
        }
    }

    /**
     * tbareainfo 无效时，从物理表连续分表数推断（取出现最多的连续长度，至少 2）
     */
    private int inferShardCount(Set<String> physicalTables, List<String> requestedTables) {
        Map<Integer, Integer> lengthFreq = new LinkedHashMap<>();
        Set<String> bases = new LinkedHashSet<>();
        if (requestedTables != null && !requestedTables.isEmpty()) {
            bases.addAll(requestedTables);
        } else {
            for (String tableName : physicalTables) {
                ShardingViewSqlBuilder.ShardSplit split =
                        sqlBuilder.parseCanonicalShardSplit(tableName, Integer.MAX_VALUE);
                if (split != null) {
                    bases.add(split.baseName);
                }
            }
        }
        for (String base : bases) {
            int consecutive = countConsecutiveShards(physicalTables, base);
            if (consecutive >= 2) {
                lengthFreq.merge(consecutive, 1, Integer::sum);
            }
        }
        int bestLength = 0;
        int bestFreq = 0;
        for (Map.Entry<Integer, Integer> entry : lengthFreq.entrySet()) {
            int length = entry.getKey();
            int freq = entry.getValue();
            if (freq > bestFreq || (freq == bestFreq && length > bestLength)) {
                bestFreq = freq;
                bestLength = length;
            }
        }
        return bestLength;
    }

    private int countConsecutiveShards(Set<String> physicalTables, String baseTable) {
        int count = 0;
        while (physicalTables.contains(baseTable + (count + 1))) {
            count++;
        }
        return count;
    }

    private Set<String> listBaseTables(String database) throws SQLException {
        String sql = "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = '" + MysqlJdbcHelper.escapeLiteral(database) + "'"
                + " AND table_type = 'BASE TABLE'";
        List<String> names = jdbcHelper.queryStrings(database, sql);
        Set<String> result = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null) {
                result.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private List<String> normalizeRequestedTables(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tableName : tableNames) {
            String base = sqlBuilder.normalizeBaseTableName(tableName);
            if (StringUtils.hasText(base)) {
                if (!base.matches("[A-Za-z0-9_]+")) {
                    throw new IllegalArgumentException("非法表名: " + tableName);
                }
                normalized.add(base);
            }
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 解析目标库：优先 databases 参数，否则按种类展开去重
     */
    List<String> resolveDatabases(Set<DbInitCategory> categories, List<String> databases) {
        if (databases != null && !databases.isEmpty()) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (String database : databases) {
                if (StringUtils.hasText(database)) {
                    names.add(database.trim());
                }
            }
            return new ArrayList<>(names);
        }

        Set<DbInitCategory> resolved = categories;
        if (resolved == null || resolved.isEmpty()) {
            resolved = properties.getCategorySet();
        }
        List<DbInitTarget> targets = DbInitTarget.buildForCategories(resolved, properties.isIncludeDxfundSingleDb());
        return targets.stream()
                .map(DbInitTarget::getDatabaseName)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 解析逗号分隔表名
     */
    public static List<String> parseCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }
}
