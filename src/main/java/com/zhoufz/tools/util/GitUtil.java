package com.zhoufz.tools.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */
public class GitUtil {

    public static void getClone(String gitRep, String targetDir)  {
        try {
            // 执行 git clone 命令
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(new File(targetDir));
            pb.command("git", "clone", gitRep);
            Process process = pb.start();
            // 打印执行过程中的输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Git clone " + gitRep + " 成功！");
            } else {
                System.out.println("Git clone " + gitRep + " 失败，退出码：" + exitCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void getSubModuleClone(String gitRep, String targetDir, String moduleName) {

        try {
            // 执行 git clone 命令
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(new File(targetDir));
            pb.command("git","submodule", "add", gitRep, moduleName);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // 打印执行过程中的输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Git clone " + gitRep + " 成功！");
            } else {
                System.out.println("Git clone " + gitRep + " 失败，退出码：" + exitCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
