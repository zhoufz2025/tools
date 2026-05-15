package com.zhoufz.tools;

import com.zhoufz.tools.service.HundsunServiceImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * Git批量更新测试
 * @author zhoufz
 * 日期 2025/11/29
 */
public class GitUpdateTest {

    /** 与 {@code src/main/resources/gitrep.txt} 中每行左侧键名一致，例如 lcpt-dxfund */
    private static final String TARGET_REPO_KEY = "lcpt-dxfund";

    /** 要切换到的分支或标签名，例如 IFMS6.0V202607.00.005 */
    private static final String TARGET_VERSION = "IFMS6.0V202607.00.005";

    private static final String GITREP_TXT =
            "/Users/zhoufz/hundsun/tools/src/main/resources/gitrep.txt";

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

    /**
     * 根据 gitrep.txt 仅对指定键名的仓库执行 {@code git pull}（逻辑见 {@link HundsunServiceImpl#updateGitRepositoryFromGitrep}）。
     */
    @Test
    public void updateSpecifiedGitRepositoryFromGitrep() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        boolean ok = service.updateGitRepositoryFromGitrep(GITREP_TXT, TARGET_REPO_KEY);
        Assert.assertTrue("更新失败，请检查仓库路径与网络：" + TARGET_REPO_KEY, ok);
    }

    /**
     * 根据 gitrep.txt 将指定键名的仓库切换到指定版本（分支/标签），例如 IFMS6.0V202607.00.005。
     */
    @Test
    public void checkoutSpecifiedGitRepositoryToVersionFromGitrep() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        boolean ok = service.checkoutGitRepositoryFromGitrep(GITREP_TXT, TARGET_REPO_KEY, TARGET_VERSION);
        Assert.assertTrue("切换到 " + TARGET_VERSION + " 失败：" + TARGET_REPO_KEY, ok);
    }
}
