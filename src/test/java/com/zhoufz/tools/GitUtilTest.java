package com.zhoufz.tools;

import com.zhoufz.tools.util.GitUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

/**
 * Git 命令解析与 clone 工作目录创建。
 */
public class GitUtilTest {

    @Test
    public void resolveGitExecutableCanRunVersion() throws Exception {
        String git = GitUtil.resolveGitExecutable();
        Assert.assertNotNull(git);
        Process process = new ProcessBuilder(git, "--version").start();
        Assert.assertEquals(0, process.waitFor());
    }

    @Test
    public void getCloneCreatesMissingWorkDirectory() throws Exception {
        File tmp = Files.createTempDirectory("gitutil-clone").toFile();
        File workDir = new File(tmp, "lcpt-front");
        Assert.assertFalse(workDir.exists());
        GitUtil.getClone("not-a-valid-git-url", workDir.getAbsolutePath());
        Assert.assertTrue(workDir.isDirectory());
    }

    @Test
    public void getSubModuleCloneWithoutGitRootDoesNotThrow() throws Exception {
        File tmp = Files.createTempDirectory("gitutil-sub").toFile();
        File bizDir = new File(tmp, "biz");
        GitUtil.getSubModuleClone("https://example.com/x.git", bizDir.getAbsolutePath(), "console-fina-vue");
        Assert.assertFalse(new File(bizDir, "console-fina-vue").exists());
    }
}
