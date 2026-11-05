package com.zhoufz.tools.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhoufz
 * 日期 2025/8/30
 */
public class GitUtil {

    /**
     * 解析本机 git 可执行文件。IntelliJ 从 Dock 启动时 PATH 往往不含 Homebrew，
     * 需要用绝对路径启动 git。
     */
    public static String resolveGitExecutable() {
        String envGit = System.getenv("GIT_EXECUTABLE");
        String[] candidates = {
                envGit,
                "/opt/homebrew/bin/git",
                "/usr/local/bin/git",
                "/usr/bin/git"
        };
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            File file = new File(candidate.trim());
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return "git";
    }

    public static void getClone(String gitRep, String targetDir)  {
        try {
            ProcessBuilder pb = gitProcess(new File(targetDir), true, "clone", gitRep);
            Process process = pb.start();
            printProcessOutput(process);
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
            File workDir = new File(targetDir);
            File moduleDir = new File(workDir, moduleName);
            if (isValidGitRepository(moduleDir.getAbsolutePath())) {
                System.out.println("子模块已存在，跳过：" + moduleDir.getAbsolutePath());
                return;
            }
            File gitRoot = findGitRoot(workDir);
            if (gitRoot == null) {
                System.out.println("不是 git 仓库，无法拉取子模块：" + targetDir);
                return;
            }
            String relPath = gitRoot.toPath().toAbsolutePath().normalize()
                    .relativize(moduleDir.toPath().toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
            int initCode = runGit(gitRoot, "git", "submodule", "update", "--init", "--", relPath);
            if (initCode == 0 && isValidGitRepository(moduleDir.getAbsolutePath())) {
                System.out.println("Git submodule init " + moduleName + " 成功！");
                return;
            }
            System.out.println("submodule update --init 未完成，尝试 submodule add：" + gitRep);
            ProcessBuilder pb = gitProcess(workDir, true, "submodule", "add", gitRep, moduleName);
            Process process = pb.start();
            printProcessOutput(process);
            int exitCode = process.waitFor();
            if (exitCode == 0 && isValidGitRepository(moduleDir.getAbsolutePath())) {
                System.out.println("Git clone " + gitRep + " 成功！");
                return;
            }
            System.out.println("submodule add 未完成，改为 clone：" + gitRep);
            ProcessBuilder clonePb = gitProcess(workDir, true, "clone", gitRep, moduleName);
            Process cloneProcess = clonePb.start();
            printProcessOutput(cloneProcess);
            int cloneCode = cloneProcess.waitFor();
            if (cloneCode == 0) {
                System.out.println("Git clone " + gitRep + " 成功！");
            } else {
                System.out.println("Git clone " + gitRep + " 失败，退出码：" + cloneCode);
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
            ProcessBuilder pb = gitProcess(dir, false, "remote", "-v");
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
     * 执行 git pull 更新指定目录的仓库。
     * <p>首次 pull 失败时（常见于本地改动与远程冲突），自动执行
     * {@code stash → pull → stash pop}；二次 pull 仍失败会尽量 stash pop 还原本地改动。</p>
     * <p>{@code stash pop} 若产生冲突：一律以 <b>stash 本地版</b>为准
     *（{@code git checkout --theirs}），再 add 并 drop 该 stash，避免留下冲突标记。</p>
     *
     * @param repoDir 仓库目录路径
     * @return 是否更新成功（以 pull 是否成功为准；stash pop 冲突会打日志但仍可能返回 true）
     */
    /**
     * git pull；若仓库含 submodule，默认也会 recurse + foreach pull（兼容旧调用）。
     * 前端 HUI 等场景请用 {@link #gitPull(String, boolean)} 并传 {@code updateSubmodules=false}。
     */
    public static boolean gitPull(String repoDir) {
        return gitPull(repoDir, true);
    }

    /**
     * @param updateSubmodules false 时仅 pull 当前仓，不 --recurse-submodules、不 foreach pull
     */
    public static boolean gitPull(String repoDir, boolean updateSubmodules) {
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

            boolean pullSubs = updateSubmodules && hasSubmodules(repoDir);
            if (hasSubmodules(repoDir) && !updateSubmodules) {
                System.out.println("======开始更新（跳过 submodules）：" + repoDir);
            } else if (pullSubs) {
                System.out.println("======开始更新（包含submodules）：" + repoDir);
            } else {
                System.out.println("======开始更新：" + repoDir);
            }

            if (doGitPullOnce(dir, pullSubs)) {
                return true;
            }

            System.out.println("Git pull 失败，尝试 stash → pull → stash pop：" + repoDir);
            if (!stashPushForPull(dir)) {
                System.out.println("stash 失败或无可暂存改动，放弃重试：" + repoDir);
                return false;
            }

            boolean pulled = doGitPullOnce(dir, pullSubs);
            boolean popped = stashPopAfterPull(dir);
            if (pulled) {
                if (!popped) {
                    System.out.println("[WARN] pull 已成功，但 stash 还原仍失败，请检查 git status / stash list：" + repoDir);
                } else {
                    System.out.println("Git pull（经 stash，冲突已按本地 stash 版处理）" + repoDir + " 成功！");
                }
                return true;
            }
            System.out.println("stash 后再次 pull 仍失败：" + repoDir);
            if (!popped) {
                System.out.println("[WARN] stash pop/冲突处理失败，本地改动可能仍在 stash 中，请检查 git stash list：" + repoDir);
            }
            return false;
        } catch (Exception e) {
            System.out.println("Git pull " + repoDir + " 发生异常：");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 执行一次 pull；{@code pullSubmodules=true} 时带 submodule 后续更新。
     */
    private static boolean doGitPullOnce(File dir, boolean pullSubmodules) throws Exception {
        ProcessBuilder pb = pullSubmodules
                ? gitProcess(dir, false, "pull", "--recurse-submodules")
                : gitProcess(dir, false, "pull");
        Process process = pb.start();
        printProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode == 0 && pullSubmodules) {
            System.out.println("------检查并更新 submodules：" + dir.getAbsolutePath());
            ProcessBuilder pb2 = gitProcess(dir, false,
                    "submodule", "foreach", resolveGitExecutable(), "pull", "origin", "HEAD");
            Process process2 = pb2.start();

            int updatedCount = 0;
            int unchangedCount = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process2.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                String currentSubmodule = "";
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Entering '") || line.startsWith("正在进入 '")) {
                        currentSubmodule = line;
                    } else if (line.contains("Updating") || line.contains("Fast-forward")
                            || line.contains("更新") || line.contains("快进")) {
                        if (!currentSubmodule.isEmpty()) {
                            System.out.println(currentSubmodule);
                            currentSubmodule = "";
                        }
                        System.out.println("  " + line);
                        updatedCount++;
                    } else if (line.contains("Already up to date") || line.contains("已经是最新")) {
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
                System.out.println("Git pull (含submodules) " + dir.getAbsolutePath() + " 成功！");
                return true;
            }
            System.out.println("Submodule 更新失败，退出码：" + exitCode2);
            return false;
        }
        if (exitCode == 0) {
            System.out.println("Git pull " + dir.getAbsolutePath() + " 成功！");
            return true;
        }
        System.out.println("Git pull " + dir.getAbsolutePath() + " 失败，退出码：" + exitCode);
        return false;
    }

    /**
     * 暂存本地改动（含未跟踪文件），便于 pull。
     *
     * @return true 表示 stash 成功且有条目被创建
     */
    private static boolean stashPushForPull(File dir) throws Exception {
        System.out.println("------ git stash push -u -m tools-auto-stash-before-pull：" + dir.getAbsolutePath());
        int before = stashListSize(dir);
        ProcessBuilder pb = gitProcess(dir, false,
                "stash", "push", "-u", "-m", "tools-auto-stash-before-pull");
        Process process = pb.start();
        printProcessOutput(process);
        int code = process.waitFor();
        if (code != 0) {
            System.out.println("stash push 失败，退出码：" + code);
            return false;
        }
        int after = stashListSize(dir);
        if (after <= before) {
            // 常见：No local changes to save
            System.out.println("stash 后条目数未增加，可能没有可暂存改动");
            return false;
        }
        return true;
    }

    private static boolean stashPopAfterPull(File dir) throws Exception {
        System.out.println("------ git stash pop：" + dir.getAbsolutePath());
        ProcessBuilder pb = gitProcess(dir, false, "stash", "pop");
        Process process = pb.start();
        printProcessOutput(process);
        int code = process.waitFor();
        if (code == 0) {
            return true;
        }
        System.out.println("stash pop 失败，退出码：" + code + "，尝试按「stash 本地版」自动解决冲突…");
        return resolveStashConflictsPreferStash(dir);
    }

    /**
     * stash apply/pop 冲突时：stage2=Updated upstream(HEAD)，stage3=Stashed changes。
     * 用户约定冲突一律以 stash 为准 → {@code checkout --theirs}。
     */
    private static boolean resolveStashConflictsPreferStash(File dir) throws Exception {
        List<String> unmerged = listUnmergedFiles(dir);
        if (unmerged.isEmpty()) {
            System.out.println("没有未合并文件，无法自动按 stash 解决：" + dir.getAbsolutePath());
            return false;
        }
        System.out.println("冲突文件 " + unmerged.size() + " 个，全部取 Stashed changes（--theirs）：");
        for (String path : unmerged) {
            System.out.println("  - " + path);
            int checkoutCode = runGit(dir, "git", "checkout", "--theirs", "--", path);
            if (checkoutCode != 0) {
                System.out.println("[WARN] checkout --theirs 失败：" + path);
                return false;
            }
            int addCode = runGit(dir, "git", "add", "--", path);
            if (addCode != 0) {
                System.out.println("[WARN] git add 失败：" + path);
                return false;
            }
        }
        List<String> still = listUnmergedFiles(dir);
        if (!still.isEmpty()) {
            System.out.println("[WARN] 仍有未合并文件：" + still);
            return false;
        }
        // pop 冲突时 stash 条目不会自动删除，需手动 drop
        System.out.println("------ git stash drop（冲突已按本地 stash 解决）：" + dir.getAbsolutePath());
        int dropCode = runGit(dir, "git", "stash", "drop");
        if (dropCode != 0) {
            System.out.println("[WARN] stash drop 失败，请检查 git stash list：" + dir.getAbsolutePath());
            // 工作区已按 stash 修好，仍视为成功
        }
        System.out.println("stash 冲突已按本地版解决：" + dir.getAbsolutePath());
        return true;
    }

    private static List<String> listUnmergedFiles(File dir) throws Exception {
        ProcessBuilder pb = gitProcess(dir, false, "diff", "--name-only", "--diff-filter=U");
        Process process = pb.start();
        List<String> files = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String path = line.trim();
                if (!path.isEmpty()) {
                    files.add(path);
                }
            }
        }
        // 读完 stdout 后再排空 stderr，避免阻塞
        try (BufferedReader err = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            while (err.readLine() != null) {
                // ignore
            }
        }
        process.waitFor();
        return files;
    }

    private static int stashListSize(File dir) throws Exception {
        ProcessBuilder pb = gitProcess(dir, false, "stash", "list");
        Process process = pb.start();
        int count = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        process.waitFor();
        return count;
    }

    /**
     * Git 切换分支选项。默认走轻量路径：不拉全量 tags、切换后不 pull、不递归更新 submodule。
     */
    public static class CheckoutOptions {
        /** 是否额外拉取 tags（仅切 tag / submodule 钉版本时需要） */
        public boolean fetchTags = false;
        /** 切换成功后是否再 git pull（默认 false：fetch+checkout -B 已对齐） */
        public boolean pullAfter = false;
        /** 是否 submodule update --init --recursive（默认 false） */
        public boolean updateSubmodules = false;
        /** 已在目标分支/标签时跳过（默认 true） */
        public boolean skipIfAlreadyOnRef = true;

        public static CheckoutOptions defaults() {
            return new CheckoutOptions();
        }
    }

    /**
     * 使用默认轻量选项切换分支/标签。
     *
     * @see #gitCheckoutWithResult(String, String, CheckoutOptions)
     */
    public static boolean gitCheckout(String repoDir, String ref) {
        Map<String, Object> result = gitCheckoutWithResult(repoDir, ref, CheckoutOptions.defaults());
        return Boolean.TRUE.equals(result.get("success"));
    }

    /**
     * 在指定仓库切换到分支或标签（优化版）。
     * <p>默认：先本地 checkout → 已有 origin 跟踪分支 → 单 ref fetch（--no-tags）→ 再匹配 hotfix/release；
     * 仅在显式勾选或目标明确是 tag 时才拉标签。默认不 pull、不 recursive submodule。</p>
     *
     * @return success/skipped/status/message/branch/timings 等
     */
    public static Map<String, Object> gitCheckoutWithResult(String repoDir, String ref, CheckoutOptions options) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> timings = new LinkedHashMap<>();
        result.put("timings", timings);
        result.put("repoPath", repoDir);
        result.put("ref", ref);
        long t0 = System.currentTimeMillis();
        CheckoutOptions opt = options == null ? CheckoutOptions.defaults() : options;
        try {
            File dir = new File(repoDir);
            if (!dir.exists() || !dir.isDirectory()) {
                return failCheckout(result, timings, t0, "目录不存在：" + repoDir);
            }
            if (!isValidGitRepository(repoDir)) {
                return failCheckout(result, timings, t0, "不是有效的git仓库：" + repoDir);
            }

            String rawRef = ref == null ? "" : ref.trim();
            if (rawRef.isEmpty()) {
                return failCheckout(result, timings, t0, "分支/标签不能为空");
            }
            boolean tagIntent = isTagIntent(rawRef);
            String normalized = normalizeCheckoutRef(rawRef);
            result.put("normalizedRef", normalized);
            result.put("tagIntent", tagIntent);

            String current = currentBranch(repoDir);
            result.put("branchBefore", current);
            if (opt.skipIfAlreadyOnRef && alreadyOnRef(current, normalized, rawRef)) {
                result.put("success", true);
                result.put("skipped", true);
                result.put("status", "skipped");
                result.put("message", "已在目标：" + current);
                result.put("branch", current);
                timings.put("totalMs", System.currentTimeMillis() - t0);
                System.out.println("[checkout] 跳过（已在目标）" + repoDir + " → " + current);
                return result;
            }

            // 1) 先本地尝试（零网络）
            long tLocal = System.currentTimeMillis();
            int checkoutCode = tryCheckoutRef(dir, normalized);
            timings.put("localCheckoutMs", System.currentTimeMillis() - tLocal);

            // 2) 本地失败：按需窄 fetch，再试
            if (checkoutCode != 0) {
                long tFetch = System.currentTimeMillis();
                boolean needTags = opt.fetchTags || tagIntent;
                fetchForCheckout(dir, normalized, needTags);
                timings.put("fetchMs", System.currentTimeMillis() - tFetch);

                long tRetry = System.currentTimeMillis();
                checkoutCode = tryCheckoutRef(dir, normalized);
                timings.put("retryCheckoutMs", System.currentTimeMillis() - tRetry);
            } else {
                timings.put("fetchMs", 0L);
            }

            if (checkoutCode != 0) {
                System.out.println("------ checkout 失败，列出 origin 远程分支便于核对：" + repoDir);
                runGit(dir, "git", "branch", "-r", "--sort=refname");
                return failCheckout(result, timings, t0, "git checkout 失败，退出码=" + checkoutCode);
            }

            if (opt.updateSubmodules && hasSubmodules(repoDir)) {
                long tSub = System.currentTimeMillis();
                System.out.println("------ git submodule update --init --recursive：" + repoDir);
                int subCode = runGit(dir, "git", "submodule", "update", "--init", "--recursive");
                timings.put("submoduleMs", System.currentTimeMillis() - tSub);
                if (subCode != 0) {
                    return failCheckout(result, timings, t0, "submodule 更新失败，退出码=" + subCode);
                }
            } else {
                timings.put("submoduleMs", 0L);
                if (hasSubmodules(repoDir) && !opt.updateSubmodules) {
                    System.out.println("------ 跳过 submodule 更新（未勾选）：" + repoDir);
                }
            }

            if (opt.pullAfter && isOnNamedBranch(dir)) {
                long tPull = System.currentTimeMillis();
                System.out.println("------ 切换后 git pull：" + repoDir);
                boolean pulled = gitPull(repoDir, opt.updateSubmodules);
                timings.put("pullMs", System.currentTimeMillis() - tPull);
                if (!pulled) {
                    return failCheckout(result, timings, t0, "切换成功但后续 git pull 失败");
                }
            } else {
                timings.put("pullMs", 0L);
                if (!opt.pullAfter) {
                    System.out.println("------ 跳过 git pull（未勾选）：" + repoDir);
                }
            }

            String after = currentBranch(repoDir);
            result.put("success", true);
            result.put("skipped", false);
            result.put("status", "ok");
            result.put("message", "切换成功");
            result.put("branch", after);
            timings.put("totalMs", System.currentTimeMillis() - t0);
            System.out.println("Git checkout " + normalized + " @ " + repoDir
                    + " 成功！耗时 " + timings.get("totalMs") + " ms");
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return failCheckout(result, timings, t0, "异常：" + e.getMessage());
        }
    }

    private static Map<String, Object> failCheckout(Map<String, Object> result, Map<String, Object> timings,
                                                    long startedAt, String message) {
        System.out.println("[checkout] 失败：" + message);
        result.put("success", false);
        result.put("skipped", false);
        result.put("status", "failed");
        result.put("message", message);
        timings.put("totalMs", System.currentTimeMillis() - startedAt);
        return result;
    }

    /** 去掉 tag/、tags/、refs/tags/ 前缀，得到真实 ref 名。 */
    public static String normalizeCheckoutRef(String ref) {
        String r = ref.trim();
        if (r.regionMatches(true, 0, "refs/tags/", 0, "refs/tags/".length())) {
            return r.substring("refs/tags/".length());
        }
        if (r.regionMatches(true, 0, "tags/", 0, "tags/".length())) {
            return r.substring("tags/".length());
        }
        if (r.regionMatches(true, 0, "tag/", 0, "tag/".length())) {
            return r.substring("tag/".length());
        }
        return r;
    }

    /** 用户明确按 tag 意图填写（带 tag/ 等前缀）。 */
    public static boolean isTagIntent(String ref) {
        String r = ref.trim();
        return r.regionMatches(true, 0, "tag/", 0, 4)
                || r.regionMatches(true, 0, "tags/", 0, 5)
                || r.regionMatches(true, 0, "refs/tags/", 0, 10);
    }

    public static boolean alreadyOnRef(String current, String normalized, String rawRef) {
        if (current == null || current.isEmpty() || normalized == null || normalized.isEmpty()) {
            return false;
        }
        if (current.equals(normalized) || current.equals(rawRef)) {
            return true;
        }
        // describe 结果：tag/xxx 或 xxx-12-gabcdef
        if (current.equals("tag/" + normalized) || current.startsWith(normalized + "-")) {
            return true;
        }
        if (current.endsWith("/" + normalized)) {
            return true;
        }
        return false;
    }

    /**
     * 窄 fetch：只拉需要的 branch/tag，默认 --no-tags，避免全量 tags。
     */
    private static void fetchForCheckout(File dir, String normalized, boolean fetchTags) throws Exception {
        System.out.println("====== 窄 fetch（" + (fetchTags ? "含目标 tag" : "无 tags") + "）：" + dir.getPath()
                + " ref=" + normalized);

        // 1) 直接按用户给的名字当分支拉
        if (fetchSingleBranch(dir, normalized) == 0) {
            return;
        }

        // 2) 无斜杠时尝试常见发布前缀（hotfix/版本 等）
        if (!normalized.contains("/")) {
            String[] prefixes = {"hotfix/", "release/", "bugfix/", "patch/", "feature/"};
            for (String prefix : prefixes) {
                if (fetchSingleBranch(dir, prefix + normalized) == 0) {
                    return;
                }
            }
        }

        // 3) tag：仅显式需要时
        if (fetchTags) {
            System.out.println("------ fetch 单个 tag：" + normalized);
            int tagCode = runGit(dir, "git", "fetch", "origin", "tag", normalized, "--no-tags");
            if (tagCode == 0) {
                return;
            }
            runGit(dir, "git", "fetch", "origin",
                    "refs/tags/" + normalized + ":refs/tags/" + normalized, "--no-tags");
            return;
        }

        // 4) 兜底：全量分支、仍不拉 tags（比旧逻辑 --tags 轻得多）
        System.out.println("------ 兜底 fetch origin --prune --no-tags：" + dir.getPath());
        runGit(dir, "git", "fetch", "origin", "--prune", "--no-tags");
    }

    private static int fetchSingleBranch(File dir, String branchName) throws Exception {
        System.out.println("------ fetch 单分支：" + branchName);
        return runGit(dir, "git", "fetch", "origin",
                "refs/heads/" + branchName + ":refs/remotes/origin/" + branchName,
                "--prune", "--no-tags");
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
        ProcessBuilder pb = gitProcess(dir, false,
                "for-each-ref", "--format=%(refname:short)", "refs/remotes/origin/");
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

    /**
     * 是否为 submodule 工作区（{@code .git} 为文件，指向父仓 modules）。
     */
    public static boolean isSubmoduleWorkingTree(String repoDir) {
        File git = new File(repoDir, ".git");
        return git.isFile();
    }

    /**
     * 读取父仓 {@code .gitmodules} 中已初始化（存在且有效）的 submodule 绝对路径。
     */
    public static List<String> listInitializedSubmodulePaths(String parentRepoPath) {
        List<String> result = new ArrayList<>();
        File gitmodules = new File(parentRepoPath, ".gitmodules");
        if (!gitmodules.isFile()) {
            return result;
        }
        try {
            String currentPath = null;
            for (String line : java.nio.file.Files.readAllLines(gitmodules.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[submodule")) {
                    addSubmoduleIfInitialized(parentRepoPath, currentPath, result);
                    currentPath = null;
                } else if (trimmed.startsWith("path = ")) {
                    currentPath = trimmed.substring("path = ".length()).trim();
                }
            }
            addSubmoduleIfInitialized(parentRepoPath, currentPath, result);
        } catch (Exception e) {
            System.out.println("读取 .gitmodules 失败：" + parentRepoPath + " → " + e.getMessage());
        }
        return result;
    }

    private static void addSubmoduleIfInitialized(String parentRepoPath, String relativePath,
                                                  List<String> result) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return;
        }
        File sub = new File(parentRepoPath, relativePath.trim());
        String abs = sub.getAbsolutePath();
        if (result.contains(abs)) {
            return;
        }
        if (isValidGitRepository(abs)) {
            result.add(abs);
        }
    }

    /**
     * 查询仓库当前分支/检出位置。
     * <p>命名分支返回分支名；分离 HEAD（含 submodule 钉死提交）用 {@code git describe --tags --always}
     * 展示版本，例如 {@code tag/IFMS6.0V202607.04.000-12-ga2e3c2931}。</p>
     *
     * @return 分支描述；失败返回空串
     */
    public static String currentBranch(String repoDir) {
        try {
            File dir = new File(repoDir);
            if (!dir.isDirectory() || !isValidGitRepository(repoDir)) {
                return "";
            }
            String abbrev = readGitOneLine(dir, "rev-parse", "--abbrev-ref", "HEAD");
            if (abbrev != null && !abbrev.isEmpty() && !"HEAD".equals(abbrev)) {
                return abbrev;
            }
            String exact = readGitOneLine(dir, "describe", "--tags", "--exact-match");
            if (exact != null && !exact.isEmpty()) {
                return exact;
            }
            // submodule 常见：停在某 tag 之后若干提交，exact-match 失败，用 always 带出版本
            String describe = readGitOneLine(dir, "describe", "--tags", "--always");
            if (describe != null && !describe.isEmpty()) {
                return describe;
            }
            String sha = readGitOneLine(dir, "rev-parse", "--short", "HEAD");
            if (sha != null && !sha.isEmpty()) {
                return "detached:" + sha;
            }
            return "HEAD";
        } catch (Exception e) {
            System.out.println("查询 Git 分支失败：" + repoDir + " → " + e.getMessage());
            return "";
        }
    }

    private static String readGitOneLine(File dir, String... gitArgs) throws Exception {
        ProcessBuilder pb = gitProcess(dir, false, gitArgs);
        Process process = pb.start();
        String line;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            line = reader.readLine();
        }
        // 丢弃 stderr，避免阻塞
        try (BufferedReader err = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            while (err.readLine() != null) {
                // ignore
            }
        }
        int code = process.waitFor();
        if (code != 0 || line == null) {
            return "";
        }
        return line.trim();
    }

    /** {@code git rev-parse --abbrev-ref HEAD} 在分离 HEAD 时为字面量 {@code HEAD} */
    private static boolean isOnNamedBranch(File dir) throws Exception {
        String line = readGitOneLine(dir, "rev-parse", "--abbrev-ref", "HEAD");
        return line != null && !line.isEmpty() && !"HEAD".equals(line);
    }

    private static int runGit(File dir, String... command) throws Exception {
        String[] args = command;
        if (command.length > 0 && "git".equals(command[0])) {
            args = Arrays.copyOfRange(command, 1, command.length);
        }
        ProcessBuilder pb = gitProcess(dir, false, args);
        Process process = pb.start();
        printProcessOutput(process);
        return process.waitFor();
    }

    private static ProcessBuilder gitProcess(File workDir, boolean createIfMissing, String... gitArgs) {
        String[] command = new String[gitArgs.length + 1];
        command[0] = resolveGitExecutable();
        System.arraycopy(gitArgs, 0, command, 1, gitArgs.length);
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) {
            if (createIfMissing) {
                ensureDirectory(workDir);
            }
            pb.directory(workDir);
        }
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        String path = env.get("PATH");
        String extra = "/opt/homebrew/bin" + File.pathSeparator + "/usr/local/bin"
                + File.pathSeparator + "/usr/bin" + File.pathSeparator + "/bin";
        env.put("PATH", extra + (path == null || path.isEmpty() ? "" : File.pathSeparator + path));
        return pb;
    }

    static void ensureDirectory(File dir) {
        if (dir == null) {
            return;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建目录：" + dir.getAbsolutePath());
        }
        if (!dir.isDirectory()) {
            throw new IllegalStateException("不是目录：" + dir.getAbsolutePath());
        }
    }

    private static File findGitRoot(File dir) {
        File current = dir == null ? null : dir.getAbsoluteFile();
        while (current != null) {
            File git = new File(current, ".git");
            if (git.exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private static void printProcessOutput(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}
