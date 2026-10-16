package com.zhoufz.tools;

import com.zhoufz.tools.db.LocalDbInitProperties;
import com.zhoufz.tools.db.ShardingViewResult;
import com.zhoufz.tools.db.ShardingViewService;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;

/**
 * 分表视图生成集成测试（连接本地 MySQL）
 *
 * <p>运行前请确认 application.properties 中 local.db 连接可用</p>
 *
 * @author zhoufz
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ShardingViewServiceTest {

    @Autowired
    private ShardingViewService shardingViewService;

    @Autowired
    private LocalDbInitProperties properties;

    /**
     * 仅生成 lcptpub.tbclient 视图
     */
    @Test
    public void createTbclientView() {
        ShardingViewResult result = shardingViewService.createViews(
                "lcptpub", Collections.singletonList("tbclientseller"));
        printResult(result);
        Assert.assertTrue("生成失败: " + result.getErrors(), result.isSuccess());
        Assert.assertFalse(result.getCreatedViews().isEmpty());
    }

    /**
     * 重复执行应成功（CREATE OR REPLACE）
     */
    @Test
    public void createTbclientViewIdempotent() {
        ShardingViewResult first = shardingViewService.createViews(
                "lcptpub", Collections.singletonList("tbclient"));
        ShardingViewResult second = shardingViewService.createViews(
                "lcptpub", Collections.singletonList("tbclient"));
        printResult(second);
        Assert.assertTrue(first.isSuccess());
        Assert.assertTrue(second.isSuccess());
    }

    /**
     * 自动识别 lcptpub 全部完整分表组并建视图（数量较多，默认忽略）
     */
    @Test
    @Ignore("会为 lcptpub 全部完整分表组建视图，确认后手动执行")
    public void createAllViewsOnLcptpub() {
        ShardingViewResult result = shardingViewService.createViewsForDatabases(
                Collections.singletonList("lcptpub"));
        printResult(result);
        Assert.assertTrue(result.isSuccess());
    }

    /**
     * 按配置种类批量生成（默认忽略）
     */
    @Test
    @Ignore("按默认种类多库生成，确认后手动执行")
    public void createAllViewsByDefaultCategories() {
        ShardingViewResult result = shardingViewService.createViews();
        printResult(result);
        Assert.assertTrue(result.isSuccess());
    }

    /**
     * 同时指定多表
     */
    @Test
    public void createMultipleSpecifiedViews() {
        ShardingViewResult result = shardingViewService.createViews(
                "lcptpub", Arrays.asList("tbclient", "tbclientadd", "tbbankacc"));
        printResult(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(3, result.getCreatedViews().size());
    }

    /**
     * 基名含 00 的分表：tbdxfundshare001..0016 → 视图 tbdxfundshare00
     */
    @Test
    public void createZeroPrefixedShareView() {
        ShardingViewResult result = shardingViewService.createViews(
                "dxfundtrans1", Collections.singletonList("tbdxfundshare00"));
        printResult(result);
        Assert.assertTrue("生成失败: " + result.getErrors(), result.isSuccess());
        Assert.assertFalse(result.getCreatedViews().isEmpty());
        Assert.assertTrue(result.getCreatedViews().get(0).contains("tbdxfundshare00"));
    }

    /**
     * 指定零填充物理表名时，应归一成基名 tbdxfundshare00
     */
    @Test
    public void createZeroPrefixedShareViewByPhysicalName() {
        ShardingViewResult result = shardingViewService.createViews(
                "dxfundtrans1", Collections.singletonList("tbdxfundshare001"));
        printResult(result);
        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(result.getCreatedViews().get(0).contains("tbdxfundshare00"));
    }

    private void printResult(ShardingViewResult result) {
        System.out.println("成功: " + result.isSuccess());
        System.out.println("库: " + result.getDatabases());
        System.out.println("分表数: " + result.getShardCount());
        System.out.println("耗时(ms): " + result.getElapsedMs());
        System.out.println("mysql: " + properties.getHost() + ":" + properties.getPort());
        System.out.println("已创建/更新: " + result.getCreatedViews());
        if (!result.getSkippedViews().isEmpty()) {
            System.out.println("跳过:");
            result.getSkippedViews().forEach(System.out::println);
        }
        if (!result.getErrors().isEmpty()) {
            System.out.println("错误:");
            result.getErrors().forEach(System.out::println);
        }
    }
}
