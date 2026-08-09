package com.zhoufz.tools;

import com.zhoufz.tools.service.ProjectProductFileTransplantService;
import com.zhoufz.tools.service.ProjectProductTransplantConfig;
import com.zhoufz.tools.service.ProjectProductTransplantConfigLoader;
import com.zhoufz.tools.service.ProjectProductTransplantService;
import com.zhoufz.tools.service.TransplantBusiness;
import com.zhoufz.tools.service.TransplantScope;
import org.junit.Assert;
import org.junit.Test;

import java.util.EnumSet;

/**
 * 工程代码与产品代码双向移植测试。
 * <p>
 * 支持按联机/前端/低柜精准控制移植范围；前端仅移植个性化 vue 与个性化路由。
 *
 * @author zhoufz
 * @date 20260705
 */
public class ProjectProductTest {

    // ==================== 路径配置（USE_PROPERTIES_FILE=false 时生效） ====================

    private static final String PROJECT_ROOT = "F:\\ProjectSource";
    private static final String PROJECT_BANK_DIR_NAME = "lcpt-qhyh_dxfund";
    private static final String BANK_CODE = "qhyh_dxfund";
    private static final String PRODUCT_BACKEND_ROOT = "F:\\AppSource\\Sources\\app\\lcpt-server";
    private static final String PRODUCT_FRONT_ROOT = "F:\\GITIFMS6.0\\Sources\\app\\lcpt-front";
    private static final String PRODUCT_COUNTER_ROOT = "F:\\GITIFMS6.0\\Sources\\app\\ifmcounter";

    /** 是否优先从 classpath:projectProduct.properties 读取配置 */
    private static final boolean USE_PROPERTIES_FILE = true;

    // ==================== 移植范围（USE_PROPERTIES_FILE=false 时生效） ====================

    private static final boolean SCOPE_ONLINE = true;
    private static final boolean SCOPE_FRONT = false;
    private static final boolean SCOPE_COUNTER = false;

    // ==================== 业务同步范围（USE_PROPERTIES_FILE=false 时生效） ====================

    /** 方式1：逗号指定，非空时优先生效，例如 "dxfund,dxasset,dxtrust" */
    private static final String BUSINESS_ENABLED_LIST = "dxfund";

    /** 工程→产品 指定文件列表（classpath 资源） */
    private static final String PROJECT_TO_PRODUCT_FILE = "projectToProductReplaceFile.txt";

    /** 产品→工程 指定文件列表（classpath 资源） */
    private static final String PRODUCT_TO_PROJECT_FILE = "productToProjectReplaceFile.txt";

    // ==================== 配置构建 ====================

    private ProjectProductTransplantConfig buildConfig() throws Exception {
        if (USE_PROPERTIES_FILE) {
            return ProjectProductTransplantConfigLoader.loadFromClasspath();
        }
        EnumSet<TransplantScope> scopes = EnumSet.noneOf(TransplantScope.class);
        if (SCOPE_ONLINE) {
            scopes.add(TransplantScope.ONLINE);
        }
        if (SCOPE_FRONT) {
            scopes.add(TransplantScope.FRONT);
        }
        if (SCOPE_COUNTER) {
            scopes.add(TransplantScope.COUNTER);
        }
        EnumSet<TransplantBusiness> businesses = parseBusinessesFromList(BUSINESS_ENABLED_LIST);
        return new ProjectProductTransplantConfig()
                .setProjectRoot(PROJECT_ROOT)
                .setProjectBankDirName(PROJECT_BANK_DIR_NAME)
                .setBankCode(BANK_CODE)
                .setProductBackendRoot(PRODUCT_BACKEND_ROOT)
                .setProductFrontRoot(PRODUCT_FRONT_ROOT)
                .setProductCounterRoot(PRODUCT_COUNTER_ROOT)
                .setEnabledScopes(scopes)
                .setEnabledBusinesses(businesses);
    }

    private EnumSet<TransplantBusiness> parseBusinessesFromList(String list) {
        EnumSet<TransplantBusiness> businesses = EnumSet.noneOf(TransplantBusiness.class);
        if (list == null || list.trim().isEmpty()) {
            businesses.addAll(EnumSet.allOf(TransplantBusiness.class));
            return businesses;
        }
        for (String item : list.split(",")) {
            TransplantBusiness business = TransplantBusiness.fromConfigKey(item.trim());
            if (business != null) {
                businesses.add(business);
            }
        }
        return businesses;
    }

    /**
     * 工程 → 产品。范围由 projectProduct.properties 中 transplant.scope.* 控制。
     */
    @Test
    public void transplantProjectToProduct() throws Exception {
        ProjectProductTransplantService service = new ProjectProductTransplantService();
        ProjectProductTransplantService.TransplantResult result =
                service.transplantProjectToProduct(buildConfig());
        System.out.println("移植完成，共复制 " + result.getCopiedCount() + " 个文件");
        Assert.assertTrue("未复制任何文件，请检查工程目录、银行代码与移植范围配置", result.getCopiedCount() > 0);
    }

    /**
     * 产品 → 工程。范围由 projectProduct.properties 中 transplant.scope.* 控制。
     */
    @Test
    public void transplantProductToProject() throws Exception {
        ProjectProductTransplantService service = new ProjectProductTransplantService();
        ProjectProductTransplantService.TransplantResult result =
                service.transplantProductToProject(buildConfig());
        System.out.println("移植完成，共复制 " + result.getCopiedCount() + " 个文件");
        if (result.getCopiedCount() == 0) {
            System.out.println("[WARN] 未匹配到可移植文件，请确认银行代码与 transplant.scope.* 配置");
        }
    }

    /**
     * 工程 → 产品（指定文件）。
     * <p>
     * 读取 {@link #PROJECT_TO_PRODUCT_FILE}，每行一个工程侧文件绝对路径，复制到对应产品目录。
     * 路径映射规则与 {@link #transplantProjectToProduct()} 一致（含联机 bootstrap-adapter 映射）。
     */
    @Test
    public void transplantProjectToProductByFileList() throws Exception {
        ProjectProductFileTransplantService service = new ProjectProductFileTransplantService();
        ProjectProductTransplantService.TransplantResult result =
                service.transplantProjectToProductByFileList(buildConfig(), PROJECT_TO_PRODUCT_FILE);
        System.out.println("指定文件移植完成，共复制 " + result.getCopiedCount() + " 个文件");
        Assert.assertTrue("未复制任何文件，请检查 " + PROJECT_TO_PRODUCT_FILE + " 中的路径", result.getCopiedCount() > 0);
    }

    /**
     * 产品 → 工程（指定文件）。
     * <p>
     * 读取 {@link #PRODUCT_TO_PROJECT_FILE}，每行一个产品侧文件绝对路径，复制到对应工程目录。
     * 前端自动映射：{@code console-{biz}-vue} → {@code console-{biz}-bank-vue}；
     * 低柜自动映射：剥掉 {@code ifmcounter}/{@code ifmcounter-*} 子模块前缀。
     */
    @Test
    public void transplantProductToProjectByFileList() throws Exception {
        ProjectProductFileTransplantService service = new ProjectProductFileTransplantService();
        ProjectProductTransplantService.TransplantResult result =
                service.transplantProductToProjectByFileList(buildConfig(), PRODUCT_TO_PROJECT_FILE);
        System.out.println("指定文件移植完成，共复制 " + result.getCopiedCount() + " 个文件");
        if (result.getCopiedCount() == 0) {
            System.out.println("[WARN] 未复制任何文件，请检查 " + PRODUCT_TO_PROJECT_FILE + " 中的路径");
        }
    }
}
