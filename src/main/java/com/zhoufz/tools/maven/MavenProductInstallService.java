package com.zhoufz.tools.maven;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 产品模块 versions:set → clean install → 改回原版本；可选清空本地目标 GAV 目录。
 *
 * @author zhoufz
 */
@Service
public class MavenProductInstallService {

    private static final Pattern PROJECT_ARTIFACT_ID =
            Pattern.compile("<artifactId>\\s*([^<]+?)\\s*</artifactId>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_GROUP_ID =
            Pattern.compile("<groupId>\\s*([^<]+?)\\s*</groupId>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_VERSION =
            Pattern.compile("<version>\\s*([^<]+?)\\s*</version>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARENT_BLOCK =
            Pattern.compile("<parent>[\\s\\S]*?</parent>", Pattern.CASE_INSENSITIVE);

    private final MavenProductInstallProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MavenProductInstallService(MavenProductInstallProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> defaults() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("modulePath", properties.getDefaultModulePath());
        data.put("targetVersion", properties.getDefaultTargetVersion());
        data.put("settingsPath", properties.getSettingsPath());
        data.put("localRepo", properties.getLocalRepo());
        data.put("allowedGroupId", properties.getAllowedGroupId());
        data.put("timeoutMs", properties.getTimeoutMs());
        data.put("extraMavenArgs", "-DskipTests");
        data.put("clearTargetDir", true);
        data.put("restoreOriginalVersion", true);
        data.put("offline", false);
        data.put("runClean", false);
        data.put("noSnapshotUpdates", true);
        return data;
    }

    public Map<String, Object> preview(MavenProductInstallRequest request) throws Exception {
        Context ctx = prepare(request);
        Map<String, Object> data = baseResult(ctx);
        data.put("commands", buildCommandStrings(ctx));
        data.put("willClear", Boolean.TRUE.equals(request.getClearTargetDir()));
        data.put("willRestore", Boolean.TRUE.equals(request.getRestoreOriginalVersion()));
        data.put("clearDirExists", Files.isDirectory(ctx.targetVersionDir));
        data.put("log", Collections.emptyList());
        data.put("executed", false);
        return data;
    }

    public Map<String, Object> execute(MavenProductInstallRequest request) throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("已有产品包 install 任务在执行，请等待完成后再试");
        }
        List<String> log = new ArrayList<>();
        Context ctx = null;
        Map<String, Object> data = new LinkedHashMap<>();
        boolean pipelineOk = false;
        try {
            ctx = prepare(request);
            data.putAll(baseResult(ctx));
            data.put("commands", buildCommandStrings(ctx));
            data.put("executed", true);
            data.put("log", log);

            Map<String, Object> timings = newTimingMap();
            data.put("timings", timings);
            long t0 = System.currentTimeMillis();
            timings.put("startedAt", t0);

            append(log, "==== 开始：产品包 Install（对齐工程包版本） ====");
            append(log, "模块目录: " + ctx.moduleDir);
            append(log, "GAV: " + ctx.groupId + ":" + ctx.artifactId + ":" + ctx.targetVersion
                    + "（原版本 " + ctx.originalVersion + "）");
            append(log, "本地目标目录: " + ctx.targetVersionDir);
            append(log, "策略: 直接改 pom 版本（不跑 versions:set）；"
                    + (ctx.runClean ? "clean install" : "install")
                    + (ctx.noSnapshotUpdates ? " -nsu" : "")
                    + (ctx.offline ? " -o" : ""));

            // 4. 清空目标版本目录
            if (Boolean.TRUE.equals(request.getClearTargetDir())) {
                long t = System.currentTimeMillis();
                clearTargetVersionDir(ctx, log);
                data.put("clearedDir", ctx.clearedDir);
                putTiming(timings, log, "clearDirMs", t, "清空目录");
            } else {
                timings.put("clearDirMs", 0L);
                append(log, "[跳过] 未勾选清空目标版本目录");
            }

            // 5. 直接改 pom → target（避免 versions:set 再启一次 Maven JVM）
            long tSet = System.currentTimeMillis();
            writeProjectVersion(ctx.pomFile, ctx.targetVersion);
            data.put("exitCodeSetTarget", 0);
            putTiming(timings, log, "setTargetMs", tSet, "改版本→目标");

            // 6. install（默认 -nsu，对齐 IDEA 不 Always update snapshots）
            long tInstall = System.currentTimeMillis();
            int codeInstall = runMvn(ctx, log, ctx.runClean ? "clean-install" : "install",
                    installArgs(ctx));
            data.put("exitCodeInstall", codeInstall);
            putTiming(timings, log, "installMs", tInstall, "Maven install");
            if (codeInstall != 0) {
                data.put("success", false);
                finishTimings(timings, log, t0);
                return data;
            }

            // 可选：再清 nexus 元数据与时间戳包（install 后若又出现）
            long tCleanup = System.currentTimeMillis();
            cleanupTimestampArtifacts(ctx, log);
            putTiming(timings, log, "cleanupMs", tCleanup, "清理时间戳/nexus元数据");

            // 7. 改回原版本（同样直接改文件）
            if (Boolean.TRUE.equals(request.getRestoreOriginalVersion())) {
                long tRestore = System.currentTimeMillis();
                writeProjectVersion(ctx.pomFile, ctx.originalVersion);
                data.put("exitCodeRestore", 0);
                putTiming(timings, log, "restoreMs", tRestore, "改版本→原版");
            } else {
                timings.put("restoreMs", 0L);
                append(log, "[跳过] 未勾选改回原版本（失败时 finally 仍会尝试改回）");
            }

            finishTimings(timings, log, t0);
            pipelineOk = true;
            return data;
        } finally {
            try {
                // 失败：务必改回；成功且勾选改回：再校验一次；成功且未勾选：保持目标版本
                boolean needRestore = ctx != null && StringUtils.hasText(ctx.originalVersion)
                        && (!pipelineOk || Boolean.TRUE.equals(request.getRestoreOriginalVersion()));
                if (needRestore) {
                    ensurePomRestored(ctx, log);
                }
                if (ctx != null) {
                    Map<String, Object> checks = buildChecks(ctx, log);
                    data.put("checks", checks);
                    data.put("pomVersionAfter", checks.get("pomVersionNow"));
                    if (pipelineOk) {
                        boolean jarOk = Boolean.TRUE.equals(checks.get("snapshotJarExists"));
                        data.put("success", jarOk);
                    } else if (!data.containsKey("success")) {
                        data.put("success", false);
                    }
                    // 失败早退时也尽量补全合计耗时
                    Object timingsObj = data.get("timings");
                    if (timingsObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> timings = (Map<String, Object>) timingsObj;
                        if (!timings.containsKey("totalMs") && timings.get("startedAt") instanceof Number) {
                            finishTimings(timings, log, ((Number) timings.get("startedAt")).longValue());
                        }
                    }
                    String totalHuman = "-";
                    Object timingsObj2 = data.get("timings");
                    if (timingsObj2 instanceof Map && ((Map<?, ?>) timingsObj2).get("total") != null) {
                        totalHuman = String.valueOf(((Map<?, ?>) timingsObj2).get("total"));
                    }
                    append(log, "==== 结束：success=" + data.get("success")
                            + "；总耗时=" + totalHuman + " ====");
                }
            } catch (Exception e) {
                append(log, "[WARN] finally 收尾失败: " + e.getMessage());
                if (!data.containsKey("success")) {
                    data.put("success", false);
                }
            } finally {
                running.set(false);
            }
        }
    }

    private static Map<String, Object> newTimingMap() {
        Map<String, Object> timings = new LinkedHashMap<>();
        timings.put("clearDirMs", null);
        timings.put("setTargetMs", null);
        timings.put("installMs", null);
        timings.put("cleanupMs", null);
        timings.put("restoreMs", null);
        timings.put("totalMs", null);
        timings.put("clearDir", null);
        timings.put("setTarget", null);
        timings.put("install", null);
        timings.put("cleanup", null);
        timings.put("restore", null);
        timings.put("total", null);
        return timings;
    }

    private static void putTiming(Map<String, Object> timings, List<String> log,
                                  String msKey, long startedAt, String label) {
        long ms = System.currentTimeMillis() - startedAt;
        String human = formatDuration(ms);
        timings.put(msKey, ms);
        if (msKey.endsWith("Ms")) {
            timings.put(msKey.substring(0, msKey.length() - 2), human);
        }
        append(log, "[耗时] " + label + " " + human + "（" + ms + " ms）");
    }

    private static void finishTimings(Map<String, Object> timings, List<String> log, long startedAt) {
        long total = System.currentTimeMillis() - startedAt;
        timings.put("totalMs", total);
        timings.put("total", formatDuration(total));
        timings.put("finishedAt", System.currentTimeMillis());
        append(log, "[耗时] 合计 " + formatDuration(total) + "（" + total + " ms）");
    }

    static String formatDuration(Long ms) {
        if (ms == null || ms < 0) {
            return "-";
        }
        if (ms < 1000) {
            return ms + " ms";
        }
        long sec = ms / 1000;
        if (sec < 60) {
            return sec + " s";
        }
        long min = sec / 60;
        long rem = sec % 60;
        if (min < 60) {
            return min + " min " + rem + " s";
        }
        long hour = min / 60;
        long remMin = min % 60;
        return hour + " h " + remMin + " min";
    }


    private Context prepare(MavenProductInstallRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String modulePathRaw = trim(request.getModulePath());
        if (!StringUtils.hasText(modulePathRaw)) {
            modulePathRaw = properties.getDefaultModulePath();
        }
        if (!StringUtils.hasText(modulePathRaw)) {
            throw new IllegalArgumentException("产品模块路径不能为空");
        }

        Path modulePath = Paths.get(modulePathRaw).toAbsolutePath().normalize();
        Path pomFile;
        Path moduleDir;
        if (Files.isDirectory(modulePath)) {
            moduleDir = modulePath;
            pomFile = moduleDir.resolve("pom.xml");
        } else if (Files.isRegularFile(modulePath)
                && modulePath.getFileName().toString().equalsIgnoreCase("pom.xml")) {
            pomFile = modulePath;
            moduleDir = pomFile.getParent();
        } else {
            throw new IllegalArgumentException("模块路径须为目录或 pom.xml 文件：" + modulePath);
        }
        if (!Files.isRegularFile(pomFile)) {
            throw new IllegalArgumentException("找不到 pom.xml：" + pomFile);
        }

        String targetVersion = trim(request.getTargetVersion());
        if (!StringUtils.hasText(targetVersion)) {
            targetVersion = properties.getDefaultTargetVersion();
        }
        if (!StringUtils.hasText(targetVersion)) {
            throw new IllegalArgumentException("目标版本 targetVersion 不能为空");
        }
        if (targetVersion.contains("..") || targetVersion.contains("/") || targetVersion.contains("\\")) {
            throw new IllegalArgumentException("非法目标版本：" + targetVersion);
        }

        PomInfo pomInfo = readPomInfo(pomFile);
        if (!StringUtils.hasText(pomInfo.artifactId)) {
            throw new IllegalArgumentException("无法从 pom 解析 artifactId：" + pomFile);
        }
        if (!StringUtils.hasText(pomInfo.groupId)) {
            throw new IllegalArgumentException("无法从 pom 解析 groupId：" + pomFile);
        }

        String allowed = trim(properties.getAllowedGroupId());
        if (StringUtils.hasText(allowed)
                && !allowed.equals(pomInfo.groupId)
                && !Boolean.TRUE.equals(request.getAllowOtherGroupId())) {
            throw new IllegalArgumentException(
                    "groupId=" + pomInfo.groupId + " 不在允许范围（默认 " + allowed
                            + "）。若确认无误请勾选「允许其他 groupId」");
        }

        String originalVersion = trim(request.getOriginalVersion());
        if (!StringUtils.hasText(originalVersion)) {
            originalVersion = pomInfo.version;
        }
        if (!StringUtils.hasText(originalVersion)) {
            throw new IllegalArgumentException("无法推断原版本 originalVersion，请手动填写");
        }

        String settingsPath = firstNonBlank(request.getSettingsPath(), properties.getSettingsPath());
        Path settings = Paths.get(settingsPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(settings)) {
            throw new IllegalArgumentException("Maven settings 不存在：" + settings);
        }

        String localRepoRaw = firstNonBlank(request.getLocalRepo(), properties.getLocalRepo());
        Path localRepo = Paths.get(localRepoRaw).toAbsolutePath().normalize();
        if (!Files.isDirectory(localRepo)) {
            throw new IllegalArgumentException("本地仓库目录不存在：" + localRepo);
        }
        validateLocalRepoBoundary(localRepo);

        Path targetVersionDir = resolveTargetVersionDir(localRepo, pomInfo.groupId, pomInfo.artifactId, targetVersion);
        validateDeleteScope(localRepo, targetVersionDir, pomInfo.artifactId, targetVersion);

        Context ctx = new Context();
        ctx.pomFile = pomFile;
        ctx.moduleDir = moduleDir;
        ctx.groupId = pomInfo.groupId;
        ctx.artifactId = pomInfo.artifactId;
        ctx.pomCurrentVersion = pomInfo.version;
        ctx.originalVersion = originalVersion;
        ctx.targetVersion = targetVersion;
        ctx.settingsPath = settings;
        ctx.localRepo = localRepo;
        ctx.targetVersionDir = targetVersionDir;
        ctx.extraMavenArgs = trim(request.getExtraMavenArgs());
        if (!StringUtils.hasText(ctx.extraMavenArgs)) {
            ctx.extraMavenArgs = "-DskipTests";
        }
        ctx.offline = Boolean.TRUE.equals(request.getOffline());
        // null 当 false/true 的默认：runClean 默认 false；noSnapshotUpdates 默认 true
        ctx.runClean = Boolean.TRUE.equals(request.getRunClean());
        ctx.noSnapshotUpdates = request.getNoSnapshotUpdates() == null
                || Boolean.TRUE.equals(request.getNoSnapshotUpdates());
        return ctx;
    }

    /**
     * 提交的 localRepo 必须等于配置仓库根，或其规范化后的子路径。
     */
    private void validateLocalRepoBoundary(Path localRepo) throws IOException {
        Path configured = Paths.get(properties.getLocalRepo()).toAbsolutePath().normalize();
        Path canonicalLocal = localRepo.toRealPath();
        Path canonicalConfigured;
        if (Files.exists(configured)) {
            canonicalConfigured = configured.toRealPath();
        } else {
            canonicalConfigured = configured;
        }
        if (canonicalLocal.equals(canonicalConfigured)) {
            return;
        }
        if (!canonicalLocal.startsWith(canonicalConfigured)) {
            throw new IllegalArgumentException(
                    "localRepo 必须等于配置仓库根或其子路径。配置根=" + canonicalConfigured
                            + "，请求=" + canonicalLocal);
        }
    }

    private Path resolveTargetVersionDir(Path localRepo, String groupId, String artifactId, String version) {
        Path dir = localRepo;
        for (String part : groupId.split("\\.")) {
            dir = dir.resolve(part);
        }
        dir = dir.resolve(artifactId).resolve(version);
        return dir.normalize();
    }

    private void validateDeleteScope(Path localRepo, Path targetVersionDir,
                                     String artifactId, String targetVersion) throws IOException {
        Path repoReal = localRepo.toRealPath();
        Path normalizedTarget = targetVersionDir.normalize();
        if (!normalizedTarget.startsWith(localRepo.normalize())) {
            throw new IllegalArgumentException("目标目录不在 localRepo 下：" + normalizedTarget);
        }
        // 目录可能尚不存在：用 parent 校验
        Path parent = normalizedTarget.getParent();
        if (parent == null || !artifactId.equals(parent.getFileName().toString())) {
            throw new IllegalArgumentException("目标目录父级必须是 artifactId 目录：" + normalizedTarget);
        }
        if (!targetVersion.equals(normalizedTarget.getFileName().toString())) {
            throw new IllegalArgumentException("目标目录名必须等于 targetVersion：" + normalizedTarget);
        }
        // 禁止删到 artifactId 上级：target 相对 repo 的深度至少 group段 + artifact + version
        Path relative;
        try {
            relative = repoReal.relativize(
                    Files.exists(normalizedTarget) ? normalizedTarget.toRealPath() : normalizedTarget);
        } catch (Exception e) {
            relative = localRepo.normalize().relativize(normalizedTarget);
        }
        if (relative.getNameCount() < 3) {
            throw new IllegalArgumentException("拒绝删除：路径层级过浅，疑似会伤及仓库根：" + normalizedTarget);
        }
        // 再确认 relative 最后两段
        int n = relative.getNameCount();
        if (!artifactId.equals(relative.getName(n - 2).toString())
                || !targetVersion.equals(relative.getName(n - 1).toString())) {
            throw new IllegalArgumentException("拒绝删除：路径未精确落在 artifactId/targetVersion：" + normalizedTarget);
        }
    }

    private void clearTargetVersionDir(Context ctx, List<String> log) throws IOException {
        Path dir = ctx.targetVersionDir;
        append(log, "[清空] " + dir);
        if (Files.exists(dir)) {
            deleteRecursively(dir);
        }
        Files.createDirectories(dir);
        ctx.clearedDir = dir.toString();
        append(log, "[清空] 已重建空目录");
    }

    private void cleanupTimestampArtifacts(Context ctx, List<String> log) throws IOException {
        Path dir = ctx.targetVersionDir;
        if (!Files.isDirectory(dir)) {
            return;
        }
        String baseVersion = stripSnapshot(ctx.targetVersion);
        Pattern tsJar = timestampPattern(ctx.artifactId, baseVersion, "jar");
        Pattern tsPom = timestampPattern(ctx.artifactId, baseVersion, "pom");
        Pattern tsSha = Pattern.compile(
                Pattern.quote(ctx.artifactId + "-" + baseVersion + "-") + "20\\d{6}\\.\\d{6}.*");

        List<Path> toDelete = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if ("maven-metadata-nexus.xml".equals(name)
                        || "maven-metadata-nexus.xml.sha1".equals(name)
                        || "resolver-status.properties".equals(name)) {
                    toDelete.add(p);
                    continue;
                }
                if (tsJar.matcher(name).matches()
                        || tsPom.matcher(name).matches()
                        || (name.endsWith(".sha1") && tsSha.matcher(name).matches())) {
                    toDelete.add(p);
                }
            }
        }
        for (Path p : toDelete) {
            Files.deleteIfExists(p);
            append(log, "[清理时间戳/nexus元数据] " + p.getFileName());
        }
    }

    private Map<String, Object> buildChecks(Context ctx, List<String> log) throws IOException {
        Map<String, Object> checks = new LinkedHashMap<>();
        Path jar = ctx.targetVersionDir.resolve(ctx.artifactId + "-" + ctx.targetVersion + ".jar");
        boolean jarExists = Files.isRegularFile(jar);
        long jarSize = jarExists ? Files.size(jar) : -1L;
        checks.put("snapshotJarPath", jar.toString());
        checks.put("snapshotJarExists", jarExists);
        checks.put("snapshotJarSize", jarSize);

        String baseVersion = stripSnapshot(ctx.targetVersion);
        Pattern tsJar = timestampPattern(ctx.artifactId, baseVersion, "jar");
        List<String> timestampJars = new ArrayList<>();
        if (Files.isDirectory(ctx.targetVersionDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(ctx.targetVersionDir)) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    if (tsJar.matcher(name).matches()) {
                        timestampJars.add(name);
                    }
                }
            }
        }
        checks.put("timestampJars", timestampJars);
        checks.put("noTimestampJar", timestampJars.isEmpty());

        PomInfo after = readPomInfo(ctx.pomFile);
        checks.put("pomVersionNow", after.version);
        checks.put("pomRestored", ctx.originalVersion.equals(after.version));

        append(log, "[校验] SNAPSHOT jar 存在=" + jarExists
                + (jarExists ? " size=" + jarSize : "")
                + "；时间戳包数=" + timestampJars.size()
                + "；pom 当前 version=" + after.version);
        return checks;
    }

    private void ensurePomRestored(Context ctx, List<String> log) {
        try {
            PomInfo now = readPomInfo(ctx.pomFile);
            if (ctx.originalVersion.equals(now.version)) {
                append(log, "[finally] pom 已是原版本 " + ctx.originalVersion);
                return;
            }
            append(log, "[finally] pom 当前=" + now.version + "，直接改回 " + ctx.originalVersion);
            writeProjectVersion(ctx.pomFile, ctx.originalVersion);
            append(log, "[finally] 已写回 " + readPomInfo(ctx.pomFile).version);
        } catch (Exception e) {
            append(log, "[finally] 恢复异常: " + e.getMessage());
        }
    }

    /**
     * 只改项目自身 version（不碰 parent / dependencies），替代慢的 versions:set。
     */
    public static void writeProjectVersion(Path pomFile, String newVersion) throws IOException {
        if (!StringUtils.hasText(newVersion)) {
            throw new IllegalArgumentException("newVersion 不能为空");
        }
        String xml = new String(Files.readAllBytes(pomFile), StandardCharsets.UTF_8);
        Matcher parentMatcher = PARENT_BLOCK.matcher(xml);
        int searchFrom = 0;
        if (parentMatcher.find()) {
            searchFrom = parentMatcher.end();
        }
        int searchTo = xml.length();
        // 仅用坐标之后常见大块作为终点（不把 properties/packaging 当终点，它们可能在 version 前）
        String[] blockEnds = {
                "dependencies", "dependencyManagement", "build", "reporting",
                "profiles", "modules"
        };
        for (String tag : blockEnds) {
            int idx = indexOfIgnoreCase(xml, "<" + tag, searchFrom);
            if (idx >= 0 && idx < searchTo) {
                searchTo = idx;
            }
        }
        String head = xml.substring(0, searchFrom);
        String mid = xml.substring(searchFrom, searchTo);
        String tail = xml.substring(searchTo);

        Matcher versionMatcher = PROJECT_VERSION.matcher(mid);
        if (versionMatcher.find()) {
            mid = mid.substring(0, versionMatcher.start())
                    + "<version>" + newVersion + "</version>"
                    + mid.substring(versionMatcher.end());
        } else {
            Matcher artifactMatcher = PROJECT_ARTIFACT_ID.matcher(mid);
            if (!artifactMatcher.find()) {
                throw new IllegalStateException("无法在 pom 坐标区定位 artifactId，无法写入 version：" + pomFile);
            }
            mid = mid.substring(0, artifactMatcher.end())
                    + "\n    <version>" + newVersion + "</version>"
                    + mid.substring(artifactMatcher.end());
        }
        Files.write(pomFile, (head + mid + tail).getBytes(StandardCharsets.UTF_8));
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        final int max = haystack.length() - needle.length();
        for (int i = from; i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private List<String> installArgs(Context ctx) {
        List<String> args = baseMvnArgs(ctx);
        if (ctx.offline) {
            args.add("-o");
        }
        if (ctx.noSnapshotUpdates) {
            args.add("-nsu");
        }
        if (StringUtils.hasText(ctx.extraMavenArgs)) {
            for (String token : ctx.extraMavenArgs.trim().split("\\s+")) {
                if (StringUtils.hasText(token)) {
                    args.add(token);
                }
            }
        }
        if (ctx.runClean) {
            args.add("clean");
        }
        args.add("install");
        return args;
    }

    private List<String> baseMvnArgs(Context ctx) {
        List<String> args = new ArrayList<>();
        args.add(resolveMvn());
        // -B：非交互批处理，避免卡在提示输入导致页面“一直转、后台无日志”
        args.add("-B");
        args.add("-s");
        args.add(ctx.settingsPath.toString());
        args.add("--global-settings");
        args.add(ctx.settingsPath.toString());
        return args;
    }

    private List<String> buildCommandStrings(Context ctx) {
        List<String> commands = new ArrayList<>();
        commands.add("rm -rf " + ctx.targetVersionDir + " && mkdir -p " + ctx.targetVersionDir
                + "   # 仅当勾选清空");
        commands.add("# 直接改 pom project.version → " + ctx.targetVersion
                + "（不跑 mvn versions:set）");
        commands.add(joinCommand(installArgs(ctx)));
        commands.add("# 直接改 pom project.version → " + ctx.originalVersion + "   # 仅当勾选改回");
        return commands;
    }

    private int runMvn(Context ctx, List<String> log, String step, List<String> args) throws Exception {
        append(log, "---- [" + step + "] " + joinCommand(args));
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(ctx.moduleDir.toFile());
        pb.redirectErrorStream(true);
        // 显式不继承控制台 stdin，避免 Maven 卡在等待输入
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        Map<String, String> env = pb.environment();
        String path = env.get("PATH");
        String extra = "/opt/homebrew/bin" + java.io.File.pathSeparator + "/usr/local/bin"
                + java.io.File.pathSeparator + "/usr/bin" + java.io.File.pathSeparator + "/bin";
        env.put("PATH", extra + (path == null || path.isEmpty() ? "" : java.io.File.pathSeparator + path));
        // 降低交互可能：CI 风格
        env.put("MAVEN_OPTS", trimToEmpty(env.get("MAVEN_OPTS")));

        Process process = pb.start();
        // 关闭子进程 stdin，杜绝“Waiting for input”
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // ignore
        }

        // 必须异步读 stdout：若在主线程 readLine 阻塞，超时 waitFor 永远进不去
        final StringBuilder out = new StringBuilder();
        Thread gobbler = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (out) {
                        out.append(line).append('\n');
                    }
                    // 同步打到 IDEA 控制台，避免“后台没日志”的错觉
                    System.out.println("[product-install][" + step + "] " + line);
                    synchronized (log) {
                        if (log.size() < 5000) {
                            log.add(line);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("[product-install][" + step + "] 读输出中断: " + e.getMessage());
            }
        }, "mvn-gobbler-" + step);
        gobbler.setDaemon(true);
        gobbler.start();

        boolean finished = process.waitFor(properties.getTimeoutMs(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            gobbler.join(3000);
            append(log, "[ERROR] [" + step + "] 超时（" + properties.getTimeoutMs() + " ms），已强制终止");
            return -1;
        }
        gobbler.join(10000);
        int code = process.exitValue();
        append(log, "[" + step + "] exitCode=" + code);
        if (code != 0) {
            String all;
            synchronized (out) {
                all = out.toString();
            }
            if (all.length() > 0 && log.size() >= 5000) {
                int from = Math.max(0, all.length() - 4000);
                append(log, "...(日志截断，尾部)...\n" + all.substring(from));
            }
        }
        return code;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private Map<String, Object> baseResult(Context ctx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", ctx.groupId);
        data.put("artifactId", ctx.artifactId);
        data.put("originalVersion", ctx.originalVersion);
        data.put("targetVersion", ctx.targetVersion);
        data.put("pomCurrentVersion", ctx.pomCurrentVersion);
        data.put("moduleDir", ctx.moduleDir.toString());
        data.put("pomFile", ctx.pomFile.toString());
        data.put("settingsPath", ctx.settingsPath.toString());
        data.put("localRepo", ctx.localRepo.toString());
        data.put("targetVersionDir", ctx.targetVersionDir.toString());
        data.put("snapshotJarPath",
                ctx.targetVersionDir.resolve(ctx.artifactId + "-" + ctx.targetVersion + ".jar").toString());
        return data;
    }

    public static PomInfo readPomInfo(Path pomFile) throws IOException {
        String xml = new String(Files.readAllBytes(pomFile), StandardCharsets.UTF_8);
        // 去掉注释，减少误匹配
        xml = xml.replaceAll("(?s)<!--.*?-->", "");

        String parentGroupId = null;
        String parentVersion = null;
        Matcher parentMatcher = PARENT_BLOCK.matcher(xml);
        String withoutParent = xml;
        if (parentMatcher.find()) {
            String parentXml = parentMatcher.group();
            parentGroupId = firstMatch(PROJECT_GROUP_ID, parentXml);
            parentVersion = firstMatch(PROJECT_VERSION, parentXml);
            withoutParent = parentMatcher.replaceFirst(" ");
        }

        // 只解析项目坐标区，避免命中 dependencies / plugins 里的 GAV
        String coordArea = stripSecondaryBlocks(withoutParent);

        String artifactId = firstMatch(PROJECT_ARTIFACT_ID, coordArea);
        String groupId = firstMatch(PROJECT_GROUP_ID, coordArea);
        String version = firstMatch(PROJECT_VERSION, coordArea);
        if (!StringUtils.hasText(groupId)) {
            groupId = parentGroupId;
        }
        if (!StringUtils.hasText(version)) {
            version = parentVersion;
        }

        PomInfo info = new PomInfo();
        info.groupId = groupId == null ? "" : groupId.trim();
        info.artifactId = artifactId == null ? "" : artifactId.trim();
        info.version = version == null ? "" : version.trim();
        return info;
    }

    /** 去掉会干扰项目级 GAV 解析的大块。 */
    private static String stripSecondaryBlocks(String xml) {
        String[] tags = {
                "dependencies", "dependencyManagement", "build", "reporting",
                "profiles", "pluginRepositories", "repositories", "distributionManagement",
                "developers", "contributors", "licenses", "scm", "issueManagement",
                "ciManagement", "mailingLists", "modules"
        };
        String result = xml;
        for (String tag : tags) {
            result = result.replaceAll("(?is)<" + tag + "\\b[^>]*>.*?</" + tag + ">", " ");
        }
        return result;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static Pattern timestampPattern(String artifactId, String baseVersion, String ext) {
        return Pattern.compile(
                "^" + Pattern.quote(artifactId + "-" + baseVersion + "-")
                        + "20\\d{6}\\.\\d{6}.*\\." + Pattern.quote(ext) + "$");
    }

    private static String stripSnapshot(String version) {
        if (version != null && version.endsWith("-SNAPSHOT")) {
            return version.substring(0, version.length() - "-SNAPSHOT".length());
        }
        return version;
    }

    private String resolveMvn() {
        String configured = trim(properties.getMvnExecutable());
        if (StringUtils.hasText(configured) && !"mvn".equals(configured)) {
            return configured;
        }
        // 优先绝对路径，避免 IDEA 启动的 JVM PATH 里找不到 mvn 却静默异常/挂起
        String[] candidates = {
                configured,
                "/opt/homebrew/bin/mvn",
                "/usr/local/bin/mvn",
                "mvn"
        };
        for (String c : candidates) {
            if (!StringUtils.hasText(c)) {
                continue;
            }
            if ("mvn".equals(c)) {
                return c;
            }
            if (Files.isExecutable(Paths.get(c))) {
                return c;
            }
        }
        return "mvn";
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void append(List<String> log, String line) {
        System.out.println("[product-install] " + line);
        synchronized (log) {
            log.add(line);
        }
    }

    private static String joinCommand(List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (String a : args) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (a.indexOf(' ') >= 0) {
                sb.append('"').append(a).append('"');
            } else {
                sb.append(a);
            }
        }
        return sb.toString();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        return b;
    }

    public static final class PomInfo {
        public String groupId;
        public String artifactId;
        public String version;
    }

    private static final class Context {
        Path pomFile;
        Path moduleDir;
        String groupId;
        String artifactId;
        String pomCurrentVersion;
        String originalVersion;
        String targetVersion;
        Path settingsPath;
        Path localRepo;
        Path targetVersionDir;
        String extraMavenArgs;
        boolean offline;
        boolean runClean;
        boolean noSnapshotUpdates;
        String clearedDir;
    }
}
