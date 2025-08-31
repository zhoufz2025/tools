package com.zhoufz.tools;

import com.zhoufz.tools.service.HundsunServiceImpl;
import org.junit.Test;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */


public class HundsunTest {

    @Test
    public void replaceJavaFile() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        service.replaceJavaFile();
    }

    @Test
    public void getGitSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/";
        service.getGitSourceCode(basePath);
    }

    @Test
    public void getGitHuiSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/git/Sources/";
        service.getGitHuiSourceCode(basePath);
    }

    @Test
    public void getGitCounterSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/git/Sources/";
        service.getGitCounterSourceCode(basePath);
    }
}
