package com.zhoufz.tools.copy;

import com.zhoufz.tools.util.FileUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 大包→小包：按源根 A / 目标根 B 做相对路径对齐复制，并检测目录结构风险。
 *
 * @author zhoufz
 */
@Service
public class PackageCopyService {

    /**
     * 相对路径前几级目录需在 B 下连续命中的最小段数。
     * 例如 rel=lcpt-dxasset-trans/.../Foo.java，要求 B 下至少存在 lcpt-dxasset-trans（及更深时再看第 2 段）。
     */
    private static final int MIN_MATCHED_SEGMENTS = 2;

    public Map<String, Object> preview(PackageCopyRequest request) {
        List<Map<String, String>> items = buildItems(request);
        return summary(request, items);
    }

    public Map<String, Object> execute(PackageCopyRequest request) {
        List<Map<String, String>> items = buildItems(request);
        Set<String> force = normalizeForce(request.getForcePaths());
        int ok = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, String>> results = new ArrayList<>();
        for (Map<String, String> item : items) {
            Map<String, String> one = new LinkedHashMap<>(item);
            String status = item.get("status");
            String source = item.get("source");
            boolean forced = force.contains(normalizePathKey(source));
            if ("error".equals(status)) {
                one.put("result", "skipped");
                one.put("resultMsg", "错误项不可复制");
                skipped++;
                results.add(one);
                continue;
            }
            if ("risk".equals(status) && !forced) {
                one.put("result", "skipped");
                one.put("resultMsg", "结构风险，未勾选强制确认");
                skipped++;
                results.add(one);
                continue;
            }
            try {
                copyOne(item, request.getTaskId());
                one.put("result", "success");
                one.put("resultMsg", forced && "risk".equals(status) ? "强制复制成功" : "成功");
                ok++;
            } catch (Exception e) {
                one.put("result", "failed");
                one.put("resultMsg", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                failed++;
                System.out.println("复制失败: " + source + " → " + e.getMessage());
                e.printStackTrace();
            }
            results.add(one);
        }
        Map<String, Object> data = summary(request, results);
        data.put("ok", ok);
        data.put("skipped", skipped);
        data.put("failed", failed);
        data.put("items", results);
        return data;
    }

    private List<Map<String, String>> buildItems(PackageCopyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        Path aRoot = requireDir(request.getSourceRoot(), "源根 A");
        Path bRoot = requireDir(request.getTargetRoot(), "目标根 B");
        List<String> files = request.getFiles() == null ? Collections.emptyList() : request.getFiles();
        if (files.isEmpty()) {
            throw new IllegalArgumentException("请至少提供一个源文件路径");
        }
        boolean bExists = bRoot.toFile().exists();
        boolean bNonEmpty = bExists && hasNonDotChildren(bRoot.toFile());
        String taskId = request.getTaskId() == null ? "" : request.getTaskId().trim();

        List<Map<String, String>> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : files) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String sourceAbs = normalizeAbs(raw.trim());
            if (!seen.add(normalizePathKey(sourceAbs))) {
                continue;
            }
            items.add(analyzeOne(aRoot, bRoot, sourceAbs, bExists, bNonEmpty, taskId));
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("文件列表为空或无效");
        }
        return items;
    }

    private Map<String, String> analyzeOne(Path aRoot, Path bRoot, String sourceAbs,
                                          boolean bExists, boolean bNonEmpty, String taskId) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("source", sourceAbs);
        File sourceFile = new File(sourceAbs);
        if (!sourceFile.isFile()) {
            return mark(item, "", "", "error", "skip", "源文件不存在或不是文件");
        }
        Path sourcePath = sourceFile.toPath().toAbsolutePath().normalize();
        if (!sourcePath.startsWith(aRoot)) {
            return mark(item, "", "", "error", "skip", "源文件不在源根 A 下，不可复制");
        }
        Path rel = aRoot.relativize(sourcePath);
        String relStr = rel.toString().replace('\\', '/');
        if (relStr.isEmpty() || relStr.equals(".")) {
            return mark(item, relStr, "", "error", "skip", "相对路径为空");
        }
        Path targetPath = bRoot.resolve(rel).normalize();
        if (!targetPath.startsWith(bRoot)) {
            return mark(item, relStr, "", "error", "skip", "解析后的目标路径越出目标根 B");
        }
        String targetAbs = targetPath.toAbsolutePath().toString();
        item.put("rel", relStr);
        item.put("target", targetAbs);

        String action = isScriptFile(sourceAbs) && StringUtils.hasText(taskId) ? "append" : "copy";
        StructureJudge judge = judgeStructure(bRoot, rel, bExists, bNonEmpty, targetPath);
        if ("error".equals(judge.status)) {
            return mark(item, relStr, targetAbs, "error", "skip", judge.reason);
        }
        if ("risk".equals(judge.status)) {
            item.put("status", "risk");
            item.put("action", "skip");
            item.put("reason", judge.reason);
            item.put("hint", "勾选强制确认后才会复制");
            return item;
        }
        item.put("status", "ok");
        item.put("action", action);
        String existHint = targetPath.toFile().isFile()
                ? ("append".equals(action) ? "目标已存在，将按 taskId 追加" : "目标已存在，将覆盖")
                : ("append".equals(action) ? "将按 taskId 追加（目标不存在则整文件写入）" : "将新建/覆盖");
        item.put("reason", judge.reason + "；" + existHint);
        return item;
    }

    private StructureJudge judgeStructure(Path bRoot, Path rel, boolean bExists, boolean bNonEmpty, Path targetPath) {
        File targetParent = targetPath.getParent() == null ? null : targetPath.getParent().toFile();
        if (targetParent != null && targetParent.isDirectory()) {
            return StructureJudge.ok("目标父目录已存在");
        }
        List<String> segments = new ArrayList<>();
        for (Path part : rel) {
            String name = part.toString();
            if (!name.isEmpty() && !".".equals(name)) {
                segments.add(name);
            }
        }
        // 最后一段是文件名，目录段 = segments 去掉最后一段
        if (segments.size() <= 1) {
            // 文件直接在 A 根下：B 为空可新建；B 非空仍允许（落在 B 根）
            return StructureJudge.ok("文件位于模块根下");
        }
        List<String> dirSegments = segments.subList(0, segments.size() - 1);
        int matched = 0;
        Path cursor = bRoot;
        for (String seg : dirSegments) {
            cursor = cursor.resolve(seg);
            if (cursor.toFile().exists()) {
                matched++;
            } else {
                break;
            }
        }
        String first = dirSegments.get(0);
        File firstDir = bRoot.resolve(first).toFile();
        if (matched >= Math.min(MIN_MATCHED_SEGMENTS, dirSegments.size())) {
            return StructureJudge.ok("已命中目录前缀 " + matched + " 段，将创建缺失中间目录");
        }
        if (firstDir.isDirectory()) {
            return StructureJudge.ok("第一级目录已存在，将创建缺失中间目录");
        }
        if (bExists && bNonEmpty && !firstDir.exists()) {
            return StructureJudge.risk("目标根 B 非空，但相对路径第一级目录「" + first
                    + "」不存在，架构目录可能对不上（需强制确认）");
        }
        if (!bExists || !bNonEmpty) {
            return StructureJudge.ok("目标根 B 为空或不存在，将按相对路径新建");
        }
        return StructureJudge.risk("目录结构对齐不足（连续命中 " + matched + " 段，阈值 "
                + MIN_MATCHED_SEGMENTS + "），需强制确认");
    }

    private void copyOne(Map<String, String> item, String taskId) throws IOException {
        File source = new File(item.get("source"));
        File target = new File(item.get("target"));
        boolean append = isScriptFile(source.getName()) && StringUtils.hasText(taskId);
        System.out.println((append ? "追加" : "复制") + ": " + source.getAbsolutePath() + " → " + target.getAbsolutePath());
        if (append) {
            FileUtil.replaceSql(target, source, taskId.trim());
        } else {
            FileUtil.replace(target, source);
        }
    }

    private Map<String, Object> summary(PackageCopyRequest request, List<Map<String, String>> items) {
        int okCount = 0;
        int riskCount = 0;
        int errorCount = 0;
        for (Map<String, String> item : items) {
            String status = item.get("status");
            if ("ok".equals(status)) {
                okCount++;
            } else if ("risk".equals(status)) {
                riskCount++;
            } else {
                errorCount++;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceRoot", normalizeAbs(request.getSourceRoot()));
        data.put("targetRoot", normalizeAbs(request.getTargetRoot()));
        data.put("taskId", request.getTaskId() == null ? "" : request.getTaskId().trim());
        data.put("okCount", okCount);
        data.put("riskCount", riskCount);
        data.put("errorCount", errorCount);
        data.put("items", items);
        return data;
    }

    private static Map<String, String> mark(Map<String, String> item, String rel, String target,
                                            String status, String action, String reason) {
        item.put("rel", rel);
        item.put("target", target);
        item.put("status", status);
        item.put("action", action);
        item.put("reason", reason);
        return item;
    }

    private static Path requireDir(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return Paths.get(raw.trim()).toAbsolutePath().normalize();
    }

    private static String normalizeAbs(String path) {
        return Paths.get(path.trim()).toAbsolutePath().normalize().toString();
    }

    private static String normalizePathKey(String path) {
        if (path == null) {
            return "";
        }
        return Paths.get(path.trim()).toAbsolutePath().normalize().toString();
    }

    private static Set<String> normalizeForce(List<String> forcePaths) {
        Set<String> set = new LinkedHashSet<>();
        if (forcePaths == null) {
            return set;
        }
        for (String p : forcePaths) {
            if (StringUtils.hasText(p)) {
                set.add(normalizePathKey(p));
            }
        }
        return set;
    }

    private static boolean hasNonDotChildren(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (!child.getName().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isScriptFile(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".sql") || lower.endsWith(".upd") || lower.endsWith(".vm");
    }

    private static final class StructureJudge {
        private final String status;
        private final String reason;

        private StructureJudge(String status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        static StructureJudge ok(String reason) {
            return new StructureJudge("ok", reason);
        }

        static StructureJudge risk(String reason) {
            return new StructureJudge("risk", reason);
        }
    }
}
