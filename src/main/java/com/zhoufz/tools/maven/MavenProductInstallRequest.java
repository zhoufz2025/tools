package com.zhoufz.tools.maven;

/**
 * 产品包按工程包版本 install 请求。
 *
 * @author zhoufz
 */
public class MavenProductInstallRequest {

    /** 产品模块目录或 pom.xml 路径 */
    private String modulePath;

    /** 工程包正在用的目标版本，如 7.5.0-SNAPSHOT */
    private String targetVersion;

    /** 原版本；空则从当前 pom 推断，install 后改回 */
    private String originalVersion;

    /** Maven settings 路径 */
    private String settingsPath;

    /** 本地仓库根 */
    private String localRepo;

    /** 执行前清空目标版本目录，默认 true */
    private Boolean clearTargetDir = Boolean.TRUE;

    /** install 成功后改回原版本，默认 true */
    private Boolean restoreOriginalVersion = Boolean.TRUE;

    /** 额外 Maven 参数，默认 -DskipTests */
    private String extraMavenArgs = "-DskipTests";

    /** 是否离线（追加 -o） */
    private Boolean offline = Boolean.FALSE;

    /**
     * 是否执行 clean（默认 false：与 IDEA 日常增量更接近，更快）。
     * 需要彻底重编时再勾选。
     */
    private Boolean runClean = Boolean.FALSE;

    /**
     * 不检查 SNAPSHOT 更新（追加 -nsu，默认 true）。
     * 对齐 IDEA 关闭「Always update snapshots」；关了才会反复打 Nexus 元数据导致很慢。
     */
    private Boolean noSnapshotUpdates = Boolean.TRUE;

    /**
     * 允许非配置白名单的 groupId（默认拒绝非 com.hundsun.lcpt）。
     */
    private Boolean allowOtherGroupId = Boolean.FALSE;

    public String getModulePath() {
        return modulePath;
    }

    public void setModulePath(String modulePath) {
        this.modulePath = modulePath;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public String getOriginalVersion() {
        return originalVersion;
    }

    public void setOriginalVersion(String originalVersion) {
        this.originalVersion = originalVersion;
    }

    public String getSettingsPath() {
        return settingsPath;
    }

    public void setSettingsPath(String settingsPath) {
        this.settingsPath = settingsPath;
    }

    public String getLocalRepo() {
        return localRepo;
    }

    public void setLocalRepo(String localRepo) {
        this.localRepo = localRepo;
    }

    public Boolean getClearTargetDir() {
        return clearTargetDir;
    }

    public void setClearTargetDir(Boolean clearTargetDir) {
        this.clearTargetDir = clearTargetDir;
    }

    public Boolean getRestoreOriginalVersion() {
        return restoreOriginalVersion;
    }

    public void setRestoreOriginalVersion(Boolean restoreOriginalVersion) {
        this.restoreOriginalVersion = restoreOriginalVersion;
    }

    public String getExtraMavenArgs() {
        return extraMavenArgs;
    }

    public void setExtraMavenArgs(String extraMavenArgs) {
        this.extraMavenArgs = extraMavenArgs;
    }

    public Boolean getOffline() {
        return offline;
    }

    public void setOffline(Boolean offline) {
        this.offline = offline;
    }

    public Boolean getRunClean() {
        return runClean;
    }

    public void setRunClean(Boolean runClean) {
        this.runClean = runClean;
    }

    public Boolean getNoSnapshotUpdates() {
        return noSnapshotUpdates;
    }

    public void setNoSnapshotUpdates(Boolean noSnapshotUpdates) {
        this.noSnapshotUpdates = noSnapshotUpdates;
    }

    public Boolean getAllowOtherGroupId() {
        return allowOtherGroupId;
    }

    public void setAllowOtherGroupId(Boolean allowOtherGroupId) {
        this.allowOtherGroupId = allowOtherGroupId;
    }
}
