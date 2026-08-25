package com.zhoufz.tools;

import com.zhoufz.tools.db.SqlScriptSanitizer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SqlScriptSanitizerTest {

    @Test
    public void stripComments_removesOrphanBlockCommentEnd() {
        String sql = "/* outer\n"
                + "/*insert into t values(1);*/\n"
                + "insert into t values(2);\n"
                + " */\n"
                + "update t set a=1;\n";

        String stripped = SqlScriptSanitizer.stripComments(sql);

        Assert.assertFalse(stripped.contains("*/"));
        Assert.assertTrue(stripped.contains("insert into t values(2);"));
        Assert.assertTrue(stripped.contains("update t set a=1;"));
    }

    @Test
    public void stripComments_preservesStringWithCommentLikeText() {
        String sql = "insert into t values('/* not a comment */');\n";

        String stripped = SqlScriptSanitizer.stripComments(sql);

        Assert.assertEquals("insert into t values('/* not a comment */');\n", stripped);
    }

    @Test
    public void stripComments_removesLineComment() {
        String sql = "select 1; -- comment\nselect 2;\n";

        String stripped = SqlScriptSanitizer.stripComments(sql);

        Assert.assertTrue(stripped.contains("select 1;"));
        Assert.assertTrue(stripped.contains("select 2;"));
        Assert.assertFalse(stripped.contains("comment"));
    }

    @Test
    public void stripComments_dxassetSubtransFile() throws Exception {
        Path sqlFile = Paths.get(
                "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/sql/sql-dxasset/pub/dxasset/base/initdata/ifmmanage/ifmmanage_subtrans_dxasset.sql");
        if (!Files.exists(sqlFile)) {
            return;
        }

        String stripped = SqlScriptSanitizer.readAndStripComments(sqlFile);
        Assert.assertFalse("孤立 */ 应被移除", stripped.contains("*/"));
        Assert.assertTrue(stripped.contains("update tsys_subtrans"));
    }
}
