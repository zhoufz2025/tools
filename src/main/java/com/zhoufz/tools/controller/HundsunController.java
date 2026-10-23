package com.zhoufz.tools.controller;

import com.zhoufz.tools.dto.Result;
import com.zhoufz.tools.service.HundsunConfigFileServiceImpl;
import com.zhoufz.tools.service.HundsunServiceImpl;
import com.zhoufz.tools.util.GitUtil;
import com.zhoufz.tools.util.SvnUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */
@RestController
public class HundsunController {

    @Autowired
    private HundsunServiceImpl hundsunService;

    @Autowired
    private HundsunConfigFileServiceImpl configFileService;

    @RequestMapping("/hundsun/replace")
    public void replaceFile() {

    }

    /**
     * 批量更新仓库（Git / SVN / 两者）
     *
     * @param basePath 基础路径，例如：/Users/zhoufz/hundsun/lcpt60/git/Sources/
     * @param vcsType  git | svn | all，默认 git（兼容旧调用）
     * @return JSON格式的响应
     */
    @RequestMapping("/hundsun/git/updateAll")
    public Result<String> updateAllRepositories(
            @RequestParam("basePath") String basePath,
            @RequestParam(value = "vcsType", required = false, defaultValue = "git") String vcsType) {
        try {
            String type = vcsType == null ? "git" : vcsType.trim().toLowerCase();
            if ("svn".equals(type)) {
                hundsunService.updateAllSvnRepositories(basePath);
            } else if ("all".equals(type) || "git+svn".equals(type) || "both".equals(type)) {
                hundsunService.updateAllGitAndSvnRepositories(basePath);
            } else {
                hundsunService.updateAllGitRepositories(basePath);
            }
            return Result.success("批量更新完成（" + type + "），请查看控制台日志获取详细信息");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("批量更新失败：" + e.getMessage());
        }
    }

    /**
     * 查询基础路径下各仓库当前分支。
     *
     * @param vcsType git | svn | all，默认 git
     */
    @RequestMapping("/hundsun/git/branches")
    public Result<List<Map<String, String>>> listBranches(
            @RequestParam("basePath") String basePath,
            @RequestParam(value = "vcsType", required = false, defaultValue = "git") String vcsType) {
        try {
            List<Map<String, String>> rows = hundsunService.listCurrentBranches(basePath, vcsType);
            return Result.success("共 " + rows.size() + " 个仓库", rows);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("查询分支失败：" + e.getMessage());
        }
    }

    /**
     * 切换单个仓库分支。
     *
     * @param repoPath 仓库根目录
     * @param ref      分支/标签；SVN 为 URL 或 {@code ^/branches/xxx}
     * @param vcsType  git | svn | auto，默认 auto
     */
    @RequestMapping("/hundsun/git/checkoutOne")
    public Result<Map<String, String>> checkoutOneRepository(
            @RequestParam("repoPath") String repoPath,
            @RequestParam("ref") String ref,
            @RequestParam(value = "vcsType", required = false, defaultValue = "auto") String vcsType) {
        try {
            boolean ok = hundsunService.checkoutOneRepository(repoPath, ref, vcsType);
            String resolvedType = resolveRepoType(repoPath, vcsType);
            Map<String, String> data = new HashMap<>();
            data.put("repoPath", repoPath);
            data.put("ref", ref);
            data.put("vcsType", resolvedType);
            data.put("branch", "svn".equals(resolvedType)
                    ? SvnUtil.currentBranch(repoPath)
                    : GitUtil.currentBranch(repoPath));
            if (ok) {
                return Result.success("切换成功：" + data.get("branch"), data);
            }
            Result<Map<String, String>> error = Result.error("切换失败，请查看控制台日志");
            error.setData(data);
            return error;
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("切换失败：" + e.getMessage());
        }
    }

    private String resolveRepoType(String repoPath, String vcsType) {
        if (vcsType != null) {
            String type = vcsType.trim().toLowerCase();
            if ("git".equals(type) || "svn".equals(type)) {
                return type;
            }
        }
        if (GitUtil.isValidGitRepository(repoPath)) {
            return "git";
        }
        if (SvnUtil.isValidSvnWorkingCopy(repoPath)) {
            return "svn";
        }
        return "unknown";
    }

    /**
     * 批量切换分支。Git 走 {@code git checkout}，SVN 走 {@code svn switch}。
     *
     * @param ref 分支/标签；SVN 请填 URL 或 {@code ^/branches/xxx}
     */
    @RequestMapping("/hundsun/git/checkoutAll")
    public Result<String> checkoutAllRepositories(
            @RequestParam("basePath") String basePath,
            @RequestParam("ref") String ref,
            @RequestParam(value = "vcsType", required = false, defaultValue = "git") String vcsType) {
        try {
            String type = vcsType == null ? "git" : vcsType.trim().toLowerCase();
            if ("svn".equals(type)) {
                hundsunService.switchAllSvnRepositories(basePath, ref);
            } else if ("all".equals(type) || "git+svn".equals(type) || "both".equals(type)) {
                hundsunService.checkoutAllGitAndSvnRepositories(basePath, ref);
            } else {
                hundsunService.checkoutAllGitRepositories(basePath, ref);
            }
            return Result.success("批量切换完成（" + type + " → " + ref + "），请查看控制台日志");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("批量切换失败：" + e.getMessage());
        }
    }

    /**
     * 按配置文件做代码替换（大包开发后提交到小包）。
     * Git 对应 gitReplaceFile 格式，SVN 对应 svnReplaceFile 格式。
     * filePath 为本机绝对路径，或 classpath 资源名。
     */
    @RequestMapping("/hundsun/code/replace")
    public Result<String> replaceCode(
            @RequestParam("filePath") String filePath,
            @RequestParam(value = "vcsType", required = false, defaultValue = "git") String vcsType) {
        try {
            String type = vcsType == null ? "git" : vcsType.trim().toLowerCase();
            if ("svn".equals(type)) {
                hundsunService.svnReplaceSqlFile(filePath);
            } else {
                hundsunService.gitReplaceSqlFile(filePath);
            }
            return Result.success("代码替换完成（" + type + "），请查看控制台日志");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("代码替换失败：" + e.getMessage());
        }
    }

    /**
     * 批量更新所有 SVN 工作副本（等价于 {@code /hundsun/git/updateAll?vcsType=svn}）
     */
    @RequestMapping("/hundsun/svn/updateAll")
    public Result<String> updateAllSvnRepositories(@RequestParam("basePath") String basePath) {
        try {
            hundsunService.updateAllSvnRepositories(basePath);
            return Result.success("SVN 批量更新完成，请查看控制台日志获取详细信息");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("SVN 批量更新失败：" + e.getMessage());
        }
    }

    /**
     * 备份配置文件
     * @param sourceRoot 源目录（要备份的目录）
     * @param targetRoot 目标目录（备份存放目录）
     * @return JSON格式的响应，包含备份的文件列表
     */
    @RequestMapping("/hundsun/config/backup")
    public Result<Map<String, Object>> backupConfigFiles(
            @RequestParam("sourceRoot") String sourceRoot,
            @RequestParam("targetRoot") String targetRoot) {
        try {
            List<String> backedUpFiles = configFileService.backupFiles(sourceRoot, targetRoot);
            
            Map<String, Object> data = new HashMap<>();
            data.put("count", backedUpFiles.size());
            data.put("files", backedUpFiles);
            
            return Result.success("配置文件备份成功，共备份 " + backedUpFiles.size() + " 个文件", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("配置文件备份失败：" + e.getMessage());
        }
    }

    /**
     * 还原/替换配置文件
     *
     * @param sourceRoot 目标目录（要被替换的目录）
     * @param targetRoot 备份目录（备份文件所在目录）
     * @param restorePom 是否还原 pom.xml，默认 false（跳过）
     */
    @RequestMapping("/hundsun/config/restore")
    public Result<Map<String, Object>> restoreConfigFiles(
            @RequestParam("sourceRoot") String sourceRoot,
            @RequestParam("targetRoot") String targetRoot,
            @RequestParam(value = "restorePom", required = false, defaultValue = "false")
            boolean restorePom) {
        try {
            List<String> restoredFiles = configFileService.restoreFiles(sourceRoot, targetRoot, restorePom);

            Map<String, Object> data = new HashMap<>();
            data.put("count", restoredFiles.size());
            data.put("files", restoredFiles);
            data.put("restorePom", restorePom);

            return Result.success("配置文件还原成功，共还原 " + restoredFiles.size()
                    + " 个文件（restorePom=" + restorePom + "）", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("配置文件还原失败：" + e.getMessage());
        }
    }

    /**
     * 恢复备份（从备份目录恢复到项目目录）
     *
     * @param projectRoot 项目目录（要被恢复的目录）
     * @param backupRoot  备份目录（备份文件所在目录）
     * @param restorePom  是否还原 pom.xml，默认 false（跳过）
     */
    @RequestMapping("/hundsun/config/recover")
    public Result<Map<String, Object>> recoverFromBackup(
            @RequestParam("projectRoot") String projectRoot,
            @RequestParam("backupRoot") String backupRoot,
            @RequestParam(value = "restorePom", required = false, defaultValue = "false")
            boolean restorePom) {
        try {
            List<String> recoveredFiles = configFileService.restoreFiles(projectRoot, backupRoot, restorePom);

            Map<String, Object> data = new HashMap<>();
            data.put("projectRoot", projectRoot);
            data.put("backupRoot", backupRoot);
            data.put("count", recoveredFiles.size());
            data.put("files", recoveredFiles);
            data.put("restorePom", restorePom);

            return Result.success("从备份恢复成功，共恢复 " + recoveredFiles.size()
                    + " 个配置文件（restorePom=" + restorePom + "）", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("从备份恢复失败：" + e.getMessage());
        }
    }
}
