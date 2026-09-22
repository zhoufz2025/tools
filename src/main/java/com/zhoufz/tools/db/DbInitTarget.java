package com.zhoufz.tools.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单个数据库及其对应的 SQL 模块目录（相对 sqlRoot）
 * <p>
 * 规则：
 * <ul>
 *   <li>所有 pub 目录脚本导入 {@link #PUB_DATABASE}</li>
 *   <li>各子系统 trans 目录脚本导入对应 trans 库</li>
 * </ul>
 *
 * @author zhoufz
 */
public class DbInitTarget {

    /** 公共库：承载 sql-pub 及基金/资管/信托/理财 pub 脚本 */
    public static final String PUB_DATABASE = "lcptpub";

    private static final String PUB_SQL_PUB = "sql-pub/pub/pub";

    private static final String PUB_SQL_DXFUND = "sql-dxfund/pub/dxfund";

    private static final String PUB_SQL_DXASSET = "sql-dxasset/pub/dxasset";

    private static final String PUB_SQL_DXTRUST = "sql-dxtrust/pub/dxtrust";

    private static final String PUB_SQL_FINA = "sql-fina/pub/fina";

    private static final String TRANS_SQL_DXFUND = "sql-dxfund/trans/dxfund";

    private static final String TRANS_SQL_DXASSET = "sql-dxasset/trans/dxasset";

    private static final String TRANS_SQL_DXTRUST = "sql-dxtrust/trans/dxtrust";

    private static final String TRANS_SQL_FINA = "sql-fina/trans/fina";

    private final String databaseName;

    private final List<String> sqlModulePaths;

    /** recreate 时是否 DROP 该库 */
    private final boolean dropOnRecreate;

    public DbInitTarget(String databaseName, List<String> sqlModulePaths, boolean dropOnRecreate) {
        this.databaseName = databaseName;
        this.sqlModulePaths = sqlModulePaths;
        this.dropOnRecreate = dropOnRecreate;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public List<String> getSqlModulePaths() {
        return sqlModulePaths;
    }

    public boolean isDropOnRecreate() {
        return dropOnRecreate;
    }

    public boolean isPubDatabase() {
        return PUB_DATABASE.equals(databaseName);
    }

    /**
     * 按选定种类生成本次要刷新的库清单
     */
    public static List<DbInitTarget> buildForCategories(Set<DbInitCategory> categories, boolean includeDxfundSingleDb) {
        if (categories == null || categories.isEmpty()) {
            categories = DbInitCategory.all();
        }

        Map<String, TargetBuilder> builderMap = new LinkedHashMap<>();

        if (categories.contains(DbInitCategory.PUB)) {
            appendPubModule(builderMap, PUB_SQL_PUB, true);
        }
        if (categories.contains(DbInitCategory.DXFUND)) {
            appendPubModule(builderMap, PUB_SQL_DXFUND, false);
            append(builderMap, "dxfundtrans1", TRANS_SQL_DXFUND, true);
            append(builderMap, "dxfundtrans2", TRANS_SQL_DXFUND, true);
            if (includeDxfundSingleDb) {
                append(builderMap, "dxfundsingledb", PUB_SQL_DXFUND, true);
                append(builderMap, "dxfundsingledb", TRANS_SQL_DXFUND, false);
            }
        }
        if (categories.contains(DbInitCategory.DXASSET)) {
            appendPubModule(builderMap, PUB_SQL_DXASSET, false);
            append(builderMap, "dxassettrans1", TRANS_SQL_DXASSET, true);
            append(builderMap, "dxassettrans2", TRANS_SQL_DXASSET, true);
        }
        if (categories.contains(DbInitCategory.DXTRUST)) {
            appendPubModule(builderMap, PUB_SQL_DXTRUST, false);
            append(builderMap, "dxtrusttrans1", TRANS_SQL_DXTRUST, true);
            append(builderMap, "dxtrusttrans2", TRANS_SQL_DXTRUST, true);
        }
        if (categories.contains(DbInitCategory.FINA)) {
            appendPubModule(builderMap, PUB_SQL_FINA, false);
            append(builderMap, "finatrans1", TRANS_SQL_FINA, true);
            append(builderMap, "finatrans2", TRANS_SQL_FINA, true);
        }

        List<DbInitTarget> targets = new ArrayList<>();
        for (TargetBuilder builder : builderMap.values()) {
            targets.add(builder.build());
        }
        return targets;
    }

    /**
     * pub 目录脚本统一刷入 lcptpub
     */
    private static void appendPubModule(Map<String, TargetBuilder> builderMap, String pubModulePath,
                                        boolean dropLcptpubOnRecreate) {
        append(builderMap, PUB_DATABASE, pubModulePath, dropLcptpubOnRecreate);
    }

    /**
     * 所有 pub 模块路径（用于文档/测试）
     */
    public static List<String> allPubModulePaths() {
        return Collections.unmodifiableList(Arrays.asList(
                PUB_SQL_PUB, PUB_SQL_DXFUND, PUB_SQL_DXASSET, PUB_SQL_DXTRUST, PUB_SQL_FINA
        ));
    }

    /**
     * 兼容旧逻辑：全部种类
     */
    public static List<DbInitTarget> standardTargets(boolean includeDxfundSingleDb) {
        return buildForCategories(EnumSet.allOf(DbInitCategory.class), includeDxfundSingleDb);
    }

    private static void append(Map<String, TargetBuilder> builderMap, String databaseName,
                               String sqlModulePath, boolean dropOnRecreate) {
        TargetBuilder builder = builderMap.computeIfAbsent(databaseName, TargetBuilder::new);
        builder.addModule(sqlModulePath);
        if (dropOnRecreate) {
            builder.markDropOnRecreate();
        }
    }

    private static class TargetBuilder {

        private final String databaseName;

        private final List<String> sqlModulePaths = new ArrayList<>();

        private boolean dropOnRecreate;

        private TargetBuilder(String databaseName) {
            this.databaseName = databaseName;
        }

        private void addModule(String sqlModulePath) {
            if (!sqlModulePaths.contains(sqlModulePath)) {
                sqlModulePaths.add(sqlModulePath);
            }
        }

        private void markDropOnRecreate() {
            this.dropOnRecreate = true;
        }

        private DbInitTarget build() {
            return new DbInitTarget(databaseName, Collections.unmodifiableList(new ArrayList<>(sqlModulePaths)),
                    dropOnRecreate);
        }
    }
}
