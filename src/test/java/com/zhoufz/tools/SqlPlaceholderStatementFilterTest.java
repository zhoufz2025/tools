package com.zhoufz.tools;

import com.zhoufz.tools.db.SqlPlaceholderStatementFilter;
import com.zhoufz.tools.db.SqlScriptSanitizer;
import com.zhoufz.tools.db.SqlTemplateStatementFilter;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SqlPlaceholderStatementFilterTest {

    private static final String SQL_ROOT = "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/sql";

    @Test
    public void mysqlShareExtParamShouldBeFilledAsOne() {
        String sql = "INSERT INTO tbparam VALUES\n"
                + "    ('000000','T2H0511_UES_SHAREEXTTT','浮动盈亏是否使用分TA表',?,'是','2','1','null~');\n";
        SqlTemplateStatementFilter.FilterResult result = SqlPlaceholderStatementFilter.filter(sql);
        Assert.assertTrue(result.isFiltered());
        Assert.assertEquals(1, result.getKeptStatements());
        Assert.assertEquals(0, result.getSkippedStatements());
        Assert.assertTrue(result.getSql().contains("'1'"));
        Assert.assertFalse(SqlPlaceholderStatementFilter.containsUnboundJdbcPlaceholder(result.getSql()));
    }

    @Test
    public void siteSpecificPlaceholderShouldBeSkipped() {
        String sql = "INSERT INTO TBPARAM VALUES('000000', 'FINA_AMT_FLAG', '扣款日计算标识', ?, ' ', '2', '1', ' ');\n";
        SqlTemplateStatementFilter.FilterResult result = SqlPlaceholderStatementFilter.filter(sql);
        Assert.assertEquals(0, result.getKeptStatements());
        Assert.assertEquals(1, result.getSkippedStatements());
        Assert.assertFalse(result.hasExecutableSql());
    }

    @Test
    public void cronQuestionMarkInsideStringShouldBeKept() {
        String sql = "INSERT INTO tbscheduletrigger VALUES ('1', 'FREE2H0967', '0 0/5 * * * ? ');\n";
        SqlTemplateStatementFilter.FilterResult result = SqlPlaceholderStatementFilter.filter(sql);
        Assert.assertEquals(1, result.getKeptStatements());
        Assert.assertEquals(0, result.getSkippedStatements());
        Assert.assertTrue(result.getSql().contains("0 0/5 * * * ? "));
    }

    @Test
    public void finaTbparamFileShouldBeExecutableAfterFilter() throws Exception {
        Path file = Paths.get(SQL_ROOT, "sql-fina/pub/fina/base/initdata/tbparam.sql");
        String stripped = SqlScriptSanitizer.readAndStripComments(file);
        SqlTemplateStatementFilter.FilterResult result = SqlPlaceholderStatementFilter.filter(stripped);
        Assert.assertFalse(SqlPlaceholderStatementFilter.containsUnboundJdbcPlaceholder(result.getSql()));
        Assert.assertTrue(result.getSql().contains("T2H0511_UES_SHAREEXTTT"));
        Assert.assertTrue(result.getSql().contains("'1'"));
    }
}
