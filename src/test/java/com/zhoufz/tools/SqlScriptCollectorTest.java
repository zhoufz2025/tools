package com.zhoufz.tools;

import com.zhoufz.tools.db.SqlScriptCollector;
import com.zhoufz.tools.db.SqlScriptPhase;
import com.zhoufz.tools.db.SqlScriptCollectResult;
import com.zhoufz.tools.db.SqlTemplateStatementFilter;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * initData 收集与模板过滤测试
 */
public class SqlScriptCollectorTest {

    private static final String SQL_ROOT = "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/sql";

    private final SqlScriptCollector collector = new SqlScriptCollector();

    @Test
    public void initDataShouldSkipNonMysqlWorkflowScripts() throws Exception {
        Path moduleRoot = Paths.get(SQL_ROOT, "sql-pub/pub/pub");
        SqlScriptCollectResult result = collector.collectWithSkipped(moduleRoot, SqlScriptPhase.INITDATA);

        for (Path script : result.getScripts()) {
            String path = script.toString().replace('\\', '/').toLowerCase();
            Assert.assertFalse("不应包含 ora 工作流脚本: " + script, path.contains("/workflow/ora/"));
            Assert.assertFalse("不应包含 ob 工作流脚本: " + script, path.contains("/workflow/ob/"));
            Assert.assertFalse("不应包含 pg 工作流脚本: " + script, path.contains("/workflow/pg/"));
        }

        long workflowMysqlCount = result.getScripts().stream()
                .filter(p -> p.toString().replace('\\', '/').contains("/workflow/mysql/"))
                .count();
        Assert.assertEquals("应仅保留 3 个 mysql 工作流脚本", 3, workflowMysqlCount);
        Assert.assertTrue("应记录跳过的非 MySQL 工作流脚本", result.getSkippedScripts().size() >= 24);
    }

    @Test
    public void tbparamShouldNotBeFilteredByTaTemplateFilter() throws Exception {
        Path tbparam = Paths.get(SQL_ROOT, "sql-pub/pub/pub/base/initdata/tbparam.sql");
        Assert.assertFalse(SqlTemplateStatementFilter.containsTaTemplateMarker(tbparam));

        SqlTemplateStatementFilter.FilterResult filterResult = SqlTemplateStatementFilter.filter(tbparam);
        Assert.assertEquals(0, filterResult.getSkippedStatements());
    }

    @Test
    public void dxassetSchemaShouldExecuteTableBeforeIndex() throws Exception {
        Path moduleRoot = Paths.get(SQL_ROOT, "sql-dxasset/pub/dxasset");
        List<Path> scripts = collector.collect(moduleRoot, SqlScriptPhase.SCHEMA);

        int tableIdx = -1;
        int indexIdx = -1;
        for (int i = 0; i < scripts.size(); i++) {
            String name = scripts.get(i).getFileName().toString();
            if (name.equals("001dbstruct_querydxasset_mysql.table.sql")) {
                tableIdx = i;
            }
            if (name.equals("001dbstruct_querydxasset_mysql.index.sql")) {
                indexIdx = i;
            }
        }
        Assert.assertTrue(tableIdx >= 0);
        Assert.assertTrue(indexIdx >= 0);
        Assert.assertTrue("query 表结构应先于索引执行", tableIdx < indexIdx);
    }
}
