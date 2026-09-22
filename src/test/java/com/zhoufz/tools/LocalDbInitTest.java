package com.zhoufz.tools;

import com.zhoufz.tools.db.DbInitCategory;
import com.zhoufz.tools.db.LocalDbInitResult;
import com.zhoufz.tools.db.LocalDbInitService;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.EnumSet;

/**
 * 本地 MySQL 建库与基线数据初始化测试
 *
 * <p>运行前请在 application.properties 中配置 local.db.password</p>
 *
 * @author zhoufz
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class LocalDbInitTest {

    @Autowired
    private LocalDbInitService localDbInitService;

    /**
     * 仅建库（默认种类：pub,dxfund,dxasset,dxtrust，不含 fina）
     */
    @Test
    @Ignore("会按默认种类建库，只刷理财请跑 initFina")
    public void createDatabases() {
        LocalDbInitResult result = localDbInitService.createDatabases();
        printResult(result);
    }

    /**
     * 仅刷新基金代销相关库
     */
    @Test
    @Ignore("确认密码后手动执行")
    public void createDxfundDatabases() {
        LocalDbInitResult result = localDbInitService.createDatabases(EnumSet.of(DbInitCategory.DXFUND));
        printResult(result);
    }

    /**
     * 仅刷新理财：建库 + DDL + initdata。
     * pub 写入 lcptpub（不 DROP 库，但 schema 会 drop 理财相关表），trans 写入 finatrans1/2。
     */
    @Test
    @Ignore("表结构已刷过时不要再跑；续跑 initdata 请用 initFinaData")
    public void initFina() {
        LocalDbInitResult result = localDbInitService.initAll(EnumSet.of(DbInitCategory.FINA));
        printResult(result);
    }

    /**
     * 仅刷理财 initdata（表结构已成功、中途失败后续跑这个，不要再跑 initFina）
     */
    @Test
    public void initFinaData() {
        LocalDbInitResult result = localDbInitService.initData(EnumSet.of(DbInitCategory.FINA));
        printResult(result);
    }

    /**
     * 全量：DROP 重建 + DDL + initdata（耗时很长，去掉 @Ignore 后单独跑）
     */
    @Test
    @Ignore("全量初始化耗时很长，确认密码后手动执行")
    public void initAll() {
        LocalDbInitResult result = localDbInitService.initAll();
        printResult(result);
    }

    /**
     * 仅表结构（默认种类，不含 fina）
     */
    @Test
    @Ignore("会按默认种类刷 DDL，只刷理财请跑 initFina")
    public void initSchema() {
        LocalDbInitResult result = localDbInitService.initSchema();
        printResult(result);
    }

    /**
     * 仅基础数据（默认种类，不含 fina）
     */
    @Test
    @Ignore("会按默认种类刷 initdata，只刷理财请跑 initFina")
    public void initData() {
        LocalDbInitResult result = localDbInitService.initData();
        printResult(result);
    }

    private void printResult(LocalDbInitResult result) {
        System.out.println("成功: " + result.isSuccess());
        System.out.println("种类: " + result.getSelectedCategories());
        System.out.println("耗时(ms): " + result.getElapsedMs());
        System.out.println("建库: " + result.getCreatedDatabases());
        System.out.println("执行脚本数: " + result.getExecutedScripts().size());
        if (!result.getErrors().isEmpty()) {
            System.out.println("错误:");
            result.getErrors().forEach(System.out::println);
        }
        if (!result.getExecutedScripts().isEmpty()) {
            System.out.println("最后 5 个脚本:");
            int from = Math.max(0, result.getExecutedScripts().size() - 5);
            result.getExecutedScripts().subList(from, result.getExecutedScripts().size())
                    .forEach(System.out::println);
        }
        Assert.assertTrue("初始化失败，请检查 local.db.password 等配置: " + result.getErrors(), result.isSuccess());
    }
}
