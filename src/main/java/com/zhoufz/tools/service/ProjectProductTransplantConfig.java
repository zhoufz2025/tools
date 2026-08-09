package com.zhoufz.tools.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * 工程代码与产品代码双向移植配置。
 *
 * @author zhoufz
 * @date 20260705
 */
public class ProjectProductTransplantConfig {

    /** 工程根目录，例如 F:\ProjectSource */
    private String projectRoot;

    /** 工程银行目录名，例如 lcpt-ljyh（位于 projectRoot 下） */
    private String projectBankDirName;

    /** 银行代码，用于产品→工程时过滤个性化路径，例如 ljyh */
    private String bankCode;

    /** 产品后端根目录，例如 F:\AppSource\Sources\app\lcpt-server */
    private String productBackendRoot;

    /** 产品前端根目录，例如 F:\GITIFMS6.0\Sources\app\lcpt-front */
    private String productFrontRoot;

    /** 产品低柜根目录，例如 F:\GITIFMS6.0\Sources\app\ifmcounter */
    private String productCounterRoot;

    /** 工程内模块子目录名：后端 */
    public static final String MODULE_SERVER = "lcpt-server";

    /** 工程内模块子目录名：前端 */
    public static final String MODULE_FRONT = "lcpt-front";

    /** 工程内模块子目录名：低柜 */
    public static final String MODULE_COUNTER = "ifmcounter";

    /** 跳过的目录名（不区分大小写） */
    private Set<String> skipDirNames = new HashSet<>(Arrays.asList(
            "spsql", "sql", "sql-bank",
            ".git", ".idea", ".svn", "target", "node_modules", "logs"
    ));

    /** 跳过的 SQL 相关扩展名（不区分大小写） */
    private Set<String> skipExtensions = new HashSet<>(Arrays.asList(
            ".sql", ".upd", ".vm"
    ));

    /** 跳过的文件名（不区分大小写，完全匹配） */
    private Set<String> skipFileNames = new HashSet<>(Collections.singletonList(
            "readme.txt"
    ));

    /** 参与移植的范围，默认三者全开 */
    private Set<TransplantScope> enabledScopes = EnumSet.allOf(TransplantScope.class);

    /** 参与移植的业务子系统，默认全部 */
    private Set<TransplantBusiness> enabledBusinesses = EnumSet.allOf(TransplantBusiness.class);

    public Path getProjectBankRoot() {
        return Paths.get(projectRoot, projectBankDirName);
    }

    public Path getProjectModuleRoot(String moduleSubDir) {
        return getProjectBankRoot().resolve(moduleSubDir);
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public ProjectProductTransplantConfig setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
        return this;
    }

    public String getProjectBankDirName() {
        return projectBankDirName;
    }

    public ProjectProductTransplantConfig setProjectBankDirName(String projectBankDirName) {
        this.projectBankDirName = projectBankDirName;
        return this;
    }

    public String getBankCode() {
        return bankCode;
    }

    public ProjectProductTransplantConfig setBankCode(String bankCode) {
        this.bankCode = bankCode;
        return this;
    }

    public String getProductBackendRoot() {
        return productBackendRoot;
    }

    public ProjectProductTransplantConfig setProductBackendRoot(String productBackendRoot) {
        this.productBackendRoot = productBackendRoot;
        return this;
    }

    public String getProductFrontRoot() {
        return productFrontRoot;
    }

    public ProjectProductTransplantConfig setProductFrontRoot(String productFrontRoot) {
        this.productFrontRoot = productFrontRoot;
        return this;
    }

    public String getProductCounterRoot() {
        return productCounterRoot;
    }

    public ProjectProductTransplantConfig setProductCounterRoot(String productCounterRoot) {
        this.productCounterRoot = productCounterRoot;
        return this;
    }

    public Set<String> getSkipDirNames() {
        return Collections.unmodifiableSet(skipDirNames);
    }

    public ProjectProductTransplantConfig setSkipDirNames(Set<String> skipDirNames) {
        this.skipDirNames = new HashSet<>(skipDirNames);
        return this;
    }

    public Set<String> getSkipExtensions() {
        return Collections.unmodifiableSet(skipExtensions);
    }

    public ProjectProductTransplantConfig setSkipExtensions(Set<String> skipExtensions) {
        this.skipExtensions = new HashSet<>(skipExtensions);
        return this;
    }

    public Set<String> getSkipFileNames() {
        return Collections.unmodifiableSet(skipFileNames);
    }

    public ProjectProductTransplantConfig setSkipFileNames(Set<String> skipFileNames) {
        this.skipFileNames = new HashSet<>(skipFileNames);
        return this;
    }

    public Set<TransplantScope> getEnabledScopes() {
        return Collections.unmodifiableSet(enabledScopes);
    }

    public ProjectProductTransplantConfig setEnabledScopes(Set<TransplantScope> enabledScopes) {
        if (enabledScopes == null || enabledScopes.isEmpty()) {
            throw new IllegalArgumentException("enabledScopes 至少选择一个移植范围");
        }
        this.enabledScopes = EnumSet.copyOf(enabledScopes);
        return this;
    }

    public boolean isScopeEnabled(TransplantScope scope) {
        return enabledScopes.contains(scope);
    }

    public Set<TransplantBusiness> getEnabledBusinesses() {
        return Collections.unmodifiableSet(enabledBusinesses);
    }

    public ProjectProductTransplantConfig setEnabledBusinesses(Set<TransplantBusiness> enabledBusinesses) {
        if (enabledBusinesses == null || enabledBusinesses.isEmpty()) {
            throw new IllegalArgumentException("enabledBusinesses 至少选择一个业务");
        }
        this.enabledBusinesses = EnumSet.copyOf(enabledBusinesses);
        return this;
    }

    public boolean isBusinessEnabled(TransplantBusiness business) {
        return enabledBusinesses.contains(business);
    }

    public TransplantScope resolveScope(String moduleName) {
        if (MODULE_SERVER.equals(moduleName)) {
            return TransplantScope.ONLINE;
        }
        if (MODULE_FRONT.equals(moduleName)) {
            return TransplantScope.FRONT;
        }
        if (MODULE_COUNTER.equals(moduleName)) {
            return TransplantScope.COUNTER;
        }
        throw new IllegalArgumentException("未知模块: " + moduleName);
    }
}
