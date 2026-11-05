package com.zhoufz.tools;

import com.zhoufz.tools.maven.MavenProductInstallService;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * pom 解析冒烟：groupId/artifactId/version（含 parent 回落）。
 *
 * @author zhoufz
 */
public class MavenProductInstallPomParseTest {

    @Test
    public void parseWithOwnVersionAndParentGroup() throws Exception {
        Path dir = Files.createTempDirectory("pom-parse");
        Path pom = dir.resolve("pom.xml");
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<project>\n"
                + "  <parent>\n"
                + "    <groupId>com.hundsun.lcpt</groupId>\n"
                + "    <artifactId>lcpt-jres</artifactId>\n"
                + "    <version>7.4.0-SNAPSHOT</version>\n"
                + "  </parent>\n"
                + "  <artifactId>lcpt-core</artifactId>\n"
                + "  <version>7.4.0-SNAPSHOT</version>\n"
                + "</project>\n";
        Files.write(pom, xml.getBytes(StandardCharsets.UTF_8));

        MavenProductInstallService.PomInfo info = MavenProductInstallService.readPomInfo(pom);
        Assert.assertEquals("com.hundsun.lcpt", info.groupId);
        Assert.assertEquals("lcpt-core", info.artifactId);
        Assert.assertEquals("7.4.0-SNAPSHOT", info.version);
    }

    @Test
    public void parseIgnoresDependencyGroupId() throws Exception {
        Path dir = Files.createTempDirectory("pom-parse-dep");
        Path pom = dir.resolve("pom.xml");
        String xml = "<project>\n"
                + "  <parent>\n"
                + "    <groupId>com.hundsun.lcpt</groupId>\n"
                + "    <artifactId>lcpt-jres</artifactId>\n"
                + "    <version>7.4.0-SNAPSHOT</version>\n"
                + "  </parent>\n"
                + "  <artifactId>lcpt-core</artifactId>\n"
                + "  <version>7.4.0-SNAPSHOT</version>\n"
                + "  <dependencies>\n"
                + "    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis</artifactId></dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        Files.write(pom, xml.getBytes(StandardCharsets.UTF_8));

        MavenProductInstallService.PomInfo info = MavenProductInstallService.readPomInfo(pom);
        Assert.assertEquals("com.hundsun.lcpt", info.groupId);
        Assert.assertEquals("lcpt-core", info.artifactId);
        Assert.assertEquals("7.4.0-SNAPSHOT", info.version);
    }

    @Test
    public void writeProjectVersionDoesNotTouchParentOrDeps() throws Exception {
        Path dir = Files.createTempDirectory("pom-write");
        Path pom = dir.resolve("pom.xml");
        String xml = "<project>\n"
                + "  <parent>\n"
                + "    <groupId>com.hundsun.lcpt</groupId>\n"
                + "    <artifactId>lcpt-jres</artifactId>\n"
                + "    <version>7.4.0-SNAPSHOT</version>\n"
                + "  </parent>\n"
                + "  <artifactId>lcpt-core</artifactId>\n"
                + "  <version>7.4.0-SNAPSHOT</version>\n"
                + "  <dependencies>\n"
                + "    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis</artifactId>"
                + "<version>1.0</version></dependency>\n"
                + "  </dependencies>\n"
                + "</project>\n";
        Files.write(pom, xml.getBytes(StandardCharsets.UTF_8));

        MavenProductInstallService.writeProjectVersion(pom, "7.5.0-SNAPSHOT");
        String after = new String(Files.readAllBytes(pom), StandardCharsets.UTF_8);
        Assert.assertTrue(after.contains("<parent>") && after.contains("<version>7.4.0-SNAPSHOT</version>"));
        Assert.assertTrue(after.contains("<artifactId>lcpt-core</artifactId>"));
        Assert.assertTrue(after.contains("<version>7.5.0-SNAPSHOT</version>"));
        Assert.assertTrue(after.contains("<version>1.0</version>"));
        Assert.assertEquals("7.5.0-SNAPSHOT", MavenProductInstallService.readPomInfo(pom).version);

        MavenProductInstallService.writeProjectVersion(pom, "7.4.0-SNAPSHOT");
        Assert.assertEquals("7.4.0-SNAPSHOT", MavenProductInstallService.readPomInfo(pom).version);
    }

    @Test
    public void parseInheritVersionFromParent() throws Exception {
        Path dir = Files.createTempDirectory("pom-parse2");
        Path pom = dir.resolve("pom.xml");
        String xml = "<project>\n"
                + "  <parent>\n"
                + "    <groupId>com.hundsun.lcpt</groupId>\n"
                + "    <artifactId>parent</artifactId>\n"
                + "    <version>7.5.0-SNAPSHOT</version>\n"
                + "  </parent>\n"
                + "  <artifactId>only-artifact</artifactId>\n"
                + "</project>\n";
        Files.write(pom, xml.getBytes(StandardCharsets.UTF_8));

        MavenProductInstallService.PomInfo info = MavenProductInstallService.readPomInfo(pom);
        Assert.assertEquals("com.hundsun.lcpt", info.groupId);
        Assert.assertEquals("only-artifact", info.artifactId);
        Assert.assertEquals("7.5.0-SNAPSHOT", info.version);
    }
}
