package com.zhoufz.tools;

import com.zhoufz.tools.service.HundsunConfigFileServiceImpl;
import com.zhoufz.tools.service.HundsunServiceImpl;
import org.junit.Test;

import java.io.IOException;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */


public class HundsunTest {

    @Test
    public void gitReplaceJavaFile() {
        // git checkout hotfix/IFMS6.0V202405.08.037
    
        HundsunServiceImpl service = new HundsunServiceImpl();
        service.gitReplaceSqlFile("gitReplaceFile.txt");
    }
    
    @Test
    public void svnReplaceJavaFile() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        service.svnReplaceSqlFile("svnReplaceFile.txt");
    }

    @Test
    public void getGitSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/commitGit/Sources/app";
        service.getGitSourceCode(basePath);
    }
    
    /**
     * 按 projectGit.xlsx（resources/projectGit.xlsx）批量 clone 银行个性化工程仓库。
     * Excel 第1列：项目编号；第2列：Git 地址；下载到 basePath 下以仓库名命名的子目录。
     */
    @Test
    public void getProjectGitSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/project";
        service.getProjectGitSourceCode(basePath);
    }

    @Test
    public void getGitHuiSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/commitGit/Sources";
        service.getGitHuiSourceCode(basePath);
    }

    @Test
    public void getGitCounterSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "/Users/zhoufz/hundsun/lcpt60/commitGit/Sources";
        service.getGitCounterSourceCode(basePath);
    }

    @Test
    public void getBackUpConfigFile() throws IOException {
        String sourceRoot = "/Users/zhoufz/hundsun/lcpt60/project/lcpt-qhyh_dxfund";
        String targetRoot = "/Users/zhoufz/hundsun/lcpt60/config/project/lcpt-qhyh_dxfund/20260922";
        HundsunConfigFileServiceImpl fileService = new HundsunConfigFileServiceImpl();
        fileService.backupFiles(sourceRoot,targetRoot);

    }
    
    @Test
    public void getRestoreFiles() throws IOException {
        // 快照目录（config/app、config/20260828）与工程 app 对齐，不要把 sourceRoot 再下探到 lcpt-server
        // 覆盖 config/app → 工程：sourceRoot=.../git/Sources/app，targetRoot=.../config/app
        // 错误示例：sourceRoot=.../app/lcpt-server + targetRoot=.../config → 多出 lcpt-server/app/
        String sourceRoot = "/Users/zhoufz/hundsun/lcpt60/git/Sources/app";
        String targetRoot = "/Users/zhoufz/hundsun/lcpt60/config/20260828";
        HundsunConfigFileServiceImpl fileService = new HundsunConfigFileServiceImpl();
        fileService.restoreFiles(sourceRoot,targetRoot);
        
    }

    /**
     * 删除工程下所有 Maven target 目录（编译产物）
     */
    @Test
    public void deleteTargetDirs() throws IOException {
        String sourceRoot = "/Users/zhoufz/hundsun/lcpt60/git/Sources/app";
        HundsunConfigFileServiceImpl fileService = new HundsunConfigFileServiceImpl();
        fileService.deleteTargetDirs(sourceRoot);
    }

    /**
     * 按 backUpFile.txt 列表备份指定文件到备份目录
     */
    @Test
    public void backUpSpecifiedFiles() throws IOException {
        String backupRoot = "F:\\AppSource\\backup-specified";
        HundsunConfigFileServiceImpl fileService = new HundsunConfigFileServiceImpl();
        fileService.backupSpecifiedFiles(backupRoot);
    }

    /**
     * 按 backUpFile.txt 列表从备份目录还原指定文件
     */
    @Test
    public void restoreSpecifiedFiles() throws IOException {
        String backupRoot = "F:\\AppSource\\backup-specified";
        HundsunConfigFileServiceImpl fileService = new HundsunConfigFileServiceImpl();
        fileService.restoreSpecifiedFiles(backupRoot);
    }

}
