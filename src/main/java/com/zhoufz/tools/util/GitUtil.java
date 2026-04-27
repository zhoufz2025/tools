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

    /**
     * 检查目录是否包含 git submodule
     * @param repoDir 仓库目录路径
     * @return 是否包含 submodule
     */
    public static boolean hasSubmodules(String repoDir) {
        File gitmodulesFile = new File(repoDir, ".gitmodules");
        return gitmodulesFile.exists();
    }

    /**
     * 检查是否是有效的 git 仓库（有提交历史或远程分支）
     * @param repoDir 仓库目录路径
     * @return 是否是有效的 git 仓库
     */
    public static boolean isValidGitRepository(String repoDir) {
        try {
            File dir = new File(repoDir);
            if (!dir.exists() || !dir.isDirectory()) {
                return false;
            }

            // 检查是否有 .git 目录
            File gitDir = new File(dir, ".git");
            if (!gitDir.exists()) {
                return false;
            }

            // 检查是否有远程分支
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(dir);
            pb.command("git", "remote", "-v");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            
            // 如果有远程仓库配置，则认为是有效仓库
            return exitCode == 0 && output.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 执行 git pull 更新指定目录的仓库
     * @param repoDir 仓库目录路径
     * @return 是否更新成功
     */
    public static boolean gitPull(String repoDir) {
        try {
            File dir = new File(repoDir);
            if (!dir.exists() || !dir.isDirectory()) {
                System.out.println("目录不存在：" + repoDir);
                return false;
            }

            // 检查是否是有效的git仓库
            if (!isValidGitRepository(repoDir)) {
                System.out.println("不是有效的git仓库（无远程分支）：" + repoDir);
                return false;
            }

            boolean hasSubmodules = hasSubmodules(repoDir);
            if (hasSubmodules) {
                System.out.println("======开始更新（包含submodules）：" + repoDir);
            } else {
                System.out.println("======开始更新：" + repoDir);
            }
            
            // 执行 git pull 命令
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(dir);
            if (hasSubmodules) {
                // 对于包含 submodule 的仓库，使用 --recurse-submodules 参数
                pb.command("git", "pull", "--recurse-submodules");
            } else {
                pb.command("git", "pull");
            }
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
            
            // 如果有 submodule，还需要更新 submodule
            if (exitCode == 0 && hasSubmodules) {
                System.out.println("------检查并更新 submodules：" + repoDir);
                
                // 使用 foreach 遍历所有子模块并更新
                ProcessBuilder pb2 = new ProcessBuilder();
                pb2.directory(dir);
                pb2.command("git", "submodule", "foreach", "git", "pull", "origin", "HEAD");
                pb2.redirectErrorStream(true);
                Process process2 = pb2.start();
                
                int updatedCount = 0;
                int unchangedCount = 0;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process2.getInputStream()))) {
                    String line;
                    String currentSubmodule = "";
                    while ((line = reader.readLine()) != null) {
                        // 识别进入哪个子模块
                        if (line.startsWith("Entering '") || line.startsWith("正在进入 '")) {
                            currentSubmodule = line;
                        }
                        // 只显示有实际更新的子模块
                        else if (line.contains("Updating") || line.contains("Fast-forward") || 
                                 line.contains("更新") || line.contains("快进")) {
                            if (!currentSubmodule.isEmpty()) {
                                System.out.println(currentSubmodule);
                                currentSubmodule = "";
                            }
                            System.out.println("  " + line);
                            updatedCount++;
                        }
                        else if (line.contains("Already up to date") || line.contains("已经是最新")) {
                            unchangedCount++;
                        }
                    }
                }
                
                int exitCode2 = process2.waitFor();
                if (exitCode2 == 0) {
                    if (updatedCount > 0) {
                        System.out.println("更新了 " + updatedCount + " 个 submodule(s)");
                    }
                    if (unchangedCount > 0) {
                        System.out.println(unchangedCount + " 个 submodule(s) 已是最新");
                    }
                    System.out.println("Git pull (含submodules) " + repoDir + " 成功！");
                    return true;
                } else {
                    System.out.println("Submodule 更新失败，退出码：" + exitCode2);
                    return false;
                }
            } else if (exitCode == 0) {
                System.out.println("Git pull " + repoDir + " 成功！");
                return true;
            } else {
                System.out.println("Git pull " + repoDir + " 失败，退出码：" + exitCode);
                return false;
            }
        } catch (Exception e) {
            System.out.println("Git pull " + repoDir + " 发生异常：");
            e.printStackTrace();
            return false;
        }
    }
}
