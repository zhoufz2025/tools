package com.zhoufz.tools.controller;

import com.zhoufz.tools.dto.Result;
import com.zhoufz.tools.service.HundsunConfigFileServiceImpl;
import com.zhoufz.tools.service.HundsunServiceImpl;
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
     * 批量更新所有Git仓库
     * @param basePath 基础路径，例如：/Users/zhoufz/hundsun/lcpt60/git/Sources/
     * @return JSON格式的响应
     */
    @RequestMapping("/hundsun/git/updateAll")
    public Result<String> updateAllRepositories(@RequestParam("basePath") String basePath) {
        try {
            hundsunService.updateAllGitRepositories(basePath);
            return Result.success("批量更新任务已启动，请查看控制台日志获取详细信息");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("批量更新失败：" + e.getMessage());
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
     * @param sourceRoot 目标目录（要被替换的目录）
     * @param targetRoot 备份目录（备份文件所在目录）
     * @return JSON格式的响应，包含还原的文件列表
     */
    @RequestMapping("/hundsun/config/restore")
    public Result<Map<String, Object>> restoreConfigFiles(
            @RequestParam("sourceRoot") String sourceRoot,
            @RequestParam("targetRoot") String targetRoot) {
        try {
            List<String> restoredFiles = configFileService.restoreFiles(sourceRoot, targetRoot);
            
            Map<String, Object> data = new HashMap<>();
            data.put("count", restoredFiles.size());
            data.put("files", restoredFiles);
            
            return Result.success("配置文件还原成功，共还原 " + restoredFiles.size() + " 个文件", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("配置文件还原失败：" + e.getMessage());
        }
    }

    /**
     * 恢复备份（从备份目录恢复到项目目录）
     * @param projectRoot 项目目录（要被恢复的目录）
     * @param backupRoot 备份目录（备份文件所在目录）
     * @return JSON格式的响应，包含恢复的文件列表
     */
    @RequestMapping("/hundsun/config/recover")
    public Result<Map<String, Object>> recoverFromBackup(
            @RequestParam("projectRoot") String projectRoot,
            @RequestParam("backupRoot") String backupRoot) {
        try {
            List<String> recoveredFiles = configFileService.restoreFiles(projectRoot, backupRoot);
            
            Map<String, Object> data = new HashMap<>();
            data.put("projectRoot", projectRoot);
            data.put("backupRoot", backupRoot);
            data.put("count", recoveredFiles.size());
            data.put("files", recoveredFiles);
            
            return Result.success("从备份恢复成功，共恢复 " + recoveredFiles.size() + " 个配置文件", data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("从备份恢复失败：" + e.getMessage());
        }
    }
}
