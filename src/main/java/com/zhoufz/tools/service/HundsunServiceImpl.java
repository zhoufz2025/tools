package com.zhoufz.tools.service;

import com.zhoufz.tools.util.ExcelUtil;
import com.zhoufz.tools.util.FileUtil;
import com.zhoufz.tools.util.GitUtil;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */
@Service
public class HundsunServiceImpl {

    public static List<String> IFMCOUNTER_LIST = Arrays.asList(
            "ifmcounter","ifmcounter-dxasset","ifmcounter-dxfund","ifmcounter-dxtrust");

    public static List<String> HUI_LIST = Arrays.asList(
            "HUI1.0", "console-dxasset-vue", "console-dxfund-vue","console-dxtrust-vue");

    public void gitReplaceSqlFile(String fileName) {
        List<String> list = FileUtil.readFile(fileName);
        String sourcePrefix = list.get(0);
        String targetPrefix = list.get(1);
        String taskId = list.get(2);
        String version = list.get(3);
        taskId = taskId.substring(taskId.indexOf(":") + 1);
        version = version.substring(version.indexOf(":") + 1);
        File target;
        File source;
        try {
            for (int i = 4; i < list.size(); i++) {
                String readLine = list.get(i);
                if (readLine.contains("spsql")) {
                    String sourceVersion = readLine.substring(readLine.lastIndexOf("\\") + 1, readLine.lastIndexOf("\\") + 1 + "IFMS6.0V202506.00.000".length());
                    target = new File(targetPrefix + readLine.replace(sourceVersion, version));
                    source = new File(sourcePrefix + readLine);
                    System.out.println(readLine.replace(sourceVersion, version));
                    FileUtil.replaceSql(target, source, taskId);
                }else{
                    target = new File(targetPrefix + readLine);
                    source = new File(sourcePrefix + readLine);
                    System.out.println(targetPrefix + readLine);
                    System.out.println(sourcePrefix + readLine);
                    FileUtil.replace(target, source);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    
    }
    
    public void svnReplaceSqlFile(String fileName) {
        List<String> list = FileUtil.readFile(fileName);
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
            List<String> list1 = Arrays.asList("HUI1.0", "console-dxasset-vue", "console-dxfund-vue","console-dxtrust-vue",
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
                list = Arrays.asList("lcpt-pub", "lcpt-dxtrust", "lcpt-dxfund","lcpt-dxasset");
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

    private static void initGitSourcePath(String basePath) {
        // FileUtil.mkdir(Paths.get(basePath + "lcpt-server" + File.separator + "pub"));
        // FileUtil.mkdir(Paths.get(basePath + "lcpt-server" + File.separator + "sale"));
        // FileUtil.mkdir(Paths.get(basePath + "spsql"));
        // FileUtil.mkdir(Paths.get(basePath + "sql"));
        // FileUtil.mkdir(Paths.get(basePath + "lcpt-server"
        //         + File.separator + "sale" + File.separator + "lcpt-web"));

    }

    public void getGitHuiSourceCode(String basePath) {
        if (Strings.isBlank(basePath)) {
            return;
        }
        if (!basePath.endsWith(File.separator)) {
            basePath += File.separator;
        }
        String basePathTemp = basePath;
        // 获取下载地址
        Map<String, String> map = ExcelUtil.readExcel("git.xlsx");
        map.forEach((key,value)->{
            File file = new File(basePathTemp + "lcpt-front");
            if ("HUI1.0".equals(key) && !file.exists()) {
                System.out.println("======下载" + key);
                // 使用git clone下载代码
                GitUtil.getClone(value, basePathTemp + "lcpt-front");
            }
        });

        map.forEach((key,value)->{
            List<String> list = Arrays.asList("console-dxfund-vue", "console-dxtrust-vue", "console-dxasset-vue");
            File file = new File(basePathTemp + "lcpt-front" + File.separator + "HUI1.0" + File.separator
                    + "console" + File.separator + "src" + File.separator + "biz" + File.separator + key);
            if (list.contains(key) && !file.exists()) {
                System.out.println("======下载" + key);
                GitUtil.getSubModuleClone(value,
                        basePathTemp + "lcpt-front" + File.separator + "HUI1.0" + File.separator
                                + "console" + File.separator + "src" + File.separator + "biz",
                        key);
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

        // 检查当前目录是否是有效的git仓库
        File gitDir = new File(dir, ".git");
        if (gitDir.exists()) {
            // 检查是否是有效的git仓库（有远程分支）
            if (GitUtil.isValidGitRepository(dir.getAbsolutePath())) {
                repoPaths.add(dir.getAbsolutePath());
                System.out.println("找到Git仓库：" + dir.getAbsolutePath());
                // 如果是有效的git仓库，不再递归子目录（避免处理嵌套的git仓库）
                return;
            } else {
                // 如果是空的git仓库（无远程分支），继续扫描子目录
                System.out.println("跳过空Git仓库（无远程分支）：" + dir.getAbsolutePath());
            }
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
     * 批量更新所有Git仓库代码
     * @param basePath 基础路径，例如：/Users/zhoufz/hundsun/lcpt60/git/Sources/
     */
    public void updateAllGitRepositories(String basePath) {
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

        System.out.println("========== 开始批量更新所有Git仓库 ==========");
        System.out.println("基础路径：" + basePath);
        System.out.println("扫描中...");

        List<String> repoPaths = new ArrayList<>();
        
        // 递归查找所有Git仓库（最大深度5层，避免过深的目录结构）
        findGitRepositories(baseDir, repoPaths, 5, 0);

        if (repoPaths.isEmpty()) {
            System.out.println("未找到任何Git仓库！");
            return;
        }

        System.out.println("共找到 " + repoPaths.size() + " 个Git仓库");
        System.out.println("开始更新...");

        // 使用线程池并发更新
        ArrayBlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<>(50);
        int corePoolSize = 5, maxPoolSize = 10, keepAliveTime = 30;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                blockingQueue);

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        
        for (String repoPath : repoPaths) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                return GitUtil.gitPull(repoPath);
            }, executor));
        }

        // 等待所有更新完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // 统计结果
        long successCount = futures.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return false;
            }
        }).count();
        
        executor.shutdown();
        
        System.out.println("========== 批量更新完成 ==========");
        System.out.println("总计仓库数：" + repoPaths.size());
        System.out.println("成功更新：" + successCount);
        System.out.println("失败/跳过：" + (repoPaths.size() - successCount));
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
            Set<String> submodulePaths = getSubmodulePaths(repoPath);
            for (String submodulePath : submodulePaths) {
                addRepositoryMapping(repositoryMapping, submodulePath);
            }
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

    private String getOriginRemote(String repoPath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("git", "config", "--get", "remote.origin.url");
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

    private Set<String> getSubmodulePaths(String repoPath) {
        Set<String> submodulePaths = new LinkedHashSet<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "git", "submodule", "foreach", "--quiet", "--recursive", "pwd");
            processBuilder.directory(new File(repoPath));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String path = line.trim();
                    if (!Strings.isBlank(path)) {
                        submodulePaths.add(path);
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
        }
        return submodulePaths;
    }


}
