package com.zhoufz.tools;

import com.zhoufz.tools.maven.MavenProductInstallProperties;
import com.zhoufz.tools.maven.MavenProductInstallRequest;
import com.zhoufz.tools.maven.MavenProductInstallService;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class MavenProductInstallPreviewIT {

    @Test
    public void previewLcptCore() throws Exception {
        String pom = "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/lcpt-server/pub/lcpt-jres/lcpt-core/pom.xml";
        String repo = "/opt/homebrew/Cellar/maven/3.9.9/libexec/repo";
        String settings = "/opt/homebrew/Cellar/maven/3.9.9/libexec/conf/settings_hs.xml";
        Assume.assumeTrue(Files.isRegularFile(Paths.get(pom)));
        Assume.assumeTrue(Files.isDirectory(Paths.get(repo)));
        Assume.assumeTrue(Files.isRegularFile(Paths.get(settings)));

        MavenProductInstallProperties props = new MavenProductInstallProperties();
        props.setLocalRepo(repo);
        props.setSettingsPath(settings);
        MavenProductInstallService service = new MavenProductInstallService(props);

        MavenProductInstallRequest req = new MavenProductInstallRequest();
        req.setModulePath(pom);
        req.setTargetVersion("7.5.0-SNAPSHOT");
        req.setClearTargetDir(true);
        req.setRestoreOriginalVersion(true);
        req.setSettingsPath(settings);
        req.setLocalRepo(repo);

        Map<String, Object> data = service.preview(req);
        Assert.assertEquals("com.hundsun.lcpt", data.get("groupId"));
        Assert.assertEquals("lcpt-core", data.get("artifactId"));
        Assert.assertEquals("7.4.0-SNAPSHOT", data.get("originalVersion"));
        Assert.assertEquals("7.5.0-SNAPSHOT", data.get("targetVersion"));
        Assert.assertTrue(String.valueOf(data.get("targetVersionDir")).endsWith("/lcpt-core/7.5.0-SNAPSHOT"));
        Assert.assertFalse(Boolean.TRUE.equals(data.get("executed")));
        System.out.println("preview ok: " + data.get("targetVersionDir"));
        System.out.println("commands: " + data.get("commands"));
    }
}
