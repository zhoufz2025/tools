package com.zhoufz.tools;

import com.zhoufz.tools.service.HundsunServiceImpl;
import org.junit.Test;

/**
 * Git批量更新测试
 * @author zhoufz
 * 日期 2025/11/29
 */
public class GitUpdateTest {

    @Test
    public void getAllRepositories() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/git/Sources/";
        String outputPath = "/Users/zhoufz/hundsun/tools/src/main/resources/gitrep.txt";
        service.generateGitRepositoryMapping(basePath, outputPath);
    }

    @Test
    public void updateAllRepositories() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        // 替换为您实际的基础路径
        String basePath = "/Users/zhoufz/hundsun/lcpt60/git/Sources/";
        service.updateAllGitRepositories(basePath);
    }

    @Test
    public void updateByRepositories() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/git/Sources/";
        String outputPath = "/Users/zhoufz/hundsun/tools/src/main/resources/gitrep.txt";
        service.generateGitRepositoryMapping(basePath, outputPath);
    }
}

