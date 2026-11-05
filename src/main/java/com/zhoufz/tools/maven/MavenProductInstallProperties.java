package com.zhoufz.tools.maven;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 产品包按工程包版本 install 到本地仓 — 默认配置。
 *
 * @author zhoufz
 */
@ConfigurationProperties(prefix = "maven.product-install")
public class MavenProductInstallProperties {

    /** Maven settings（同时用于 -s 与 --global-settings） */
    private String settingsPath = "/opt/homebrew/Cellar/maven/3.9.9/libexec/conf/settings_hs.xml";

    /** 本地仓库根 */
    private String localRepo = "/opt/homebrew/Cellar/maven/3.9.9/libexec/repo";

    /** 默认产品模块 pom 或目录 */
    private String defaultModulePath =
            "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/lcpt-server/pub/lcpt-jres/lcpt-core/pom.xml";

    /** 默认目标版本（工程包正在用的） */
    private String defaultTargetVersion = "7.5.0-SNAPSHOT";

    /** 允许的 groupId 前缀；默认仅 com.hundsun.lcpt */
    private String allowedGroupId = "com.hundsun.lcpt";

    /** mvn 超时毫秒，默认 30 分钟 */
    private long timeoutMs = 30L * 60L * 1000L;

    /** mvn 可执行文件；空则用 PATH 中的 mvn */
    private String mvnExecutable = "mvn";

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

    public String getDefaultModulePath() {
        return defaultModulePath;
    }

    public void setDefaultModulePath(String defaultModulePath) {
        this.defaultModulePath = defaultModulePath;
    }

    public String getDefaultTargetVersion() {
        return defaultTargetVersion;
    }

    public void setDefaultTargetVersion(String defaultTargetVersion) {
        this.defaultTargetVersion = defaultTargetVersion;
    }

    public String getAllowedGroupId() {
        return allowedGroupId;
    }

    public void setAllowedGroupId(String allowedGroupId) {
        this.allowedGroupId = allowedGroupId;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getMvnExecutable() {
        return mvnExecutable;
    }

    public void setMvnExecutable(String mvnExecutable) {
        this.mvnExecutable = mvnExecutable;
    }
}
