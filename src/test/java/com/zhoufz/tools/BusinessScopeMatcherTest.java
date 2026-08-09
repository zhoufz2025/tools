package com.zhoufz.tools;

import com.zhoufz.tools.service.BusinessScopeMatcher;
import com.zhoufz.tools.service.TransplantBusiness;
import com.zhoufz.tools.service.TransplantScope;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.EnumSet;

/**
 * 业务范围匹配单元测试。
 *
 * @author zhoufz
 * @date 20260705
 */
public class BusinessScopeMatcherTest {

    @Test
    public void matchDxfundOnlinePath() {
        Assert.assertTrue(BusinessScopeMatcher.matches(
                Paths.get("sale/lcpt-dxfund/lcpt-dxfund-trans/lcpt-dxfund-batch-bootstrap-adapter"
                        + "/src/main/java/com/hundsun/lcpt/dxfund/batch/bank/mtsh_fundinsure/x.java"),
                TransplantScope.ONLINE,
                EnumSet.of(TransplantBusiness.DXFUND)));
    }

    @Test
    public void rejectInsureWhenOnlyDxfundEnabled() {
        Assert.assertFalse(BusinessScopeMatcher.matches(
                Paths.get("sale/lcpt-insure/lcpt-insure-trans/lcpt-insure-online-bootstrap-adapter"
                        + "/src/main/java/com/hundsun/lcpt/insure/online/bank/mtsh_fundinsure/x.java"),
                TransplantScope.ONLINE,
                EnumSet.of(TransplantBusiness.DXFUND)));
    }

    @Test
    public void matchDxassetFrontPath() {
        Assert.assertTrue(BusinessScopeMatcher.matches(
                Paths.get("hui1.0/console/src/biz/console-dxasset-bank-vue/router/bank/jsyh/router.js"),
                TransplantScope.FRONT,
                EnumSet.of(TransplantBusiness.DXASSET)));
    }

    @Test
    public void matchDxfundProductFrontPath() {
        Assert.assertTrue(BusinessScopeMatcher.matches(
                Paths.get("hui1.0/console/src/biz/console-dxfund-vue/views/bank/qhyh_dxfund/x.vue"),
                TransplantScope.FRONT,
                EnumSet.of(TransplantBusiness.DXFUND)));
    }

    @Test
    public void matchDxtrustCounterPath() {
        Assert.assertTrue(BusinessScopeMatcher.matches(
                Paths.get("webcontent/jspui/ifmcounter/dxtrust/query/t100334.jsp"),
                TransplantScope.COUNTER,
                EnumSet.of(TransplantBusiness.DXTRUST)));
    }

    @Test
    public void fromConfigKey() {
        Assert.assertEquals(TransplantBusiness.DXFUND, TransplantBusiness.fromConfigKey("dxfund"));
        Assert.assertNull(TransplantBusiness.fromConfigKey("unknown"));
    }
}
