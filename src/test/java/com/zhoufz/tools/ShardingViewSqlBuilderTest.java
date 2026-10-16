package com.zhoufz.tools;

import com.zhoufz.tools.db.ShardingViewSqlBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分表视图 SQL 构建单元测试
 *
 * @author zhoufz
 */
public class ShardingViewSqlBuilderTest {

    private final ShardingViewSqlBuilder builder = new ShardingViewSqlBuilder();

    @Test
    public void shouldDetectCompleteShardGroups() {
        Set<String> tables = new LinkedHashSet<>();
        for (int i = 1; i <= 16; i++) {
            tables.add("tbclient" + i);
            tables.add("tbclientadd" + i);
        }
        tables.add("tbclientnomap2");
        tables.add("tbaccreq00");
        tables.add("tbparam");

        Map<String, List<Integer>> groups = builder.detectCompleteShardGroups(tables, 16);
        Assert.assertEquals(2, groups.size());
        Assert.assertTrue(groups.containsKey("tbclient"));
        Assert.assertTrue(groups.containsKey("tbclientadd"));
        Assert.assertFalse(groups.containsKey("tbclientnomap"));
        Assert.assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                groups.get("tbclient"));
    }

    @Test
    public void shouldDetectBothUnpaddedAndZeroPrefixedBases() {
        Set<String> tables = new LinkedHashSet<>();
        for (int i = 1; i <= 16; i++) {
            tables.add("tbdxfundshare" + i);
            tables.add("tbdxfundshare00" + i);
        }
        Map<String, List<Integer>> groups = builder.detectCompleteShardGroups(tables, 16);
        Assert.assertEquals(2, groups.size());
        Assert.assertTrue(groups.containsKey("tbdxfundshare"));
        Assert.assertTrue(groups.containsKey("tbdxfundshare00"));
        Assert.assertEquals(16, groups.get("tbdxfundshare").size());
        Assert.assertEquals(16, groups.get("tbdxfundshare00").size());
    }

    @Test
    public void shouldNormalizeBaseTableName() {
        Assert.assertEquals("tbclient", builder.normalizeBaseTableName("tbclient"));
        Assert.assertEquals("tbclient", builder.normalizeBaseTableName("tbclient16"));
        Assert.assertEquals("tbclient", builder.normalizeBaseTableName(" TBCLIENT1 "));
        Assert.assertEquals("tbdxfundshare00", builder.normalizeBaseTableName("tbdxfundshare001"));
        Assert.assertEquals("tbdxfundshare00", builder.normalizeBaseTableName("tbdxfundshare0016"));
        Assert.assertEquals("tbdxfundshare00", builder.normalizeBaseTableName("tbdxfundshare00"));
    }

    @Test
    public void shouldBuildCreateOrReplaceViewSqlForZeroPrefixedBase() {
        String sql = builder.buildCreateOrReplaceViewSql("tbdxfundshare00", Arrays.asList(1, 2, 10));
        Assert.assertTrue(sql.startsWith("CREATE OR REPLACE VIEW `tbdxfundshare00` AS"));
        Assert.assertTrue(sql.contains("from `tbdxfundshare001` t"));
        Assert.assertTrue(sql.contains("from `tbdxfundshare002` t"));
        Assert.assertTrue(sql.contains("from `tbdxfundshare0010` t"));
        Assert.assertTrue(sql.contains("select 10 as o_table_num"));
    }

    @Test
    public void shouldBuildCreateOrReplaceViewSql() {
        String sql = builder.buildCreateOrReplaceViewSql("tbclient", Arrays.asList(1, 2, 3));
        Assert.assertTrue(sql.startsWith("CREATE OR REPLACE VIEW `tbclient` AS"));
        Assert.assertTrue(sql.contains("select 1 as o_table_num, t.* from `tbclient1` t"));
        Assert.assertTrue(sql.contains("union all"));
        Assert.assertTrue(sql.contains("select 3 as o_table_num, t.* from `tbclient3` t"));
        Assert.assertTrue(sql.trim().endsWith(") t"));
    }

    @Test
    public void incompleteGroupShouldBeIgnored() {
        Set<String> tables = new LinkedHashSet<>();
        for (int i = 1; i <= 15; i++) {
            tables.add("tbclient" + i);
        }
        Map<String, List<Integer>> groups = builder.detectCompleteShardGroups(tables, 16);
        Assert.assertTrue(groups.isEmpty());
    }
}
