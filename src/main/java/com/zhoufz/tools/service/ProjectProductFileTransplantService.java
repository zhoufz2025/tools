package com.zhoufz.tools.service;

import com.zhoufz.tools.util.FileUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 按文件列表指定路径进行工程↔产品代码移植。
 * <p>
 * 列表文件放在 classpath（如 {@code projectToProductReplaceFile.txt}），
 * 每行一个<strong>源侧</strong>文件的绝对路径，以 {@code #} 开头的行视为注释。
 * <p>
 * 联机自动做 bootstrap ↔ bootstrap-adapter 映射；
 * 前端自动做 {@code console-{biz}-vue} ↔ {@code console-{biz}-bank-vue} 映射；
 * 低柜自动做 {@code ifmcounter|ifmcounter-*}/ 前缀 ↔ 工程扁平路径映射。
 *
 * @author zhoufz
 * @date 20260705
 */
@Service
public class ProjectProductFileTransplantService {

    /**
     * 工程 → 产品：列表中每行为工程侧文件绝对路径。
     */
    public ProjectProductTransplantService.TransplantResult transplantProjectToProductByFileList(
            ProjectProductTransplantConfig config, String listFileName) throws IOException {
        List<Path> sources = readSourceFileList(listFileName);
        return copyByFileList(config, listFileName, sources, true);
    }

    /**
     * 产品 → 工程：列表中每行为产品侧文件绝对路径。
     */
    public ProjectProductTransplantService.TransplantResult transplantProductToProjectByFileList(
            ProjectProductTransplantConfig config, String listFileName) throws IOException {
        List<Path> sources = readSourceFileList(listFileName);
        return copyByFileList(config, listFileName, sources, false);
    }

    private ProjectProductTransplantService.TransplantResult copyByFileList(
            ProjectProductTransplantConfig config, String listFileName,
            List<Path> sourceFiles, boolean projectToProduct) throws IOException {
        String direction = projectToProduct ? "工程 → 产品（指定文件）" : "产品 → 工程（指定文件）";
        ProjectProductTransplantService.TransplantResult result =
                new ProjectProductTransplantService.TransplantResult();

        System.out.println("========== 代码移植开始: " + direction + " ==========");
        System.out.println("列表文件: classpath:" + listFileName);
        System.out.println("工程银行目录: " + config.getProjectBankRoot().toAbsolutePath());
        System.out.println("银行简称: " + config.getBankCode());
        System.out.println("待处理文件数: " + sourceFiles.size());
        System.out.println();

        if (sourceFiles.isEmpty()) {
            result.addSkipped("[SKIP] 文件列表为空");
            printFileListSummary(direction, result);
            return result;
        }

        for (Path sourceFile : sourceFiles) {
            copySingleFile(config, sourceFile, projectToProduct, result);
        }

        printFileListSummary(direction, result);
        return result;
    }

    private void copySingleFile(ProjectProductTransplantConfig config, Path sourceFile,
                                boolean projectToProduct,
                                ProjectProductTransplantService.TransplantResult result) throws IOException {
        if (!Files.isRegularFile(sourceFile)) {
            String msg = "[SKIP] 源文件不存在或不是文件: " + sourceFile;
            result.addSkipped(msg);
            System.out.println(msg);
            return;
        }
        if (shouldSkipFile(config, sourceFile)) {
            String msg = "[SKIP][排除] " + sourceFile;
            result.addSkipped(msg);
            System.out.println(msg);
            return;
        }

        ModulePair pair = resolveModulePair(config, sourceFile, projectToProduct);
        if (pair == null) {
            String msg = "[SKIP] 无法识别文件所属模块(联机/前端/低柜): " + sourceFile;
            result.addSkipped(msg);
            System.out.println(msg);
            return;
        }

        Path relative = pair.sourceRoot.relativize(sourceFile.toAbsolutePath().normalize());
        Path targetFile = resolveTargetFile(config, pair.scope, pair.targetRoot, relative, projectToProduct);

        boolean contentRewritten = false;
        if (pair.scope == TransplantScope.FRONT) {
            contentRewritten = FrontPathMapper.copyFrontFile(sourceFile, targetFile, projectToProduct);
        } else {
            Files.createDirectories(targetFile.getParent());
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

        TransplantBusiness business = BusinessScopeMatcher.resolveMatchedBusiness(
                sourceFile, pair.scope, config.getEnabledBusinesses());
        String sourceRel = normalizeRelative(relative);
        String targetRel = normalizeRelative(pair.targetRoot.relativize(targetFile));
        boolean pathMapped = !sourceRel.equals(targetRel);

        ProjectProductTransplantService.CopyRecord record = new ProjectProductTransplantService.CopyRecord(
                pair.scope, business, sourceFile, targetFile, sourceRel, targetRel,
                pathMapped || contentRewritten);
        result.addCopyRecord(record);
        result.addCopied(pair.moduleName, sourceFile + " → " + targetFile);

        System.out.println("[" + scopeLabel(pair.scope) + " / " + pair.moduleName + "]");
        System.out.println("  源目录: " + pair.sourceRoot.toAbsolutePath());
        System.out.println("  目标目录: " + pair.targetRoot.toAbsolutePath());
        System.out.println("  [COPY]");
        System.out.println("    源: " + sourceFile.toAbsolutePath());
        System.out.println("    目标: " + targetFile.toAbsolutePath());
        if (pathMapped) {
            System.out.println("    相对路径映射: " + sourceRel + " => " + targetRel);
        } else {
            System.out.println("    相对路径: " + sourceRel);
        }
        if (contentRewritten) {
            System.out.println("    内容改写: 前端别名/chunk 名已按工程↔产品规则转换");
        }
        System.out.println();
    }

    private ModulePair resolveModulePair(ProjectProductTransplantConfig config, Path sourceFile,
                                       boolean projectToProduct) {
        Path normalized = sourceFile.toAbsolutePath().normalize();
        ModulePair[] candidates;
        if (projectToProduct) {
            candidates = new ModulePair[]{
                    buildPair(normalized,
                            config.getProjectModuleRoot(ProjectProductTransplantConfig.MODULE_SERVER),
                            Paths.get(config.getProductBackendRoot()),
                            ProjectProductTransplantConfig.MODULE_SERVER, TransplantScope.ONLINE),
                    buildPair(normalized,
                            config.getProjectModuleRoot(ProjectProductTransplantConfig.MODULE_FRONT),
                            Paths.get(config.getProductFrontRoot()),
                            ProjectProductTransplantConfig.MODULE_FRONT, TransplantScope.FRONT),
                    buildPair(normalized,
                            config.getProjectModuleRoot(ProjectProductTransplantConfig.MODULE_COUNTER),
                            Paths.get(config.getProductCounterRoot()),
                            ProjectProductTransplantConfig.MODULE_COUNTER, TransplantScope.COUNTER)
            };
        } else {
            candidates = new ModulePair[]{
                    buildPair(normalized,
                            Paths.get(config.getProductBackendRoot()),
                            config.getProjectModuleRoot(ProjectProductTransplantConfig.MODULE_SERVER),
                            ProjectProductTransplantConfig.MODULE_SERVER, TransplantScope.ONLINE),
                    buildPair(normalized,
                            Paths.get(config.getProductFrontRoot()),
                            config.getProjectModuleRoot(ProjectProductTransplantConfig.MODULE_FRONT),
                            ProjectProductTransplantConfig.MODULE_FRONT, TransplantScope.FRONT),
                    buildPair(normalized,
                            Paths.get(config.getProductCounterRoot()),
                            config.getProjectModuleRoot(ProjectProductTransplantConfig.MODULE_COUNTER),
                            ProjectProductTransplantConfig.MODULE_COUNTER, TransplantScope.COUNTER)
            };
        }
        for (ModulePair pair : candidates) {
            if (pair != null) {
                return pair;
            }
        }
        return null;
    }

    private ModulePair buildPair(Path file, Path sourceRoot, Path targetRoot,
                                 String moduleName, TransplantScope scope) {
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (file.startsWith(root)) {
            return new ModulePair(scope, moduleName, root, targetRoot.toAbsolutePath().normalize());
        }
        return null;
    }

    private Path resolveTargetFile(ProjectProductTransplantConfig config, TransplantScope scope,
                                   Path targetRoot, Path relativePath, boolean projectToProduct) {
        String relativeNormalized = relativePath.toString().replace('\\', '/');
        if (scope == TransplantScope.ONLINE) {
            String mapped = OnlinePathMapper.mapModuleRelativePath(
                    relativeNormalized, projectToProduct, config.getBankCode());
            if (!projectToProduct) {
                return OnlinePathMapper.resolveTargetPath(targetRoot, mapped);
            }
            return targetRoot.resolve(mapped.replace('/', '\\'));
        }
        if (scope == TransplantScope.FRONT) {
            String mapped = FrontPathMapper.mapModuleRelativePath(relativeNormalized, projectToProduct);
            return targetRoot.resolve(mapped.replace('/', '\\'));
        }
        if (scope == TransplantScope.COUNTER) {
            String mapped = CounterPathMapper.mapModuleRelativePath(relativeNormalized, projectToProduct);
            return CounterPathMapper.resolveTargetPath(targetRoot, mapped, projectToProduct);
        }
        return targetRoot.resolve(relativePath);
    }

    private List<Path> readSourceFileList(String listFileName) {
        List<String> lines = FileUtil.readFile(listFileName);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("文件列表为空或无法读取: classpath:" + listFileName);
        }
        List<Path> paths = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            paths.add(Paths.get(trimmed));
        }
        return paths;
    }

    private boolean shouldSkipFile(ProjectProductTransplantConfig config, Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (config.getSkipFileNames().contains(fileName)) {
            return true;
        }
        for (String ext : config.getSkipExtensions()) {
            if (fileName.endsWith(ext.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void printFileListSummary(String direction,
                                      ProjectProductTransplantService.TransplantResult result) {
        System.out.println("========== " + direction + " 汇总 ==========");
        System.out.println("成功复制: " + result.getCopiedCount() + " 个文件");
        System.out.println("跳过: " + result.getSkippedFiles().size() + " 条");
        System.out.println("========================================");
    }

    private String scopeLabel(TransplantScope scope) {
        switch (scope) {
            case ONLINE:
                return "联机 ONLINE";
            case FRONT:
                return "前端 FRONT";
            case COUNTER:
                return "低柜 COUNTER";
            default:
                return scope.name();
        }
    }

    private String normalizeRelative(Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }

    private static class ModulePair {
        private final TransplantScope scope;
        private final String moduleName;
        private final Path sourceRoot;
        private final Path targetRoot;

        private ModulePair(TransplantScope scope, String moduleName, Path sourceRoot, Path targetRoot) {
            this.scope = scope;
            this.moduleName = moduleName;
            this.sourceRoot = sourceRoot;
            this.targetRoot = targetRoot;
        }
    }
}
