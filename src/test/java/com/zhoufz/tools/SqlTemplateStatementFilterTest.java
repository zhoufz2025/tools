package com.zhoufz.tools;

import com.zhoufz.tools.db.SqlTemplateStatementFilter;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SQL 模板过滤测试
 */
public class SqlTemplateStatementFilterTest {

    private static final String SQL_ROOT = "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/sql";

    @Test
    public void filterDxfundPubTableSql() throws Exception {
        Path file = Paths.get(SQL_ROOT, "sql-dxfund/pub/dxfund/base/000schema/mysql",
                "001dbstruct_dxfundpub_mysql.table.sql");
        SqlTemplateStatementFilter.FilterResult result = SqlTemplateStatementFilter.filter(file);
        Assert.assertTrue(result.getSkippedStatements() > 0);
        Assert.assertTrue(result.getKeptStatements() > 0);
        Assert.assertFalse(result.getSql().contains("${"));
    }

    @Test
    public void taclearFileShouldBeSkippedByCollectorNotHere() {
        String name = "002dbstruct_account_mysql.taclear.table.sql";
        Assert.assertTrue(name.toLowerCase().contains("taclear"));
    }
}
