package com.zhoufz.tools;

import com.zhoufz.tools.service.HundsunConfigFileServiceImpl;
import com.zhoufz.tools.service.HundsunServiceImpl;
import org.junit.Test;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

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
        String basePath = "F:\\提交\\Sources\\app";
        service.getGitSourceCode(basePath);
    }
    
    /**
     * 按 projectGit.xlsx（resources/projectGit.xlsx）批量 clone 银行个性化工程仓库。
     * Excel 第1列：项目编号；第2列：Git 地址；下载到 basePath 下以仓库名命名的子目录。
     */
    @Test
    public void getProjectGitSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "F:\\ProjectSource";
        service.getProjectGitSourceCode(basePath);
    }

    @Test
    public void getGitHuiSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "F:\\提交\\Sources";
        service.getGitHuiSourceCode(basePath);
    }

    @Test
    public void getGitCounterSourceCode() {
        HundsunServiceImpl service = new HundsunServiceImpl();
        String basePath = "F:\\提交\\Sources";
        service.getGitCounterSourceCode(basePath);
    }

    @Test
    public void getBackUpConfigFile() throws IOException {
        String sourceRoot = "F:\\AppSource\\Sources\\app\\";
        String targetRoot = "F:\\AppSource\\backup20260809";
        HundsunConfigFileServiceImpl fileService = new HundsunConfigFileServiceImpl();
        fileService.backupFiles(sourceRoot,targetRoot);

    }
    
    @Test
    public void getRestoreFiles() throws IOException {
        String sourceRoot = "F:\\AppSource\\Sources";
        String targetRoot = "F:\\AppSource\\backup20260720";
        HundsunConfigFileServiceImpl fileService = new HundsunConfigFileServiceImpl();
        fileService.restoreFiles(sourceRoot,targetRoot);
        
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
