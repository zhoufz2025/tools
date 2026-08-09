package com.zhoufz.tools;

import com.zhoufz.tools.service.OnlinePathMapper;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 联机路径映射单元测试。
 *
 * @author zhoufz
 * @date 20260705
 */
public class OnlinePathMapperTest {

    private static final String BANK = "mtsh_fundinsure";

    @Test
    public void mapProjectPubBatchAdapterToProductBootstrap() {
        String project = "sale/lcpt-dxfund/lcpt-dxfund-pub/lcpt-dxfund-pub-batch-bootstrap-adapter"
                + "/src/main/java/com/hundsun/lcpt/dxfund/pub/batch/bank/mtsh_fundinsure/impl/X.java";
        String product = OnlinePathMapper.mapModuleRelativePath(project, true, BANK);
        Assert.assertTrue(product.contains("lcpt-dxfund-pub-batch-bootstrap/"));
        Assert.assertFalse(product.contains("bootstrap-adapter"));
        Assert.assertTrue(product.contains("bank/mtsh_fundinsure/"));
    }

    @Test
    public void mapProductBatchBootstrapJavaToProjectAdapter() {
        String product = "sale/lcpt-dxfund/lcpt-dxfund-trans/lcpt-dxfund-batch-bootstrap"
                + "/src/main/java/com/hundsun/lcpt/dxfund/batch/bank/mtsh_fundinsure/impl/X.java";
        String project = OnlinePathMapper.mapModuleRelativePath(product, false, BANK);
        Assert.assertTrue(project.contains("lcpt-dxfund-batch-bootstrap-adapter/"));
    }

    @Test
    public void springContextInBootstrapNoMapping() {
        String path = "sale/lcpt-dxfund/lcpt-dxfund-pub/lcpt-dxfund-pub-batch-bootstrap"
                + "/src/main/resources/bank/mtsh_fundinsure/SpringContext.xml";
        String toProduct = OnlinePathMapper.mapModuleRelativePath(path, true, BANK);
        String toProject = OnlinePathMapper.mapModuleRelativePath(path, false, BANK);
        Assert.assertEquals(path, toProduct);
        Assert.assertEquals(path, toProject);
    }

    @Test
    public void pubOnlineAdapterPathUnchanged() {
        String path = "sale/lcpt-pub/lcpt-pub-common/lcpt-pub-online-adapter"
                + "/src/main/java/com/hundsun/lcpt/pub/online/bank/mtsh_fundinsure/T100001/X.java";
        String mapped = OnlinePathMapper.mapModuleRelativePath(path, true, BANK);
        Assert.assertEquals(path, mapped);
    }

    @Test
    public void rejectPathWithoutBankCode() {
        Path file = Paths.get("sale/lcpt-dxfund/lcpt-dxfund-trans/lcpt-dxfund-batch-bootstrap"
                + "/src/main/java/com/hundsun/lcpt/dxfund/batch/adapter/T210013/T210013HSAdapter.java");
        Assert.assertFalse(OnlinePathMapper.containsBankCode(file, BANK));
    }

    @Test
    public void acceptPathWithBankCode() {
        Path file = Paths.get("sale/lcpt-web/lcpt-web-manager-dxfund/lcpt-web-manager-dxfund-bank"
                + "/src/main/java/com/hundsun/lcpt/pub/bank/mtsh_fundinsure/impl/X.java");
        Assert.assertTrue(OnlinePathMapper.isOnlineBankPersonalizedPath(file, BANK));
    }
}
