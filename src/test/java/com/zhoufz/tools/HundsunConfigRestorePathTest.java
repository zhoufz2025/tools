package com.zhoufz.tools;

import com.zhoufz.tools.service.HundsunConfigFileServiceImpl;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 配置还原路径对齐：避免快照目录 app / 日期目录被拼进工程路径。
 */
public class HundsunConfigRestorePathTest {

    @Test
    public void stripSnapshotAppWhenDestIsLcptServer() {
        Path dest = Paths.get("/Users/zhoufz/hundsun/lcpt60/git/Sources/app/lcpt-server");
        Path relative = Paths.get("app/lcpt-server/sale/lcpt-web/pom.xml");
        Path actual = HundsunConfigFileServiceImpl.resolveRestoreTarget(dest, relative);
        Path expected = dest.resolve("sale/lcpt-web/pom.xml");
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void stripDuplicateLcptServer() {
        Path dest = Paths.get("/Users/zhoufz/hundsun/lcpt60/git/Sources/app/lcpt-server");
        Path relative = Paths.get("lcpt-server/sale/lcpt-web/pom.xml");
        Path actual = HundsunConfigFileServiceImpl.resolveRestoreTarget(dest, relative);
        Path expected = dest.resolve("sale/lcpt-web/pom.xml");
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void alignedAppRootsKeepRelative() {
        Path dest = Paths.get("/Users/zhoufz/hundsun/lcpt60/git/Sources/app");
        Path relative = Paths.get("lcpt-server/sale/lcpt-web/pom.xml");
        Path actual = HundsunConfigFileServiceImpl.resolveRestoreTarget(dest, relative);
        Path expected = dest.resolve("lcpt-server/sale/lcpt-web/pom.xml");
        Assert.assertEquals(expected, actual);
    }
}
