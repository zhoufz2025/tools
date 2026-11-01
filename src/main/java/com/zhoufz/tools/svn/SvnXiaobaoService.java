package com.zhoufz.tools.svn;

import com.zhoufz.tools.util.SvnUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SVN 小包拉包：按分支拼叶子 URL，checkout 到 svn/branch/{短名}/ 下。
 *
 * @author zhoufz
 */
@Service
public class SvnXiaobaoService {

    private static final Pattern BRANCH_SHORT = Pattern.compile("IFMS6\\.0V(\\d{6}\\.\\d{2})", Pattern.CASE_INSENSITIVE);

    private static final List<Map<String, String>> SALE_MODULES = Arrays.asList(
            module("lcpt-dxasset", "资管"),
            module("lcpt-dxfund", "基金"),
            module("lcpt-dxtrust", "信托"),
            module("lcpt-fina", "理财"),
            module("lcpt-pub", "公共"),
            module("lcpt-web", "管理台后端")
    );

    private static final List<Map<String, String>> FRONT_MODULES = Arrays.asList(
            module("console-dxasset-vue", "资管前端"),
            module("console-dxfund-vue", "基金前端"),
            module("console-dxtrust-vue", "信托前端"),
            module("console-fina-vue", "理财前端"),
            module("console-bizframe-vue", "业务框架前端"),
            module("console-account-vue", "账户前端")
    );

    private final SvnXiaobaoProperties properties;

    public SvnXiaobaoService(SvnXiaobaoProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> presets() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("localRoot", properties.getLocalRoot());
        data.put("repoRoot", properties.getRepoRoot());
        data.put("branchPrefix", properties.getBranchPrefix());
        data.put("saleModules", SALE_MODULES);
        data.put("frontModules", FRONT_MODULES);
        return data;
    }

    /**
     * 列出该 fix 分支下 spsql 可选脚本版本（按分支短名过滤，如 202405.05 → IFMS6.0V202405.05.*），新版本在前。
     */
    public Map<String, Object> listSpsqlVersions(String branchName) {
        String branch = requireBranch(branchName);
        String shortName = shortName(branch);
        if (!StringUtils.hasText(shortName)) {
            throw new IllegalArgumentException("无法从分支名解析短名：" + branch);
        }
        String spsqlUrl = sourcesBase(branch) + "/app/spsql";
        String prefix = "IFMS6.0V" + shortName;
        List<String> all = SvnUtil.svnList(spsqlUrl);
        List<String> matched = all.stream()
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length()))
                .sorted(Collections.reverseOrder(SvnXiaobaoService::compareVersionName))
                .collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("branch", branch);
        data.put("shortName", shortName);
        data.put("spsqlUrl", spsqlUrl);
        data.put("prefix", prefix);
        data.put("totalListed", all.size());
        data.put("versions", matched);
        return data;
    }

    /** 版本目录名比较：IFMS6.0V202405.05.059 > …056M2 > …056 */
    static int compareVersionName(String a, String b) {
        return normalizeVersionKey(a).compareTo(normalizeVersionKey(b));
    }

    private static String normalizeVersionKey(String name) {
        if (name == null) {
            return "";
        }
        // 便于字典序：补齐数字段；M 补丁排在同号正式版之后（reverse 后正式版更靠前时再按需）
        Matcher m = Pattern.compile("IFMS6\\.0V(\\d{6})\\.(\\d{2})\\.(\\d+)([A-Za-z]*)(\\d*)",
                Pattern.CASE_INSENSITIVE).matcher(name.trim());
        if (!m.find()) {
            return name.toUpperCase();
        }
        String patch = m.group(4) == null ? "" : m.group(4).toUpperCase();
        String patchNo = m.group(5) == null || m.group(5).isEmpty() ? "0" : m.group(5);
        // 无字母补丁的正式版 patch 空 → 用 ~ 使正式版在同号 M 版之后（reverseOrder 后正式版更前）
        String patchRank = patch.isEmpty() ? "~" : patch;
        return String.format("%s.%s.%06d.%s.%06d",
                m.group(1), m.group(2), Integer.parseInt(m.group(3)), patchRank, Integer.parseInt(patchNo));
    }

    /**
     * 从完整分支名提取短名，如 IFMS6.0V202405.05.001 → 202405.05
     */
    public String shortName(String branchName) {
        if (!StringUtils.hasText(branchName)) {
            return "";
        }
        Matcher matcher = BRANCH_SHORT.matcher(branchName.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return branchName.trim();
    }

    public List<Map<String, String>> preview(String branchName,
                                             String localRootOverride,
                                             List<String> saleModules,
                                             List<String> spsqlVersions,
                                             List<String> frontModules,
                                             boolean includeCounter) {
        String branch = requireBranch(branchName);
        String shortName = shortName(branch);
        String localRoot = resolveLocalRoot(localRootOverride);
        String sourcesBase = sourcesBase(branch);
        File branchDir = new File(localRoot, shortName);

        List<Map<String, String>> rows = new ArrayList<>();
        for (String module : normalizeList(saleModules)) {
            rows.add(item("sale", module,
                    sourcesBase + "/app/lcpt-server/sale/" + module,
                    new File(branchDir, module).getAbsolutePath()));
        }
        for (String version : normalizeList(spsqlVersions)) {
            rows.add(item("spsql", version,
                    sourcesBase + "/app/spsql/" + version,
                    new File(branchDir, version).getAbsolutePath()));
        }
        for (String front : normalizeList(frontModules)) {
            rows.add(item("front", front,
                    sourcesBase + "/lcpt-front/HUI1.0/console/src/biz/" + front,
                    new File(branchDir, "lcpt-front/biz/" + front).getAbsolutePath()));
        }
        if (includeCounter) {
            rows.add(item("counter", "ifmcounter",
                    sourcesBase + "/ifmcounter",
                    new File(branchDir, "ifmcounter").getAbsolutePath()));
        }
        for (Map<String, String> row : rows) {
            row.put("branch", branch);
            row.put("shortName", shortName);
            row.put("localStatus", detectLocalStatus(row.get("localPath"), row.get("url")));
        }
        return rows;
    }

    public Map<String, Object> checkout(String branchName,
                                        String localRootOverride,
                                        List<String> saleModules,
                                        List<String> spsqlVersions,
                                        List<String> frontModules,
                                        boolean includeCounter) {
        List<Map<String, String>> plan = preview(branchName, localRootOverride,
                saleModules, spsqlVersions, frontModules, includeCounter);
        if (plan.isEmpty()) {
            throw new IllegalArgumentException("请至少选择业务模块、脚本版本、前端或低柜之一");
        }
        int ok = 0;
        int fail = 0;
        List<Map<String, String>> results = new ArrayList<>();
        for (Map<String, String> row : plan) {
            Map<String, String> one = new LinkedHashMap<>(row);
            boolean success = SvnUtil.svnCheckout(row.get("url"), row.get("localPath"));
            one.put("success", String.valueOf(success));
            one.put("localStatus", detectLocalStatus(row.get("localPath"), row.get("url")));
            results.add(one);
            if (success) {
                ok++;
            } else {
                fail++;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("branch", requireBranch(branchName));
        data.put("shortName", shortName(branchName));
        data.put("ok", ok);
        data.put("fail", fail);
        data.put("items", results);
        return data;
    }

    /**
     * 扫描本地已拉目录（含 .svn 的子目录）。
     */
    public Map<String, Object> scanLocal(String branchName, String localRootOverride) {
        String shortName = StringUtils.hasText(branchName) ? shortName(branchName) : "";
        String localRoot = resolveLocalRoot(localRootOverride);
        File root = StringUtils.hasText(shortName) ? new File(localRoot, shortName) : new File(localRoot);
        List<Map<String, String>> rows = new ArrayList<>();
        if (root.isDirectory()) {
            scanWorkingCopies(root, root, rows);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("localRoot", localRoot);
        data.put("shortName", shortName);
        data.put("scanRoot", root.getAbsolutePath());
        data.put("items", rows);
        return data;
    }

    private void scanWorkingCopies(File scanRoot, File current, List<Map<String, String>> rows) {
        if (!current.isDirectory()) {
            return;
        }
        if (SvnUtil.isValidSvnWorkingCopy(current.getAbsolutePath())) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", scanRoot.toPath().relativize(current.toPath()).toString().replace('\\', '/'));
            row.put("localPath", current.getAbsolutePath());
            row.put("url", SvnUtil.infoItem(current.getAbsolutePath(), "url"));
            row.put("relativeUrl", SvnUtil.infoItem(current.getAbsolutePath(), "relative-url"));
            rows.add(row);
            return;
        }
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory() && !child.getName().startsWith(".")) {
                scanWorkingCopies(scanRoot, child, rows);
            }
        }
    }

    private String sourcesBase(String branchName) {
        String repo = trimSlash(properties.getRepoRoot());
        String prefix = trimSlash(properties.getBranchPrefix());
        return repo + "/" + prefix + "/" + branchName.trim() + "/Sources";
    }

    private String resolveLocalRoot(String override) {
        if (StringUtils.hasText(override)) {
            return trimSlash(override.trim());
        }
        return trimSlash(properties.getLocalRoot());
    }

    private static String requireBranch(String branchName) {
        if (!StringUtils.hasText(branchName)) {
            throw new IllegalArgumentException("分支名不能为空，例如 IFMS6.0V202405.05.001");
        }
        return branchName.trim();
    }

    private static List<String> normalizeList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return raw.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public static List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split("[,，\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private static Map<String, String> module(String code, String label) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("label", label);
        return map;
    }

    private static Map<String, String> item(String kind, String name, String url, String localPath) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("kind", kind);
        map.put("name", name);
        map.put("url", url);
        map.put("localPath", localPath);
        return map;
    }

    private String detectLocalStatus(String localPath, String expectedUrl) {
        if (!StringUtils.hasText(localPath)) {
            return "missing";
        }
        File dir = new File(localPath);
        if (!dir.exists()) {
            return "missing";
        }
        if (!SvnUtil.isValidSvnWorkingCopy(localPath)) {
            return "exists-not-svn";
        }
        String currentUrl = SvnUtil.infoItem(localPath, "url");
        if (StringUtils.hasText(expectedUrl) && StringUtils.hasText(currentUrl)
                && !trimSlash(currentUrl).equalsIgnoreCase(trimSlash(expectedUrl))) {
            return "url-mismatch";
        }
        return "ready";
    }

    private static String trimSlash(String path) {
        String s = path.trim();
        while (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
