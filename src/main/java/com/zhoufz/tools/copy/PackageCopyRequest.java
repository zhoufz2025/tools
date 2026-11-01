package com.zhoufz.tools.copy;

import java.util.ArrayList;
import java.util.List;

/**
 * 大包→小包 A/B 相对路径复制请求。
 *
 * @author zhoufz
 */
public class PackageCopyRequest {

    /** 源根 A（大包模块根） */
    private String sourceRoot;

    /** 目标根 B（小包对应根） */
    private String targetRoot;

    /** 源文件绝对路径列表 */
    private List<String> files = new ArrayList<>();

    /** SQL 追加任务号；空则脚本也整文件覆盖 */
    private String taskId;

    /**
     * 强制包含的风险源路径（仅 execute 使用）。
     * 须与 preview 中 status=risk 的 source 一致。
     */
    private List<String> forcePaths = new ArrayList<>();

    public String getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public String getTargetRoot() {
        return targetRoot;
    }

    public void setTargetRoot(String targetRoot) {
        this.targetRoot = targetRoot;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = files;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<String> getForcePaths() {
        return forcePaths;
    }

    public void setForcePaths(List<String> forcePaths) {
        this.forcePaths = forcePaths;
    }
}
