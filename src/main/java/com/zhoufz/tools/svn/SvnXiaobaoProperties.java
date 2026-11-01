package com.zhoufz.tools.svn;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SVN 小包拉包默认配置。
 *
 * @author zhoufz
 */
@ConfigurationProperties(prefix = "svn.xiaobao")
public class SvnXiaobaoProperties {

    /** 本地小包根目录，其下按分支短名建文件夹 */
    private String localRoot = "/Users/zhoufz/hundsun/lcpt60/svn/branch";

    /** SVN 仓库根 */
    private String repoRoot = "https://192.168.57.56/bank/depone/IFMS6.0";

    /** 分支前缀，如 branches/fix（大小写须与 SVN 一致） */
    private String branchPrefix = "branches/fix";

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getRepoRoot() {
        return repoRoot;
    }

    public void setRepoRoot(String repoRoot) {
        this.repoRoot = repoRoot;
    }

    public String getBranchPrefix() {
        return branchPrefix;
    }

    public void setBranchPrefix(String branchPrefix) {
        this.branchPrefix = branchPrefix;
    }
}
