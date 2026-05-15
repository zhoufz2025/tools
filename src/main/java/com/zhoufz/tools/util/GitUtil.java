package com.zhoufz.tools.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process2.getInputStream(), StandardCharsets.UTF_8))) {
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

    /**
     * 在指定仓库目录执行 git fetch 后切换到分支或标签。
     * <p>尝试顺序：本地或已有引用 {@code git checkout ref}；{@code origin/ref}；
     * 再在远程分支中匹配「路径以 {@code /ref} 结尾」或全名等于 {@code ref}（例如 {@code hotfix/IFMS6.0V202607.00.005}）；
     * 最后尝试标签 {@code refs/tags/ref}。匹配多条时优先 {@code hotfix/}、{@code release/} 等常见前缀。</p>
     * <p>在命名分支上切换成功后，会执行一次 {@link #gitPull} 与远程对齐；分离 HEAD（如标签）则跳过 pull。</p>
     *
     * @param repoDir 仓库根目录
     * @param ref      分支名、标签名或版本片段，例如 IFMS6.0V202607.00.005
     * @return 是否成功
     */
    public static boolean gitCheckout(String repoDir, String ref) {
        try {
            File dir = new File(repoDir);
            if (!dir.exists() || !dir.isDirectory()) {
                System.out.println("目录不存在：" + repoDir);
                return false;
            }
            if (!isValidGitRepository(repoDir)) {
                System.out.println("不是有效的git仓库（无远程分支）：" + repoDir);
                return false;
            }

            System.out.println("====== git fetch origin（含 tags）：" + repoDir);
            int fetchCode = runGit(dir, "git", "fetch", "origin", "--prune", "--tags");
            if (fetchCode != 0) {
                System.out.println("git fetch 失败，退出码：" + fetchCode);
                return false;
            }

            int checkoutCode = tryCheckoutRef(dir, ref);
            if (checkoutCode != 0) {
                System.out.println("------ 以下为 origin 远程分支（按名称排序，便于核对命名规则）：");
                runGit(dir, "git", "branch", "-r", "--sort=refname");
                System.out.println("git checkout 失败，退出码：" + checkoutCode);
                return false;
            }

            if (hasSubmodules(repoDir)) {
                System.out.println("------ git submodule update --init --recursive：" + repoDir);
                int subCode = runGit(dir, "git", "submodule", "update", "--init", "--recursive");
                if (subCode != 0) {
                    System.out.println("submodule 更新失败，退出码：" + subCode);
                    return false;
                }
            }

            if (isOnNamedBranch(dir)) {
                System.out.println("------ 切换分支后 git pull 同步远程：" + repoDir);
                if (!gitPull(repoDir)) {
                    return false;
                }
            } else {
                System.out.println("------ 当前为分离 HEAD（多为标签检出），跳过 git pull");
            }

            System.out.println("Git checkout " + ref + " @ " + repoDir + " 成功！");
            return true;
        } catch (Exception e) {
            System.out.println("Git checkout " + repoDir + " 发生异常：");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * @return 0 表示已成功 checkout
     */
    private static int tryCheckoutRef(File dir, String ref) throws Exception {
        System.out.println("====== git checkout " + ref + "：" + dir.getPath());
        int checkoutCode = runGit(dir, "git", "checkout", ref);
        if (checkoutCode == 0) {
            return 0;
        }
        System.out.println("直接 checkout 失败，尝试跟踪远程分支 origin/" + ref);
        checkoutCode = runGit(dir, "git", "checkout", "-B", ref, "origin/" + ref);
        if (checkoutCode == 0) {
            return 0;
        }

        List<String> originRefs = listOriginRemoteShortRefs(dir);
        List<String> suffixMatches = new ArrayList<>();
        for (String full : originRefs) {
            if (!full.startsWith("origin/")) {
                continue;
            }
            String tail = full.substring("origin/".length());
            if (tail.equals(ref) || tail.endsWith("/" + ref)) {
                suffixMatches.add(full);
            }
        }
        sortRemoteBranchCandidates(suffixMatches);
        if (!suffixMatches.isEmpty()) {
            System.out.println("------ 按版本片段 \"" + ref + "\" 匹配到远程分支（常见为 hotfix/版本 或 release/版本）：");
            for (String m : suffixMatches) {
                System.out.println("  " + m);
            }
            String pick = suffixMatches.get(0);
            if (suffixMatches.size() > 1) {
                System.out.println("------ 使用优先级最高的分支：" + pick);
            }
            // 使用 -B：本地已存在同名分支（如 hotfix/xxx）时仍可对齐到远程，避免 -t 报「分支已经存在」
            String localBranch = pick.substring("origin/".length());
            System.out.println("------ git checkout -B " + localBranch + " " + pick);
            checkoutCode = runGit(dir, "git", "checkout", "-B", localBranch, pick);
            if (checkoutCode == 0) {
                return 0;
            }
        } else {
            List<String> fuzzy = new ArrayList<>();
            for (String full : originRefs) {
                if (full.contains(ref)) {
                    fuzzy.add(full);
                }
            }
            if (!fuzzy.isEmpty()) {
                System.out.println("------ 未找到路径以 /" + ref + " 结尾的远程分支；名称中包含该片段的分支如下（可改传完整远程分支名）：");
                fuzzy.sort(Comparator.naturalOrder());
                for (String f : fuzzy) {
                    System.out.println("  " + f);
                }
            }
        }

        int tagOk = runGit(dir, "git", "show-ref", "--verify", "--quiet", "refs/tags/" + ref);
        if (tagOk == 0) {
            System.out.println("------ 作为标签检出：refs/tags/" + ref);
            checkoutCode = runGit(dir, "git", "checkout", ref);
            if (checkoutCode == 0) {
                return 0;
            }
        }
        return checkoutCode;
    }

    private static List<String> listOriginRemoteShortRefs(File dir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "git", "for-each-ref", "--format=%(refname:short)", "refs/remotes/origin/");
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        List<String> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !"origin/HEAD".equals(line)) {
                    out.add(line);
                }
            }
        }
        if (process.waitFor() != 0) {
            return new ArrayList<>();
        }
        return out;
    }

    /** 优先 hotfix、release 等常见发布分支前缀，其次更短路径、字典序 */
    private static void sortRemoteBranchCandidates(List<String> originRefs) {
        originRefs.sort(Comparator
                .comparingInt(GitUtil::remoteBranchPrefixPriority)
                .thenComparingInt(String::length)
                .thenComparing(s -> s));
    }

    private static int remoteBranchPrefixPriority(String originRef) {
        String tail = originRef.startsWith("origin/") ? originRef.substring(7) : originRef;
        if (tail.startsWith("hotfix/")) {
            return 0;
        }
        if (tail.startsWith("release/")) {
            return 1;
        }
        if (tail.startsWith("bugfix/")) {
            return 2;
        }
        if (tail.startsWith("patch/")) {
            return 3;
        }
        if (tail.startsWith("feature/")) {
            return 10;
        }
        return 20;
    }

    /** {@code git rev-parse --abbrev-ref HEAD} 在分离 HEAD 时为字面量 {@code HEAD} */
    private static boolean isOnNamedBranch(File dir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String line;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            line = reader.readLine();
        }
        int code = process.waitFor();
        return code == 0 && line != null && !"HEAD".equals(line.trim());
    }

    private static int runGit(File dir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
        return process.waitFor();
    }
}
