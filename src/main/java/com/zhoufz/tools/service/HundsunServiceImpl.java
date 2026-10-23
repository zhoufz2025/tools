package com.zhoufz.tools.service;

import com.zhoufz.tools.util.ExcelUtil;
import com.zhoufz.tools.util.FileUtil;
import com.zhoufz.tools.util.GitUtil;
import com.zhoufz.tools.util.SvnUtil;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */
@Service
public class HundsunServiceImpl {

    public static List<String> IFMCOUNTER_LIST = Arrays.asList(
            "ifmcounter","ifmcounter-dxasset","ifmcounter-dxfund","ifmcounter-dxtrust");

    public static List<String> HUI_LIST = Arrays.asList(
            "HUI1.0", "console-dxasset-vue", "console-dxfund-vue", "console-dxtrust-vue", "console-fina-vue");

    /** spsql 路径中的版本号，如 IFMS6.0V202607.02.000、IFMS6.0V202405.05.056M2 */
    private static final Pattern SPSQL_VERSION_PATTERN =
            Pattern.compile("IFMS6\\.0V\\d{6}\\.\\d{2}\\.\\d{3}[A-Za-z0-9]*");

    /**
     * 按 gitReplaceFile.txt 将源文件复制到目标工作区。
     * <p>配置格式：第1行 源目录前缀=有|无；第2行 目标根目录（如 F:\提交\Sources）；
     * 第3行 taskId:xxx；第4行 verion:xxx；第5行起为文件路径。
     * <p>目标路径按模块映射：app→{目标根}\app\...，前端→{目标根}\lcpt-front\...，低柜→{目标根}\ifmcounter\...，
     * 相对路径与原文件在各自模块下的目录结构一致。
     */
    public void gitReplaceSqlFile(String fileName) {
        List<String> list = readReplaceConfig(fileName);
        if (list == null || list.size() < 5) {
            throw new IllegalArgumentException("配置文件格式错误，至少需要5行");
        }
        boolean useSourcePrefix = true;
        String sourcePrefix = list.get(0).trim();
        if (sourcePrefix.contains("=")) {
            String flag = sourcePrefix.substring(sourcePrefix.indexOf('=') + 1).trim();
            sourcePrefix = sourcePrefix.substring(0, sourcePrefix.indexOf('=')).trim();
            useSourcePrefix = "有".equals(flag);
        }
        String targetBase = normalizeGitPath(list.get(1));
        String taskId = extractConfigValue(list.get(2));
        String version = extractConfigValue(list.get(3));
        try {
            for (int i = 4; i < list.size(); i++) {
                String readLine = list.get(i).trim();
                if (readLine.isEmpty()) {
                    continue;
                }
                File source = new File(resolveGitSourcePath(readLine, sourcePrefix, useSourcePrefix));
                File target = new File(resolveGitTargetPath(source.getAbsolutePath(), targetBase, version));
                System.out.println("源: " + source.getAbsolutePath());
                System.out.println("目标: " + target.getAbsolutePath());
                if (isSpsqlPath(source.getAbsolutePath())) {
                    FileUtil.replaceSql(target, source, taskId);
                } else {
                    FileUtil.replace(target, source);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("gitReplaceSqlFile 执行失败", e);
        }
    }

    /**
     * 优先读本机绝对路径；否则按 classpath 资源名读取（与 {@link FileUtil#readFile} 一致）。
     */
    private List<String> readReplaceConfig(String fileName) {
        if (Strings.isBlank(fileName)) {
            throw new IllegalArgumentException("配置文件不能为空");
        }
        File file = new File(fileName.trim());
        if (file.isFile()) {
            try {
                List<String> list = new ArrayList<>();
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    if (line.contains("/") && !"/".equals(File.separator)) {
                        line = line.replace("/", "\\");
                    }
                    if (line.contains("\\") && !"\\".equals(File.separator)) {
                        line = line.replace("\\", "/");
                    }
                    list.add(line);
                }
                return list;
            } catch (IOException e) {
                throw new RuntimeException("读取配置文件失败: " + file.getAbsolutePath(), e);
            }
        }
        List<String> classpath = FileUtil.readFile(fileName.trim());
        if (classpath == null) {
            throw new IllegalArgumentException("找不到配置文件: " + fileName);
        }
        return classpath;
    }

    private static String extractConfigValue(String line) {
        int colon = line.indexOf(':');
        return colon >= 0 ? line.substring(colon + 1).trim() : line.trim();
    }

    private static String normalizeGitPath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim().replace('/', File.separatorChar);
        while (normalized.endsWith(File.separator)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String resolveGitSourcePath(String line, String sourcePrefix, boolean useSourcePrefix) {
        if (!useSourcePrefix) {
            return normalizeGitPath(line);
        }
        String prefix = normalizeGitPath(sourcePrefix);
        String relative = line.trim().replace('/', File.separatorChar);
        while (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        return prefix + File.separator + relative;
    }

    private static String resolveGitTargetPath(String sourcePath, String targetBase, String targetVersion) {
        String normalizedSource = normalizeGitPath(sourcePath);
        String base = normalizeGitPath(targetBase);
        String relativePath = extractGitModuleRelativePath(normalizedSource);
        if (relativePath == null) {
            throw new IllegalArgumentException("无法识别文件所属模块(app/lcpt-front/ifmcounter): " + sourcePath);
        }
        String moduleRoot = resolveGitTargetModuleRoot(normalizedSource);
        String targetPath = base + File.separator + moduleRoot + File.separator + relativePath;
        return applySpsqlVersionReplace(targetPath, targetVersion);
    }

    private static String resolveGitTargetModuleRoot(String sourcePath) {
        String lower = sourcePath.toLowerCase();
        if (lower.contains("\\ifmcounter\\") || lower.contains("\\ifmcounter-")) {
            return "ifmcounter";
        }
        if (lower.contains("\\lcpt-front\\")) {
            return "lcpt-front";
        }
        return "app";
    }

    private static String extractGitModuleRelativePath(String sourcePath) {
        String lower = sourcePath.toLowerCase();
        String ifmcounterMarker = "\\ifmcounter\\";
        int ifmcounterIdx = lower.indexOf(ifmcounterMarker);
        if (ifmcounterIdx >= 0) {
            return sourcePath.substring(ifmcounterIdx + ifmcounterMarker.length());
        }
        String frontMarker = "\\lcpt-front\\";
        int frontIdx = lower.indexOf(frontMarker);
        if (frontIdx >= 0) {
            return sourcePath.substring(frontIdx + frontMarker.length());
        }
        String appMarker = "\\sources\\app\\";
        int appIdx = lower.indexOf(appMarker);
        if (appIdx >= 0) {
            return sourcePath.substring(appIdx + appMarker.length());
        }
        String appShortMarker = "\\app\\";
        appIdx = lower.indexOf(appShortMarker);
        if (appIdx >= 0) {
            return sourcePath.substring(appIdx + appShortMarker.length());
        }
        return null;
    }

    private static boolean isSpsqlPath(String path) {
        return path != null && path.toLowerCase().contains("\\spsql\\");
    }

    private static String applySpsqlVersionReplace(String path, String targetVersion) {
        if (Strings.isBlank(targetVersion) || !isSpsqlPath(path)) {
            return path;
        }
        return SPSQL_VERSION_PATTERN.matcher(path).replaceAll(targetVersion);
    }
    
    public void svnReplaceSqlFile(String fileName) {
        List<String> list = readReplaceConfig(fileName);
        if (list.size() < 6) {
            throw new IllegalArgumentException("SVN 替换配置至少需要 6 行");
        }
        String sourcePrefix = list.get(0), targetPrefix = list.get(1), scriptPrefix = list.get(2), vuePrefix = list.get(3);
        String taskId = list.get(4);
        taskId = taskId.substring(taskId.indexOf(":") + 1);
        File target;
        File source;
        try {
            for (int i = 5; i < list.size(); i++) {
                String readLine = list.get(i);
                if (readLine.contains("lcpt-dxfund") || readLine.contains("lcpt-dxasset") || readLine.contains("lcpt-dxtrust") || readLine.contains("lcpt-insure") || readLine.contains("lcpt-pub")) {
                    target = new File(targetPrefix + readLine.substring(readLine.indexOf("sale")+4));
                    if (sourcePrefix.contains("小包")) {
                        source = new File(sourcePrefix+ readLine.substring(readLine.indexOf("sale")+4));
                    }else {
                        source = new File(sourcePrefix+ readLine);
                    }
                    FileUtil.replace(target, source);
                }
                if (readLine.contains("lcpt-web")){
                    target = new File(targetPrefix + readLine.substring(readLine.indexOf("sale")+4));
                    if (sourcePrefix.contains("小包")) {
                        source = new File(sourcePrefix+ readLine.substring(readLine.indexOf("sale")+4));
                    }else {
                        source = new File(sourcePrefix+ readLine);
                    }
                    FileUtil.replace(target, source);
                }
                if (readLine.contains("spsql")){
                    if (StringUtils.isEmpty(scriptPrefix)) {
                        throw new RuntimeException("脚本地址前缀没有");
                    }
                    String targetVersion = scriptPrefix.substring(scriptPrefix.lastIndexOf("\\")+1);
                    String sourceVersion = readLine.substring(readLine.lastIndexOf("\\")+1,readLine.lastIndexOf("\\")+1+"IFMS6.0V202506.00.000".length());
                    System.out.println(readLine.replace(sourceVersion,targetVersion));
                    if (readLine.contains("pub")) {
                        target = new File(scriptPrefix + readLine.substring(readLine.indexOf("pub")-1).replace(sourceVersion,targetVersion));
            
                        if (sourcePrefix.contains("小包")) {
                            source = new File(sourcePrefix+ readLine.substring(readLine.indexOf("pub")-1));
                        }else {
                            source = new File(sourcePrefix+ readLine);
                        }
            
                        FileUtil.replaceSql(target, source,taskId);
                    }
                    if (readLine.contains("trans")) {
                        target = new File(scriptPrefix + readLine.substring(readLine.indexOf("trans")-1).replace(sourceVersion,targetVersion));
                        if (sourcePrefix.contains("小包")) {
                            source = new File(sourcePrefix+ readLine.substring(readLine.indexOf("trans")-1));
                        }else{
                            source = new File(sourcePrefix+ readLine);
                        }
                        FileUtil.replaceSql(target, source, taskId);
                    }
                }
                if (readLine.contains("console")) {
                    if (StringUtils.isEmpty(vuePrefix)) {
                        throw new RuntimeException("VUE地址前缀没有");
                    }
                    target = new File(vuePrefix + readLine.substring(readLine.lastIndexOf("biz") + 3));
                    if (sourcePrefix.contains("小包")) {
                        source = new File(sourcePrefix +"\\lcpt-front\\biz"+ readLine.substring(readLine.lastIndexOf("biz") + 3));
                    } else {
                        source = new File("F:\\GITIFMS6.0"+readLine);
                    }
                    FileUtil.replace(target, source);
                }
                if (readLine.contains("ifmcounter")){
                    
                    if (sourcePrefix.contains("小包")) {
                        String sourceFileName = readLine.replace(readLine.substring(readLine.indexOf("ifmcounter") + 11, readLine.indexOf("WebContent") - 1), "ifmcounter");
    
                        // System.out.println(sourcePrefix+ sourceFileName.substring(sourceFileName.indexOf("ifmcounter") + 10));
                        source = new File(sourcePrefix+ sourceFileName.substring(sourceFileName.indexOf("ifmcounter") + 10));
                    }else {
                        source = new File(sourcePrefix+ readLine);
                    }
                    String targetFileName = readLine.replace(readLine.substring(readLine.indexOf("ifmcounter") + 11, readLine.indexOf("WebContent") - 1), "ifmcounter");
                    // System.out.println(targetPrefix+targetFileName.substring(targetFileName.indexOf("ifmcounter") + 10));
                    target = new File(targetPrefix+targetFileName.substring(targetFileName.indexOf("ifmcounter") + 10));
                    FileUtil.replace(target, source);
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    public void replaceSqlFile() {

    }

    public void replaceConfigFile() {

    }


    public void saveConfigFile(String basePath) {

    }

    public void getGitSourceCode(String basePath) {
        if (Strings.isBlank(basePath)) {
            return;
        }
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        initGitSourcePath(basePath);
        // 获取下载地址
        Map<String, String> map = ExcelUtil.readExcel("git.xlsx");
        ArrayBlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<>(50);
        int corePoolSize = 10, maxPoolSize = 10, keepAliveTime = 30;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                blockingQueue);
        String basePathTemp = basePath;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        map.forEach((key, value) -> {
            List<String> list1 = Arrays.asList("HUI1.0", "console-dxasset-vue", "console-dxfund-vue",
                    "console-dxtrust-vue", "console-fina-vue",
                    "ifmcounter","ifmcounter-dxasset","ifmcounter-dxfund","ifmcounter-dxtrust");
            if (list1.contains(key)) {
                return;
            }
            futures.add(CompletableFuture.runAsync(() -> {
                List<String> list = Arrays.asList("lib", "shell", "sql-bank");
                File file = new File(basePathTemp + key);
                if (list.contains(key) && !file.exists()) {
                    System.out.println("======下载" + key);
                    // 使用git clone下载代码
                    GitUtil.getClone(value, basePathTemp);
                    return;
                }
                // spsql
                file = new File(basePathTemp + "spsql" + File.separator + key);
                if (key.startsWith("spsql") && !file.exists()) {
                    System.out.println("======下载" + key);
                    // 使用git clone下载代码
                    GitUtil.getClone(value, basePathTemp + "spsql" + File.separator);
                    return;
                }
                // sql
                file = new File(basePathTemp + "sql" + File.separator + key);
                if (key.startsWith("sql") && !key.equals("sql-bank") && !file.exists()) {
                    System.out.println("======下载" + key);
                    // 使用git clone下载代码
                    GitUtil.getClone(value, basePathTemp + "sql" + File.separator);
                    return;
                }
                // lcpt-web
                file = new File(basePathTemp + "lcpt-server"
                        + File.separator +"sale"+File.separator+"lcpt-web"+File.separator+ key);
                if (key.startsWith("lcpt-web") && !file.exists()) {
                    System.out.println("======下载" + key);
                    // 使用git clone下载代码
                    GitUtil.getClone(value, basePathTemp + "lcpt-server"
                            + File.separator + "sale" + File.separator + "lcpt-web" + File.separator);
                    return;
                }
                // lcpt-sale
                file = new File(basePathTemp + "lcpt-server"
                        + File.separator +"sale"+File.separator+ key);
                list = Arrays.asList("lcpt-pub", "lcpt-dxtrust", "lcpt-dxfund","lcpt-dxasset","lcpt-fina");
                if (list.contains(key) && !file.exists()) {
                    System.out.println("======下载" + key);
                    // 使用git clone下载代码
                    GitUtil.getClone(value, basePathTemp + "lcpt-server"
                            + File.separator +"sale"+ File.separator);
                    return;
                }
                // lcpt-pub
                list = Arrays.asList("lcpt-base", "lcpt-datax", "lcpt-dependencies","lcpt-jres",
                        "lcpt-register","lcpt-schedule");
                file = new File(basePathTemp + "lcpt-server"
                        + File.separator +"pub"+File.separator+ key);
                if (list.contains(key) && !file.exists()) {
                    System.out.println("======下载" + key);
                    // 使用git clone下载代码
                    GitUtil.getClone(value, basePathTemp + "lcpt-server"
                            + File.separator + "pub" + File.separator);
                }


            }, executor));

        });
        // 等待所有完成再退出
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

    }

    /**
     * 按 projectGit.xlsx 批量 clone 银行个性化工程仓库到指定目录。
     * <p>Excel 第1列为项目编号（如 86567.MTSH_FUNDINSURE），第2列为 Git 地址；
     * 目标目录为 {@code basePath} 下以仓库名命名的子目录（如 lcpt-mtsh_fundinsure）。</p>
     *
     * @param basePath 下载根目录，例如 F:\提交\ProjectSources
     */
    public void getProjectGitSourceCode(String basePath) {
        if (Strings.isBlank(basePath)) {
            return;
        }
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        Map<String, String> map = ExcelUtil.readExcel("projectGit.xlsx");
        ArrayBlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<>(50);
        int corePoolSize = 10, maxPoolSize = 10, keepAliveTime = 30;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                blockingQueue);
        String basePathTemp = basePath;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        map.forEach((key, value) -> {
            if (Strings.isBlank(key) || Strings.isBlank(value)) {
                return;
            }
            futures.add(CompletableFuture.runAsync(() -> {
                String repoName = extractRepositoryName(value);
                if (Strings.isBlank(repoName)) {
                    repoName = key;
                }
                File file = new File(basePathTemp + repoName);
                if (!file.exists()) {
                    System.out.println("======下载项目 " + key + " -> " + repoName);
                    GitUtil.getClone(value, basePathTemp);
                }
            }, executor));
        });
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
    }

    private static void initGitSourcePath(String basePath) {
        FileUtil.mkdir(Paths.get(basePath + "lcpt-server" + File.separator + "pub"));
        FileUtil.mkdir(Paths.get(basePath + "lcpt-server" + File.separator + "sale"));
        FileUtil.mkdir(Paths.get(basePath + "spsql"));
        FileUtil.mkdir(Paths.get(basePath + "sql"));
        FileUtil.mkdir(Paths.get(basePath + "lcpt-server"
                + File.separator + "sale" + File.separator + "lcpt-web"));

    }

    public void getGitHuiSourceCode(String basePath) {
        if (Strings.isBlank(basePath)) {
            return;
        }
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        File frontDir = new File(basePath + "lcpt-front");
        File huiDir = new File(frontDir, "HUI1.0");
        File bizDir = new File(huiDir, "console" + File.separator + "src" + File.separator + "biz");
        Map<String, String> map = ExcelUtil.readExcel("git.xlsx");
        map.forEach((key, value) -> {
            if ("HUI1.0".equals(key) && !GitUtil.isValidGitRepository(huiDir.getAbsolutePath())) {
                System.out.println("======下载" + key);
                GitUtil.getClone(value, frontDir.getAbsolutePath());
            }
        });
        if (!GitUtil.isValidGitRepository(huiDir.getAbsolutePath())) {
            System.out.println("HUI1.0 未下载成功，跳过前端子模块");
            return;
        }
        List<String> list = Arrays.asList("console-dxfund-vue", "console-dxtrust-vue",
                "console-dxasset-vue", "console-fina-vue");
        map.forEach((key, value) -> {
            File file = new File(bizDir, key);
            if (list.contains(key) && !GitUtil.isValidGitRepository(file.getAbsolutePath())) {
                System.out.println("======下载" + key);
                GitUtil.getSubModuleClone(value, bizDir.getAbsolutePath(), key);
            }
        });
    }

    public void getGitCounterSourceCode(String basePath) {

        if (Strings.isBlank(basePath)) {
            return;
        }
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        String basePathTemp = basePath;
        // 获取下载地址
        Map<String, String> map = ExcelUtil.readExcel("git.xlsx");

        ArrayBlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<>(50);
        int corePoolSize = 10, maxPoolSize = 10, keepAliveTime = 30;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                blockingQueue);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        map.forEach((key,value)->{
            if (!IFMCOUNTER_LIST.contains(key)) {
                return;
            }
            futures.add(CompletableFuture.runAsync(() -> {
                File file = new File(basePathTemp + "ifmcounter" + File.separator + key);
                if (!file.exists()) {
                    System.out.println("======下载" + key);
                    // 使用git clone下载代码
                    GitUtil.getClone(value, basePathTemp + "ifmcounter");
                }
            }, executor));

        });
        // 等待所有完成再退出
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();



    }

    /**
     * 递归查找所有Git仓库
     * @param dir 要查找的目录
     * @param repoPaths 用于存储找到的仓库路径
     * @param maxDepth 最大递归深度（防止无限递归）
     * @param currentDepth 当前递归深度
     */
    private void findGitRepositories(File dir, List<String> repoPaths, int maxDepth, int currentDepth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || currentDepth > maxDepth) {
            return;
        }

        // 检查当前目录是否是有效的git仓库（含 submodule 的 .git 文件）
        File gitDir = new File(dir, ".git");
        if (gitDir.exists()) {
            if (GitUtil.isValidGitRepository(dir.getAbsolutePath())) {
                String abs = dir.getAbsolutePath();
                if (!repoPaths.contains(abs)) {
                    repoPaths.add(abs);
                    System.out.println("找到Git仓库：" + abs);
                }
                // HUI 等父仓：继续收集已初始化的 submodule（如 console/src/biz/console-dxfund-vue）
                collectInitializedSubmodules(dir, repoPaths);
                // 不再随意递归普通子目录，避免把父仓工作区目录当成独立仓
                return;
            }
            System.out.println("跳过空Git仓库（无远程分支）：" + dir.getAbsolutePath());
        }

        // 递归查找子目录
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                // 跳过常见的非仓库目录
                String dirName = subDir.getName();
                if (!dirName.equals("node_modules") && 
                    !dirName.equals("target") && 
                    !dirName.equals("build") &&
                    !dirName.equals(".idea") &&
                    !dirName.startsWith(".")) {
                    findGitRepositories(subDir, repoPaths, maxDepth, currentDepth + 1);
                }
            }
        }
    }

    /**
     * 收集父仓 .gitmodules 中已初始化的 submodule，并递归其嵌套 submodule。
     */
    private void collectInitializedSubmodules(File parentRepo, List<String> repoPaths) {
        List<String> subs = GitUtil.listInitializedSubmodulePaths(parentRepo.getAbsolutePath());
        for (String subPath : subs) {
            if (repoPaths.contains(subPath)) {
                continue;
            }
            repoPaths.add(subPath);
            System.out.println("找到Git submodule：" + subPath);
            collectInitializedSubmodules(new File(subPath), repoPaths);
        }
    }

    /**
     * 批量更新所有Git仓库代码
     * @param basePath 基础路径，例如：/Users/zhoufz/hundsun/lcpt60/git/Sources/
     */
    public void updateAllGitRepositories(String basePath) {
        updateVcsRepositories(basePath, "Git", true, false);
    }

    /**
     * 批量更新所有 SVN 工作副本（递归扫描含 .svn 的目录并执行 svn update）
     *
     * @param basePath 基础路径，例如：/Users/zhoufz/hundsun/lcpt60/svn/
     */
    public void updateAllSvnRepositories(String basePath) {
        updateVcsRepositories(basePath, "SVN", false, true);
    }

    /**
     * 在同一基础路径下先更新 Git，再更新 SVN。
     */
    public void updateAllGitAndSvnRepositories(String basePath) {
        updateVcsRepositories(basePath, "Git+SVN", true, true);
    }

    private void updateVcsRepositories(String basePath, String title, boolean updateGit, boolean updateSvn) {
        if (Strings.isBlank(basePath)) {
            System.out.println("基础路径不能为空！");
            return;
        }
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }

        File baseDir = new File(basePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            System.out.println("基础路径不存在或不是目录：" + basePath);
            return;
        }

        System.out.println("========== 开始批量更新所有" + title + "仓库 ==========");
        System.out.println("基础路径：" + basePath);
        System.out.println("扫描中...");

        List<String> gitPaths = new ArrayList<>();
        List<String> svnPaths = new ArrayList<>();
        if (updateGit) {
            findGitRepositories(baseDir, gitPaths, 5, 0);
        }
        if (updateSvn) {
            findSvnRepositories(baseDir, svnPaths, 5, 0);
        }

        if (gitPaths.isEmpty() && svnPaths.isEmpty()) {
            System.out.println("未找到任何" + title + "仓库！");
            return;
        }

        System.out.println("共找到 Git=" + gitPaths.size() + "，SVN=" + svnPaths.size());
        System.out.println("开始更新...");

        ArrayBlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<>(50);
        int corePoolSize = 5, maxPoolSize = 10, keepAliveTime = 30;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                blockingQueue);

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (String repoPath : gitPaths) {
            futures.add(CompletableFuture.supplyAsync(() -> GitUtil.gitPull(repoPath), executor));
        }
        for (String repoPath : svnPaths) {
            futures.add(CompletableFuture.supplyAsync(() -> SvnUtil.svnUpdate(repoPath), executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long successCount = futures.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return false;
            }
        }).count();

        executor.shutdown();

        int total = gitPaths.size() + svnPaths.size();
        System.out.println("========== 批量更新完成 ==========");
        System.out.println("总计仓库数：" + total + "（Git=" + gitPaths.size() + "，SVN=" + svnPaths.size() + "）");
        System.out.println("成功更新：" + successCount);
        System.out.println("失败/跳过：" + (total - successCount));
    }

    /**
     * 查询基础路径下各仓库当前分支。
     *
     * @param vcsType git | svn | all
     * @return 每项含 type、name、path、branch
     */
    public List<Map<String, String>> listCurrentBranches(String basePath, String vcsType) {
        if (Strings.isBlank(basePath)) {
            throw new IllegalArgumentException("基础路径不能为空");
        }
        String path = basePath;
        if (!path.endsWith(File.separator)) {
            path += File.separator;
        }
        File baseDir = new File(path);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            throw new IllegalArgumentException("基础路径不存在或不是目录：" + path);
        }

        String type = vcsType == null ? "git" : vcsType.trim().toLowerCase();
        boolean includeGit = !"svn".equals(type);
        boolean includeSvn = "svn".equals(type) || "all".equals(type)
                || "git+svn".equals(type) || "both".equals(type);

        List<String> gitPaths = new ArrayList<>();
        List<String> svnPaths = new ArrayList<>();
        if (includeGit) {
            findGitRepositories(baseDir, gitPaths, 5, 0);
        }
        if (includeSvn) {
            findSvnRepositories(baseDir, svnPaths, 5, 0);
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (String repoPath : gitPaths) {
            Map<String, String> row = new LinkedHashMap<>();
            boolean submodule = GitUtil.isSubmoduleWorkingTree(repoPath);
            row.put("type", "git");
            row.put("kind", submodule ? "submodule" : "repo");
            row.put("name", new File(repoPath).getName());
            row.put("path", repoPath);
            row.put("branch", GitUtil.currentBranch(repoPath));
            rows.add(row);
        }
        for (String repoPath : svnPaths) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("type", "svn");
            row.put("kind", "repo");
            row.put("name", new File(repoPath).getName());
            row.put("path", repoPath);
            row.put("branch", SvnUtil.currentBranch(repoPath));
            rows.add(row);
        }
        rows.sort(Comparator
                .comparing((Map<String, String> m) -> m.get("type"))
                .thenComparing(m -> "submodule".equals(m.get("kind")) ? 1 : 0)
                .thenComparing(m -> m.get("name"), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /**
     * 切换单个仓库到指定分支/目标。
     *
     * @param repoPath 仓库根目录绝对路径
     * @param ref      Git 分支/标签，或 SVN URL / {@code ^/branches/...}
     * @param vcsType  git | svn；空则根据目录自动识别
     * @return 是否成功
     */
    public boolean checkoutOneRepository(String repoPath, String ref, String vcsType) {
        if (Strings.isBlank(repoPath)) {
            throw new IllegalArgumentException("仓库路径不能为空");
        }
        if (Strings.isBlank(ref)) {
            throw new IllegalArgumentException("分支/目标不能为空");
        }
        File dir = new File(repoPath.trim());
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("仓库路径不存在或不是目录：" + repoPath);
        }
        String type = vcsType == null ? "" : vcsType.trim().toLowerCase();
        if (Strings.isBlank(type) || "auto".equals(type)) {
            if (GitUtil.isValidGitRepository(dir.getAbsolutePath())) {
                type = "git";
            } else if (SvnUtil.isValidSvnWorkingCopy(dir.getAbsolutePath())) {
                type = "svn";
            } else {
                throw new IllegalArgumentException("无法识别为 Git 或 SVN 仓库：" + repoPath);
            }
        }
        String target = ref.trim();
        if ("svn".equals(type)) {
            return SvnUtil.svnSwitch(dir.getAbsolutePath(), target);
        }
        return GitUtil.gitCheckout(dir.getAbsolutePath(), target);
    }

    /**
     * 将基础路径下的 Git 仓库切换到同一分支/标签。
     */
    public void checkoutAllGitRepositories(String basePath, String ref) {
        checkoutVcsRepositories(basePath, ref, "Git", true, false);
    }

    /**
     * 将基础路径下的 SVN 工作副本 switch 到同一目标（URL 或 {@code ^/branches/...}）。
     */
    public void switchAllSvnRepositories(String basePath, String target) {
        checkoutVcsRepositories(basePath, target, "SVN", false, true);
    }

    /**
     * Git checkout 与 SVN switch 使用同一目标字符串。
     */
    public void checkoutAllGitAndSvnRepositories(String basePath, String ref) {
        checkoutVcsRepositories(basePath, ref, "Git+SVN", true, true);
    }

    private void checkoutVcsRepositories(String basePath, String ref, String title,
                                         boolean checkoutGit, boolean switchSvn) {
        if (Strings.isBlank(basePath)) {
            System.out.println("基础路径不能为空！");
            return;
        }
        if (Strings.isBlank(ref)) {
            System.out.println("分支/目标不能为空！");
            return;
        }
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        File baseDir = new File(basePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            System.out.println("基础路径不存在或不是目录：" + basePath);
            return;
        }

        System.out.println("========== 开始批量切换 " + title + " → " + ref + " ==========");
        List<String> gitPaths = new ArrayList<>();
        List<String> svnPaths = new ArrayList<>();
        if (checkoutGit) {
            findGitRepositories(baseDir, gitPaths, 5, 0);
        }
        if (switchSvn) {
            findSvnRepositories(baseDir, svnPaths, 5, 0);
        }
        if (gitPaths.isEmpty() && svnPaths.isEmpty()) {
            System.out.println("未找到任何" + title + "仓库！");
            return;
        }

        ArrayBlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<>(50);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 10, 30, TimeUnit.SECONDS, blockingQueue);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        String target = ref.trim();
        for (String repoPath : gitPaths) {
            futures.add(CompletableFuture.supplyAsync(() -> GitUtil.gitCheckout(repoPath, target), executor));
        }
        for (String repoPath : svnPaths) {
            futures.add(CompletableFuture.supplyAsync(() -> SvnUtil.svnSwitch(repoPath, target), executor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long successCount = futures.stream().filter(f -> {
            try {
                return Boolean.TRUE.equals(f.get());
            } catch (Exception e) {
                return false;
            }
        }).count();
        executor.shutdown();
        int total = gitPaths.size() + svnPaths.size();
        System.out.println("========== 批量切换完成 ==========");
        System.out.println("总计：" + total + "（Git=" + gitPaths.size() + "，SVN=" + svnPaths.size() + "）");
        System.out.println("成功：" + successCount);
        System.out.println("失败/跳过：" + (total - successCount));
    }

    /**
     * 递归查找所有 SVN 工作副本根（含 .svn）
     */
    private void findSvnRepositories(File dir, List<String> repoPaths, int maxDepth, int currentDepth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || currentDepth > maxDepth) {
            return;
        }

        if (SvnUtil.isValidSvnWorkingCopy(dir.getAbsolutePath())) {
            repoPaths.add(dir.getAbsolutePath());
            System.out.println("找到SVN工作副本：" + dir.getAbsolutePath());
            return;
        }

        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                String dirName = subDir.getName();
                if (!dirName.equals("node_modules")
                        && !dirName.equals("target")
                        && !dirName.equals("build")
                        && !dirName.equals(".idea")
                        && !dirName.startsWith(".")) {
                    findSvnRepositories(subDir, repoPaths, maxDepth, currentDepth + 1);
                }
            }
        }
    }

    /**
     * 扫描基础目录下所有有效Git仓库，并将“仓库名=物理路径”写入输出文件
     * @param basePath 基础路径，例如：/Users/zhoufz/hundsun/lcpt60/git/Sources/
     * @param outputPath 输出文件路径，例如：/Users/zhoufz/hundsun/tools/src/main/resources/gitrep.txt
     */
    public void generateGitRepositoryMapping(String basePath, String outputPath) {
        if (Strings.isBlank(basePath) || Strings.isBlank(outputPath)) {
            throw new IllegalArgumentException("basePath和outputPath不能为空");
        }
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        File baseDir = new File(basePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            throw new IllegalArgumentException("基础路径不存在或不是目录：" + basePath);
        }

        List<String> repoPaths = new ArrayList<>();
        findGitRepositories(baseDir, repoPaths, 6, 0);

        Map<String, String> repositoryMapping = new LinkedHashMap<>();
        for (String repoPath : repoPaths) {
            addRepositoryMapping(repositoryMapping, repoPath);
            collectSubmoduleMappings(repoPath, repositoryMapping);
        }

        List<String> lines = new ArrayList<>();
        repositoryMapping.forEach((key, value) -> lines.add(key + "=" + value));
        try {
            Path output = Paths.get(outputPath);
            Files.createDirectories(output.getParent());
            Set<String> mergedLines = new LinkedHashSet<>();
            if (Files.exists(output)) {
                List<String> existingLines = Files.readAllLines(output, StandardCharsets.UTF_8);
                for (String existingLine : existingLines) {
                    if (!Strings.isBlank(existingLine)) {
                        mergedLines.add(existingLine.trim());
                    }
                }
            }
            mergedLines.addAll(lines);
            Files.write(output, new ArrayList<>(mergedLines), StandardCharsets.UTF_8);
            System.out.println("写入完成，新增仓库数量：" + lines.size());
            System.out.println("合并后总记录数：" + mergedLines.size());
            System.out.println("输出文件：" + outputPath);
        } catch (Exception e) {
            throw new RuntimeException("写入gitrep.txt失败", e);
        }
    }

    /**
     * 根据 gitrep 映射文件对指定键名的仓库执行 {@code git pull}（含 submodule 时与 {@link GitUtil#gitPull} 一致）。
     *
     * @param gitrepPath      映射文件路径，每行 {@code 仓库键=仓库根目录绝对路径}；空行与 {@code #} 开头行为注释
     * @param repositoryKey   与文件中键名一致，例如 lcpt-dxfund
     * @return 是否拉取成功
     * @throws IllegalArgumentException 路径或参数不合法，或映射中不存在该键
     */
    public boolean updateGitRepositoryFromGitrep(String gitrepPath, String repositoryKey) {
        if (Strings.isBlank(gitrepPath)) {
            throw new IllegalArgumentException("gitrepPath不能为空");
        }
        if (Strings.isBlank(repositoryKey)) {
            throw new IllegalArgumentException("repositoryKey不能为空");
        }
        Map<String, String> mapping = readGitRepositoryMappingFile(gitrepPath);
        String repoPath = mapping.get(repositoryKey);
        if (Strings.isBlank(repoPath)) {
            throw new IllegalArgumentException("gitrep中未找到仓库键：" + repositoryKey);
        }
        return GitUtil.gitPull(repoPath);
    }

    /**
     * 根据 gitrep 映射文件将指定键名的仓库切换到指定分支/标签/提交。
     *
     * @param gitrepPath      映射文件路径，格式同 {@link #updateGitRepositoryFromGitrep}
     * @param repositoryKey   仓库键名
     * @param versionRef      分支名、标签名或提交号，例如 IFMS6.0V202607.00.005
     * @return 是否切换成功
     * @throws IllegalArgumentException 路径或参数不合法，或映射中不存在该键
     */
    public boolean checkoutGitRepositoryFromGitrep(String gitrepPath, String repositoryKey, String versionRef) {
        if (Strings.isBlank(gitrepPath)) {
            throw new IllegalArgumentException("gitrepPath不能为空");
        }
        if (Strings.isBlank(repositoryKey)) {
            throw new IllegalArgumentException("repositoryKey不能为空");
        }
        if (Strings.isBlank(versionRef)) {
            throw new IllegalArgumentException("versionRef不能为空");
        }
        Map<String, String> mapping = readGitRepositoryMappingFile(gitrepPath);
        String repoPath = mapping.get(repositoryKey);
        if (Strings.isBlank(repoPath)) {
            throw new IllegalArgumentException("gitrep中未找到仓库键：" + repositoryKey);
        }
        return GitUtil.gitCheckout(repoPath, versionRef);
    }

    private Map<String, String> readGitRepositoryMappingFile(String gitrepPath) {
        Path path = Paths.get(gitrepPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("gitrep映射文件不存在：" + gitrepPath);
        }
        try {
            Map<String, String> map = new LinkedHashMap<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (Strings.isBlank(trimmed) || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException("gitrep映射非法行（缺少=）：" + trimmed);
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (Strings.isBlank(key) || Strings.isBlank(value)) {
                    continue;
                }
                map.put(key, value);
            }
            if (map.isEmpty()) {
                throw new IllegalArgumentException("gitrep映射文件无有效内容：" + gitrepPath);
            }
            return map;
        } catch (IOException e) {
            throw new RuntimeException("读取gitrep映射失败", e);
        }
    }

    private String getOriginRemote(String repoPath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    GitUtil.resolveGitExecutable(), "config", "--get", "remote.origin.url");
            processBuilder.directory(new File(repoPath));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            String result;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                result = reader.readLine();
            }
            int exitCode = process.waitFor();
            return exitCode == 0 ? result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractRepositoryName(String remoteUrl) {
        String normalized = remoteUrl.trim();
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf(':'));
        if (slash < 0 || slash == normalized.length() - 1) {
            return null;
        }
        String name = normalized.substring(slash + 1);
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    private void addRepositoryMapping(Map<String, String> repositoryMapping, String repositoryPath) {
        String remoteUrl = getOriginRemote(repositoryPath);
        if (Strings.isBlank(remoteUrl)) {
            return;
        }
        String repoName = extractRepositoryName(remoteUrl);
        if (Strings.isBlank(repoName) || repositoryMapping.containsKey(repoName)) {
            return;
        }
        repositoryMapping.put(repoName, repositoryPath);
    }

    /**
     * 从 .gitmodules 递归收集 submodule 映射（含未 init 的 submodule，路径取自 path 配置）。
     */
    private void collectSubmoduleMappings(String parentRepoPath, Map<String, String> repositoryMapping) {
        File gitmodulesFile = new File(parentRepoPath, ".gitmodules");
        if (!gitmodulesFile.isFile()) {
            return;
        }
        String currentPath = null;
        String currentUrl = null;
        try {
            for (String line : Files.readAllLines(gitmodulesFile.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[submodule")) {
                    flushSubmoduleMapping(parentRepoPath, repositoryMapping, currentPath, currentUrl);
                    currentPath = null;
                    currentUrl = null;
                } else if (trimmed.startsWith("path = ")) {
                    currentPath = trimmed.substring("path = ".length()).trim();
                } else if (trimmed.startsWith("url = ")) {
                    currentUrl = trimmed.substring("url = ".length()).trim();
                }
            }
            flushSubmoduleMapping(parentRepoPath, repositoryMapping, currentPath, currentUrl);
        } catch (IOException ignored) {
        }
    }

    private void flushSubmoduleMapping(String parentRepoPath, Map<String, String> repositoryMapping,
                                       String relativePath, String urlFromGitmodules) {
        if (Strings.isBlank(relativePath)) {
            return;
        }
        String absPath = new File(parentRepoPath, relativePath).getAbsolutePath();
        int sizeBefore = repositoryMapping.size();
        addRepositoryMapping(repositoryMapping, absPath);
        if (repositoryMapping.size() == sizeBefore && !Strings.isBlank(urlFromGitmodules)) {
            String repoName = extractRepositoryName(urlFromGitmodules);
            if (!Strings.isBlank(repoName) && !repositoryMapping.containsKey(repoName)) {
                repositoryMapping.put(repoName, absPath);
            }
        }
        collectSubmoduleMappings(absPath, repositoryMapping);
    }


}
