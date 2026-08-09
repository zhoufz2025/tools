package com.zhoufz.tools;

import com.zhoufz.tools.service.CounterPathMapper;
import org.junit.Assert;
import org.junit.Test;

/**
 * 低柜路径映射单元测试。
 *
 * @author zhoufz
 * @date 20260723
 */
public class CounterPathMapperTest {

    @Test
    public void mapProductBaseCounterToProjectFlat() {
        String product = "ifmcounter/ifm/com/hundsun/lcpt/counter/bank/qhyh_dxfund/sso/SignInSingleServiceImpl.java";
        String project = CounterPathMapper.mapModuleRelativePath(product, false);
        Assert.assertEquals(
                "ifm/com/hundsun/lcpt/counter/bank/qhyh_dxfund/sso/SignInSingleServiceImpl.java",
                project);
    }

    @Test
    public void mapProductDxfundSubmoduleToProjectFlat() {
        String product = "ifmcounter-dxfund/WebContent/jspui/ifmcounter/dxfund/trans/T110518.jsp";
        String project = CounterPathMapper.mapModuleRelativePath(product, false);
        Assert.assertEquals("WebContent/jspui/ifmcounter/dxfund/trans/T110518.jsp", project);
    }

    @Test
    public void mapProjectSsoBackToProductBaseCounter() {
        // 银行简称含 _dxfund，但路径无 /dxfund/ 段，应落回基座 ifmcounter
        String project = "ifm/com/hundsun/lcpt/counter/bank/qhyh_dxfund/sso/SignInSingleServiceImpl.java";
        String product = CounterPathMapper.mapModuleRelativePath(project, true);
        Assert.assertEquals(
                "ifmcounter/ifm/com/hundsun/lcpt/counter/bank/qhyh_dxfund/sso/SignInSingleServiceImpl.java",
                product);
    }

    @Test
    public void mapProjectDxfundSrcToProductDxfundSubmodule() {
        String project = "dxfund-src/java/com/hundsun/lcpt/counter/dxfund/bank/dbs/trans/T119049HSAdapter.java";
        String product = CounterPathMapper.mapModuleRelativePath(project, true);
        Assert.assertEquals(
                "ifmcounter-dxfund/dxfund-src/java/com/hundsun/lcpt/counter/dxfund/bank/dbs/trans/T119049HSAdapter.java",
                product);
    }

    @Test
    public void alreadyPrefixedPathUnchangedWhenToProduct() {
        String path = "ifmcounter/ifm/com/hundsun/lcpt/counter/bank/ljyh/sso/X.java";
        Assert.assertEquals(path, CounterPathMapper.mapModuleRelativePath(path, true));
    }

    @Test
    public void mapProductInsureSubmoduleToProjectFlat() {
        String product = "ifmcounter-insure/insure-src/java/com/hundsun/lcpt/counter/insure/bank/x/X.java";
        String project = CounterPathMapper.mapModuleRelativePath(product, false);
        Assert.assertEquals(
                "insure-src/java/com/hundsun/lcpt/counter/insure/bank/x/X.java",
                project);
    }
}
