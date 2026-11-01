package com.zhoufz.tools.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 本机 SVN 工作副本工具（可执行文件解析 + update）。
 *
 * @author zhoufz
 */
public class SvnUtil {

    /**
     * 解析本机 svn 可执行文件。IntelliJ 从 Dock 启动时 PATH 往往不含 Homebrew。
     */
    public static String resolveSvnExecutable() {
        String envSvn = System.getenv("SVN_EXECUTABLE");
        String[] candidates = {
                envSvn,
                "/opt/homebrew/bin/svn",
                "/usr/local/bin/svn",
                "/usr/bin/svn"
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
        return "svn";
    }

    /**
     * 是否为 SVN 工作副本根（存在 .svn）。
     */
    public static boolean isValidSvnWorkingCopy(String repoDir) {
        if (repoDir == null || repoDir.trim().isEmpty()) {
            return false;
        }
        File svnDir = new File(repoDir, ".svn");
        return svnDir.isDirectory();
    }

    /**
     * 查询 SVN 工作副本当前相对路径（如 {@code ^/trunk}、{@code ^/branches/xxx}）。
     * 取不到 relative-url 时回退为完整 URL。
     */
    public static String currentBranch(String repoDir) {
        if (!isValidSvnWorkingCopy(repoDir)) {
            return "";
        }
        File dir = new File(repoDir);
        try {
            String relative = readSvnInfoItem(dir, "relative-url");
            if (relative != null && !relative.isEmpty()) {
                return relative;
            }
            String url = readSvnInfoItem(dir, "url");
            return url == null ? "" : url;
        } catch (Exception e) {
            System.out.println("查询 SVN 分支失败：" + repoDir + " → " + e.getMessage());
            return "";
        }
    }

    /**
     * 列出远程目录子项（目录名去掉末尾 {@code /}）。失败返回空列表。
     */
    public static List<String> svnList(String url) {
        if (url == null || url.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String remote = trimSlash(url.trim());
        try {
            ProcessBuilder pb = svnProcess(new File("."), "list", remote);
            Process process = pb.start();
            List<String> names = new ArrayList<>();
            StringBuilder err = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String name = line.trim();
                    if (name.endsWith("/")) {
                        name = name.substring(0, name.length() - 1);
                    }
                    if (!name.isEmpty()) {
                        names.add(name);
                    }
                }
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    err.append(line).append('\n');
                    System.out.println("[svn-err] " + line);
                }
            }
            int code = process.waitFor();
            if (code != 0) {
                System.out.println("SVN list 失败：" + remote + "，退出码 " + code);
                if (err.length() > 0) {
                    System.out.println(err);
                }
                return Collections.emptyList();
            }
            return names;
        } catch (Exception e) {
            System.out.println("SVN list 异常：" + remote);
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * 读取工作副本 {@code svn info --show-item}；失败返回空串。
     */
    public static String infoItem(String repoDir, String item) {
        if (!isValidSvnWorkingCopy(repoDir) || item == null || item.trim().isEmpty()) {
            return "";
        }
        try {
            String value = readSvnInfoItem(new File(repoDir), item.trim());
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    private static String readSvnInfoItem(File workDir, String item) throws Exception {
        ProcessBuilder pb = svnProcess(workDir, "info", "--show-item", item);
        Process process = pb.start();
        String line;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            line = reader.readLine();
        }
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

    /**
     * 对指定工作副本执行 {@code svn update}。
     *
     * @return true 表示成功
     */
    public static boolean svnUpdate(String repoDir) {
        if (!isValidSvnWorkingCopy(repoDir)) {
            System.out.println("不是有效的 SVN 工作副本：" + repoDir);
            return false;
        }
        File dir = new File(repoDir);
        try {
            ProcessBuilder pb = svnProcess(dir, "update");
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[svn] " + line);
                    output.append(line).append('\n');
                }
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[svn-err] " + line);
                    output.append(line).append('\n');
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("SVN update " + repoDir + " 成功！");
                return true;
            }
            System.out.println("SVN update " + repoDir + " 失败，退出码：" + exitCode);
            if (output.length() > 0) {
                System.out.println(output);
            }
            return false;
        } catch (Exception e) {
            System.out.println("SVN update " + repoDir + " 发生异常：");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检出 SVN 目录到本地。若目标已是工作副本且 URL 一致则改为 {@code svn update}；
     * URL 不一致则失败；目标非空且非 WC 则失败。
     *
     * @param url       远程叶子 URL
     * @param localPath 本地落点目录
     * @return true 表示成功
     */
    public static boolean svnCheckout(String url, String localPath) {
        if (url == null || url.trim().isEmpty()) {
            System.out.println("SVN checkout URL 不能为空");
            return false;
        }
        if (localPath == null || localPath.trim().isEmpty()) {
            System.out.println("SVN checkout 本地路径不能为空");
            return false;
        }
        String remote = url.trim();
        File dir = new File(localPath.trim());
        try {
            if (isValidSvnWorkingCopy(dir.getAbsolutePath())) {
                String currentUrl = readSvnInfoItem(dir, "url");
                if (currentUrl != null && !currentUrl.isEmpty()
                        && !urlsMatch(currentUrl, remote)) {
                    System.out.println("本地已是其它 URL 的工作副本，拒绝覆盖："
                            + dir.getAbsolutePath() + " → " + currentUrl + "，期望 " + remote);
                    return false;
                }
                System.out.println("本地已是目标工作副本，改为 update：" + dir.getAbsolutePath());
                return svnUpdate(dir.getAbsolutePath());
            }
            if (dir.exists()) {
                String[] children = dir.list();
                if (children != null && children.length > 0) {
                    System.out.println("本地目录非空且不是 SVN 工作副本，拒绝 checkout：" + dir.getAbsolutePath());
                    return false;
                }
            }
            File parent = dir.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                System.out.println("无法创建父目录：" + parent.getAbsolutePath());
                return false;
            }
            System.out.println("====== svn checkout " + remote + " → " + dir.getAbsolutePath());
            // svn checkout 可在任意 cwd 下指定绝对目标路径
            ProcessBuilder pb = svnProcess(parent != null ? parent : new File("."),
                    "checkout", remote, dir.getAbsolutePath());
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[svn] " + line);
                }
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[svn-err] " + line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("SVN checkout 成功：" + dir.getAbsolutePath());
                return true;
            }
            System.out.println("SVN checkout 失败，退出码：" + exitCode);
            return false;
        } catch (Exception e) {
            System.out.println("SVN checkout 发生异常：" + localPath);
            e.printStackTrace();
            return false;
        }
    }

    private static boolean urlsMatch(String a, String b) {
        String left = trimSlash(a);
        String right = trimSlash(b);
        return left.equalsIgnoreCase(right);
    }

    private static String trimSlash(String url) {
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * 在工作副本中执行 {@code svn switch}。
     *
     * @param repoDir 工作副本根目录
     * @param target  目标 URL，或相对仓库路径（如 {@code ^/branches/xxx}）
     * @return true 表示成功
     */
    public static boolean svnSwitch(String repoDir, String target) {
        if (!isValidSvnWorkingCopy(repoDir)) {
            System.out.println("不是有效的 SVN 工作副本：" + repoDir);
            return false;
        }
        if (target == null || target.trim().isEmpty()) {
            System.out.println("SVN switch 目标不能为空：" + repoDir);
            return false;
        }
        File dir = new File(repoDir);
        try {
            ProcessBuilder pb = svnProcess(dir, "switch", target.trim());
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[svn] " + line);
                }
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[svn-err] " + line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("SVN switch " + target + " @ " + repoDir + " 成功！");
                return true;
            }
            System.out.println("SVN switch " + repoDir + " 失败，退出码：" + exitCode);
            return false;
        } catch (Exception e) {
            System.out.println("SVN switch " + repoDir + " 发生异常：");
            e.printStackTrace();
            return false;
        }
    }

    private static ProcessBuilder svnProcess(File workDir, String... svnArgs) {
        String[] command = new String[svnArgs.length + 1];
        command[0] = resolveSvnExecutable();
        System.arraycopy(svnArgs, 0, command, 1, svnArgs.length);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(false);
        return pb;
    }
}
