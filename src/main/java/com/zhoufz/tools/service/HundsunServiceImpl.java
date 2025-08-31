package com.zhoufz.tools.service;

import com.zhoufz.tools.util.ExcelUtil;
import com.zhoufz.tools.util.FileUtil;
import com.zhoufz.tools.util.GitUtil;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    public void replaceJavaFile() {
        List<String> list = FileUtil.readFile();
        String sourcePrefix = list.get(0);
        String targetPrefix = list.get(1);
        String scriptPrefix = list.get(2);
        String vuePrefix = list.get(3);
        String taskId = list.get(4);
        taskId = taskId.substring(taskId.indexOf(":") + 1);
        File target;
        File source;
        try {
            for (int i = 5; i < list.size(); i++) {
                String readLine = list.get(i);
                if (readLine.contains("lcpt-dxfund") || readLine.contains("lcpt-dxasset") ||
                        readLine.contains("lcpt-dxtrust") || readLine.contains("lcpt-insure") ||
                        readLine.contains("lcpt-pub")) {
                    target = new File(targetPrefix + readLine.substring(readLine.indexOf("sale") + 4));
                    if (sourcePrefix.contains("小包")) {
                        source = new File(sourcePrefix + readLine.substring(readLine.indexOf("sale") + 4));
                    } else {
                        source = new File(sourcePrefix + readLine);
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
                    if (readLine.contains("pub")) {
                        target = new File(scriptPrefix + readLine.substring(readLine.indexOf("pub")-1).replace(sourceVersion,targetVersion));

                        if (sourcePrefix.contains("小包")) {
                            source = new File(sourcePrefix+ readLine.substring(readLine.indexOf("pub")-1));
                        }else {
                            source = new File(sourcePrefix+ readLine);
                        }

                        FileUtil.replaceSql(target, source, taskId);
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
                        source = new File(sourcePrefix+ readLine.substring(readLine.indexOf("sale")+4));
                    }else {
                        source = new File(sourcePrefix+ readLine);
                    }
                    String targetFileName = readLine.replace(readLine.substring(readLine.indexOf("ifmcounter") + 11, readLine.indexOf("WebContent") - 1), "ifmcounter");
                    target = new File(targetPrefix+targetFileName.substring(targetFileName.indexOf("ifmcounter") + 10));
                    FileUtil.replace(target, source);
                }


            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    public void replaceSqlFile() {

    }

    public void replaceConfigFile() {

    }


    public void saveConfigFile() {

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



}
