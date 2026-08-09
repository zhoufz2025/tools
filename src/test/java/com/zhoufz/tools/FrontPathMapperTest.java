package com.zhoufz.tools;

import com.zhoufz.tools.service.FrontPathMapper;
import org.junit.Assert;
import org.junit.Test;

/**
 * 前端路径映射单元测试。
 *
 * @author zhoufz
 * @date 20260721
 */
public class FrontPathMapperTest {

    @Test
    public void mapProductDxfundVueToProjectBankVue() {
        String product = "HUI1.0/console/src/biz/console-dxfund-vue/views/bank/qhyh_dxfund/"
                + "ifmDxfundCRcyw/ifmCRcywDxfundZjhbMenu/ifmCXtglDxFundautotransfer.vue";
        String project = FrontPathMapper.mapModuleRelativePath(product, false);
        Assert.assertEquals(
                "HUI1.0/console/src/biz/console-dxfund-bank-vue/views/bank/qhyh_dxfund/"
                        + "ifmDxfundCRcyw/ifmCRcywDxfundZjhbMenu/ifmCXtglDxFundautotransfer.vue",
                project);
    }

    @Test
    public void mapProductRouterToProjectBankVue() {
        String product = "HUI1.0/console/src/biz/console-dxfund-vue/router/bank/qhyh_dxfund/router.js";
        String project = FrontPathMapper.mapModuleRelativePath(product, false);
        Assert.assertEquals(
                "HUI1.0/console/src/biz/console-dxfund-bank-vue/router/bank/qhyh_dxfund/router.js",
                project);
    }

    @Test
    public void mapProjectBankVueToProductVue() {
        String project = "HUI1.0/console/src/biz/console-dxfund-bank-vue/views/bank/qhyh_dxfund/X.vue";
        String product = FrontPathMapper.mapModuleRelativePath(project, true);
        Assert.assertEquals(
                "HUI1.0/console/src/biz/console-dxfund-vue/views/bank/qhyh_dxfund/X.vue",
                product);
    }

    @Test
    public void alreadyBankVueProductToProjectUnchanged() {
        String path = "HUI1.0/console/src/biz/console-dxfund-bank-vue/router/bank/qhyh_dxfund/router.js";
        Assert.assertEquals(path, FrontPathMapper.mapModuleRelativePath(path, false));
    }

    @Test
    public void alreadyProductVueProjectToProductUnchanged() {
        String path = "HUI1.0/console/src/biz/console-dxfund-vue/views/bank/qhyh_dxfund/X.vue";
        Assert.assertEquals(path, FrontPathMapper.mapModuleRelativePath(path, true));
    }

    @Test
    public void mapFinaAndDxassetBizPackages() {
        Assert.assertEquals(
                "HUI1.0/console/src/biz/console-fina-bank-vue/views/bank/ljyh/a.vue",
                FrontPathMapper.mapModuleRelativePath(
                        "HUI1.0/console/src/biz/console-fina-vue/views/bank/ljyh/a.vue", false));
        Assert.assertEquals(
                "HUI1.0/console/src/biz/console-dxasset-vue/router/bank/jsyh/router.js",
                FrontPathMapper.mapModuleRelativePath(
                        "HUI1.0/console/src/biz/console-dxasset-bank-vue/router/bank/jsyh/router.js",
                        true));
    }

    @Test
    public void rewriteProductRouterAliasToBankVue() {
        String product = "ifmCXtglDxFundautotransfer:() => import(/* webpackChunkName: "
                + "\"console-dxfund-vue/bank/qhyh_dxfund/ifmDxfundCRcyw/ifmCXtglDxFundautotransfer\" */"
                + "`@ConsoleDxfundVue/views/bank/qhyh_dxfund/ifmDxfundCRcyw/ifmCXtglDxFundautotransfer`),";
        String project = FrontPathMapper.rewriteFileContent(product, false);
        Assert.assertTrue(project.contains("@ConsoleDxfundBankVue/views/bank/"));
        Assert.assertFalse(project.contains("@ConsoleDxfundVue/views/bank/"));
        Assert.assertTrue(project.contains("console-dxfund-bank-vue/bank/"));
        Assert.assertFalse(project.contains("console-dxfund-vue/bank/"));
    }

    @Test
    public void keepStandardComponentAliasWhenProductToProject() {
        String vue = "import HDatagrid from \"@ConsoleDxfundVue/components/HDatagrid\";\n"
                + "import { post } from \"@ConsoleDxfundVue/api/bizSys/commonUtil\";";
        String rewritten = FrontPathMapper.rewriteFileContent(vue, false);
        Assert.assertEquals(vue, rewritten);
    }

    @Test
    public void rewriteProjectBankAliasToProduct() {
        String project = "() => import(/* webpackChunkName: \"console-fina-bank-vue/bank/ljyh/x\" */"
                + "`@ConsoleFinaBankVue/views/bank/ljyh/x`),";
        String product = FrontPathMapper.rewriteFileContent(project, true);
        Assert.assertTrue(product.contains("@ConsoleFinaVue/views/bank/"));
        Assert.assertFalse(product.contains("@ConsoleFinaBankVue/"));
        Assert.assertTrue(product.contains("console-fina-vue/bank/"));
        Assert.assertFalse(product.contains("console-fina-bank-vue"));
    }
}
