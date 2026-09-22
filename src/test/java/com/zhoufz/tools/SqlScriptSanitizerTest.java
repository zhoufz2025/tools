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

    @Test
    public void rewriteInsertToIgnore_convertsInsertInto() {
        String sql = "INSERT INTO tsys_subtrans VALUES ('a','b');\ninsert into tbparam values (1);";
        String rewritten = SqlScriptSanitizer.rewriteInsertToIgnore(sql);
        Assert.assertEquals("INSERT IGNORE INTO tsys_subtrans VALUES ('a','b');\nINSERT IGNORE INTO tbparam values (1);",
                rewritten);
    }

    @Test
    public void rewriteInsertToIgnore_keepsExistingIgnore() {
        String sql = "INSERT IGNORE INTO tbparam VALUES (1);";
        Assert.assertEquals("INSERT IGNORE INTO tbparam VALUES (1);",
                SqlScriptSanitizer.rewriteInsertToIgnore(sql));
    }

    @Test
    public void gbkInitdataShouldBeReadableWithoutMalformedException() throws Exception {
        Path sqlFile = Paths.get(
                "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/sql/sql-fina/pub/fina/base/initdata/tbparam需要确认后执行的参数.sql");
        if (!Files.exists(sqlFile)) {
            return;
        }
        Assert.assertFalse(SqlScriptSanitizer.containsDelimiter(sqlFile));
        String content = SqlScriptSanitizer.readSqlContent(sqlFile);
        Assert.assertTrue(content.contains("FINA_AMT_FLAG"));
        Assert.assertTrue(content.contains("扣款日"));
    }
}
