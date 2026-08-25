package com.zhoufz.tools;

import com.zhoufz.tools.db.DbInitCategory;
import com.zhoufz.tools.db.DbInitTarget;
import com.zhoufz.tools.db.SchemaScriptType;
import com.zhoufz.tools.db.SqlScriptCollector;
import com.zhoufz.tools.db.SqlScriptPhase;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;

/**
 * 库映射与 DDL 顺序规则测试
 */
public class DbInitTargetTest {

    private static final String SQL_ROOT = "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/sql";

    private final SqlScriptCollector collector = new SqlScriptCollector();

    @Test
    public void allPubModulesShouldTargetLcptpub() {
        List<DbInitTarget> targets = DbInitTarget.buildForCategories(EnumSet.allOf(DbInitCategory.class), false);
        DbInitTarget lcptpub = targets.stream()
                .filter(DbInitTarget::isPubDatabase)
                .findFirst()
                .orElse(null);
        Assert.assertNotNull(lcptpub);
        Assert.assertEquals(DbInitTarget.PUB_DATABASE, lcptpub.getDatabaseName());
        Assert.assertEquals(DbInitTarget.allPubModulePaths(), lcptpub.getSqlModulePaths());
    }

    @Test
    public void allModulesShouldExecuteTableBeforeIndex() throws Exception {
        String[] modules = {
                "sql-pub/pub/pub",
                "sql-dxfund/pub/dxfund",
                "sql-dxfund/trans/dxfund",
                "sql-dxasset/pub/dxasset",
                "sql-dxasset/trans/dxasset",
                "sql-dxtrust/pub/dxtrust",
                "sql-dxtrust/trans/dxtrust"
        };
        for (String module : modules) {
            Path moduleRoot = Paths.get(SQL_ROOT, module);
            if (!moduleRoot.toFile().isDirectory()) {
                continue;
            }
            List<Path> scripts = collector.collect(moduleRoot, SqlScriptPhase.SCHEMA);
            assertTableBeforeIndex(module, scripts);
        }
    }

    private void assertTableBeforeIndex(String module, List<Path> scripts) {
        boolean indexStarted = false;
        for (Path script : scripts) {
            SchemaScriptType type = SchemaScriptType.fromFileName(script);
            if (type == SchemaScriptType.INDEX) {
                indexStarted = true;
            }
            if (type == SchemaScriptType.TABLE) {
                Assert.assertFalse("模块 " + module + " 中建表脚本不能出现在索引脚本之后: " + script.getFileName(),
                        indexStarted);
            }
        }
    }
}
