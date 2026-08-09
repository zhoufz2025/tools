package com.zhoufz.tools.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工程代码与产品代码双向移植服务。
 * <p>
 * 支持按联机/前端/低柜精准控制移植范围；前端仅移植个性化 vue 与个性化路由。
 * SQL 脚本不参与移植。
 *
 * @author zhoufz
 * @date 20260705
 */
@Service
public class ProjectProductTransplantService {

    public static class TransplantResult {
        private final List<String> copiedFiles = new ArrayList<>();
        private final List<String> skippedFiles = new ArrayList<>();
        private final Map<String, Integer> moduleCopiedCount = new LinkedHashMap<>();
        private final List<CopyRecord> copyRecords = new ArrayList<>();
        private final List<ModuleSectionLog> moduleSections = new ArrayList<>();

        public List<String> getCopiedFiles() {
            return copiedFiles;
        }

        public List<String> getSkippedFiles() {
            return skippedFiles;
        }

        public Map<String, Integer> getModuleCopiedCount() {
            return moduleCopiedCount;
        }

        public List<CopyRecord> getCopyRecords() {
            return copyRecords;
        }

        public List<ModuleSectionLog> getModuleSections() {
            return moduleSections;
        }

        public int getCopiedCount() {
            return copiedFiles.size();
        }

        void addCopied(String module, String info) {
            copiedFiles.add(info);
            moduleCopiedCount.merge(module, 1, Integer::sum);
        }

        void addSkipped(String info) {
            skippedFiles.add(info);
        }

        void addCopyRecord(CopyRecord record) {
            copyRecords.add(record);
        }

        void addModuleSection(ModuleSectionLog section) {
            moduleSections.add(section);
        }
    }

    /** 单个模块（联机/前端/低柜）移植分区日志 */
    public static class ModuleSectionLog {
        private final TransplantScope scope;
        private final String moduleName;
        private final Path sourceRoot;
        private final Path targetRoot;
        private final int copiedCount;

        public ModuleSectionLog(TransplantScope scope, String moduleName, Path sourceRoot,
                                Path targetRoot, int copiedCount) {
            this.scope = scope;
            this.moduleName = moduleName;
            this.sourceRoot = sourceRoot;
            this.targetRoot = targetRoot;
            this.copiedCount = copiedCount;
        }

        public TransplantScope getScope() {
            return scope;
        }

        public String getModuleName() {
            return moduleName;
        }

        public Path getSourceRoot() {
            return sourceRoot;
        }

        public Path getTargetRoot() {
            return targetRoot;
        }

        public int getCopiedCount() {
            return copiedCount;
        }
    }

    /** 单文件复制明细 */
    public static class CopyRecord {
        private final TransplantScope scope;
        private final TransplantBusiness business;
        private final Path sourceFile;
        private final Path targetFile;
        private final String sourceRelative;
        private final String targetRelative;
        private final boolean pathMapped;

        public CopyRecord(TransplantScope scope, TransplantBusiness business,
                          Path sourceFile, Path targetFile,
                          String sourceRelative, String targetRelative, boolean pathMapped) {
            this.scope = scope;
            this.business = business;
            this.sourceFile = sourceFile;
            this.targetFile = targetFile;
            this.sourceRelative = sourceRelative;
            this.targetRelative = targetRelative;
            this.pathMapped = pathMapped;
        }

        public TransplantScope getScope() {
            return scope;
        }

        public TransplantBusiness getBusiness() {
            return business;
        }

        public Path getSourceFile() {
            return sourceFile;
        }

        public Path getTargetFile() {
            return targetFile;
        }

        public String getSourceRelative() {
            return sourceRelative;
        }

        public String getTargetRelative() {
            return targetRelative;
        }

        public boolean isPathMapped() {
            return pathMapped;
        }
    }

    /**
     * 将指定银行工程代码移植到产品目录。
     */
    public TransplantResult transplantProjectToProduct(ProjectProductTransplantConfig config) throws IOException {
        validateConfig(config);
        printTransplantHeader("工程 → 产品", config);
        TransplantResult result = new TransplantResult();
        transplantModuleIfEnabled(config, ProjectProductTransplantConfig.MODULE_SERVER,
                Paths.get(config.getProductBackendRoot()), result, CopyDirection.PROJECT_TO_PRODUCT);
        transplantModuleIfEnabled(config, ProjectProductTransplantConfig.MODULE_FRONT,
                Paths.get(config.getProductFrontRoot()), result, CopyDirection.PROJECT_TO_PRODUCT);
        transplantModuleIfEnabled(config, ProjectProductTransplantConfig.MODULE_COUNTER,
                Paths.get(config.getProductCounterRoot()), result, CopyDirection.PROJECT_TO_PRODUCT);
        printSummary("工程 → 产品", config, result);
        return result;
    }

    /**
     * 将指定银行产品代码移植回工程目录。
     */
    public TransplantResult transplantProductToProject(ProjectProductTransplantConfig config) throws IOException {
        validateConfig(config);
        printTransplantHeader("产品 → 工程", config);
        TransplantResult result = new TransplantResult();
        transplantModuleIfEnabled(config, ProjectProductTransplantConfig.MODULE_SERVER,
                Paths.get(config.getProductBackendRoot()), result, CopyDirection.PRODUCT_TO_PROJECT);
        transplantModuleIfEnabled(config, ProjectProductTransplantConfig.MODULE_FRONT,
                Paths.get(config.getProductFrontRoot()), result, CopyDirection.PRODUCT_TO_PROJECT);
        transplantModuleIfEnabled(config, ProjectProductTransplantConfig.MODULE_COUNTER,
                Paths.get(config.getProductCounterRoot()), result, CopyDirection.PRODUCT_TO_PROJECT);
        printSummary("产品 → 工程", config, result);
        return result;
    }

    private void printTransplantHeader(String direction, ProjectProductTransplantConfig config) {
        System.out.println("========== 代码移植开始: " + direction + " ==========");
        System.out.println("工程银行目录: " + config.getProjectBankRoot().toAbsolutePath());
        System.out.println("银行简称: " + config.getBankCode());
        System.out.println("移植范围: " + config.getEnabledScopes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", ")));
        System.out.println("同步业务: " + config.getEnabledBusinesses().stream()
                .map(b -> b.getLabel() + "(" + b.getConfigKey() + ")")
                .collect(Collectors.joining(", ")));
        System.out.println("产品联机目录: " + Paths.get(config.getProductBackendRoot()).toAbsolutePath());
        System.out.println("产品前端目录: " + Paths.get(config.getProductFrontRoot()).toAbsolutePath());
        System.out.println("产品低柜目录: " + Paths.get(config.getProductCounterRoot()).toAbsolutePath());
    }

    private enum CopyDirection {
        PROJECT_TO_PRODUCT,
        PRODUCT_TO_PROJECT
    }

    private void transplantModuleIfEnabled(ProjectProductTransplantConfig config, String moduleName,
                                           Path productModuleRoot, TransplantResult result,
                                           CopyDirection direction) throws IOException {
        TransplantScope scope = config.resolveScope(moduleName);
        if (!config.isScopeEnabled(scope)) {
            String msg = "[SKIP][范围] 未启用 " + scope.name() + "，跳过模块 " + moduleName;
            result.addSkipped(msg);
            System.out.println(msg);
            return;
        }
        Path projectModuleRoot = config.getProjectModuleRoot(moduleName);
        copyModule(config, moduleName, scope, projectModuleRoot, productModuleRoot, result, direction);
    }

    private void copyModule(ProjectProductTransplantConfig config, String moduleName, TransplantScope scope,
                            Path projectModuleRoot, Path productModuleRoot,
                            TransplantResult result, CopyDirection direction) throws IOException {
        if (!Files.isDirectory(projectModuleRoot) && direction == CopyDirection.PROJECT_TO_PRODUCT) {
            String msg = "[SKIP][" + moduleName + "] 工程模块目录不存在: " + projectModuleRoot;
            result.addSkipped(msg);
            System.out.println(msg);
            return;
        }
        if (!Files.isDirectory(productModuleRoot) && direction == CopyDirection.PRODUCT_TO_PROJECT) {
            String msg = "[SKIP][" + moduleName + "] 产品模块目录不存在: " + productModuleRoot;
            result.addSkipped(msg);
            System.out.println(msg);
            return;
        }

        Path sourceRoot = direction == CopyDirection.PROJECT_TO_PRODUCT ? projectModuleRoot : productModuleRoot;
        Path targetRoot = direction == CopyDirection.PROJECT_TO_PRODUCT ? productModuleRoot : projectModuleRoot;

        if (!Files.isDirectory(sourceRoot)) {
            String msg = "[SKIP][" + moduleName + "] 源目录不存在: " + sourceRoot;
            result.addSkipped(msg);
            System.out.println(msg);
            return;
        }

        List<CopyRecord> moduleCopies = new ArrayList<>();

        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (shouldSkipDirectory(config, sourceRoot, dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (shouldSkipFile(config, file)) {
                    result.addSkipped("[SKIP][排除] " + file);
                    return FileVisitResult.CONTINUE;
                }
                if (!shouldIncludeFile(config, scope, file)) {
                    return FileVisitResult.CONTINUE;
                }

                Path relativePath = sourceRoot.relativize(file);
                Path targetFile = resolveTargetFile(config, scope, targetRoot, relativePath, direction);
                boolean contentRewritten = false;
                if (scope == TransplantScope.FRONT) {
                    contentRewritten = FrontPathMapper.copyFrontFile(
                            file, targetFile, direction == CopyDirection.PROJECT_TO_PRODUCT);
                } else {
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }

                String sourceRel = normalizeRelative(relativePath);
                String targetRel = normalizeRelative(targetRoot.relativize(targetFile));
                boolean pathMapped = !sourceRel.equals(targetRel);
                TransplantBusiness business = BusinessScopeMatcher.resolveMatchedBusiness(
                        file, scope, config.getEnabledBusinesses());

                CopyRecord record = new CopyRecord(scope, business, file, targetFile,
                        sourceRel, targetRel, pathMapped || contentRewritten);
                moduleCopies.add(record);
                result.addCopyRecord(record);
                result.addCopied(moduleName, file + " → " + targetFile);
                return FileVisitResult.CONTINUE;
            }
        });

        printModuleSectionLog(scope, moduleName, sourceRoot, targetRoot, moduleCopies, result);
    }

    private void printModuleSectionLog(TransplantScope scope, String moduleName,
                                       Path sourceRoot, Path targetRoot,
                                       List<CopyRecord> copies, TransplantResult result) {
        result.addModuleSection(new ModuleSectionLog(scope, moduleName, sourceRoot, targetRoot, copies.size()));

        System.out.println();
        System.out.println("---------- [" + scopeLabel(scope) + "] " + moduleName + " ----------");
        System.out.println("  源目录: " + sourceRoot.toAbsolutePath());
        System.out.println("  目标目录: " + targetRoot.toAbsolutePath());
        System.out.println("  复制文件数: " + copies.size());

        if (copies.isEmpty()) {
            System.out.println("  （本模块无匹配文件，未复制）");
            return;
        }

        Map<TransplantBusiness, List<CopyRecord>> grouped = new LinkedHashMap<>();
        for (CopyRecord copy : copies) {
            TransplantBusiness key = copy.getBusiness() != null ? copy.getBusiness() : TransplantBusiness.PUB;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(copy);
        }

        for (Map.Entry<TransplantBusiness, List<CopyRecord>> entry : grouped.entrySet()) {
            TransplantBusiness business = entry.getKey();
            List<CopyRecord> businessCopies = entry.getValue();
            System.out.println();
            System.out.println("  --- 业务: " + business.getLabel() + "(" + business.getConfigKey()
                    + ") [" + businessCopies.size() + " 个文件] ---");
            for (CopyRecord copy : businessCopies) {
                System.out.println("  [COPY]");
                System.out.println("    源: " + copy.getSourceFile().toAbsolutePath());
                System.out.println("    目标: " + copy.getTargetFile().toAbsolutePath());
                if (copy.isPathMapped()) {
                    System.out.println("    相对路径映射: " + copy.getSourceRelative()
                            + " => " + copy.getTargetRelative());
                } else {
                    System.out.println("    相对路径: " + copy.getSourceRelative());
                }
            }
        }
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

    private Path resolveTargetFile(ProjectProductTransplantConfig config, TransplantScope scope,
                                 Path targetRoot, Path relativePath, CopyDirection direction) {
        String relativeNormalized = relativePath.toString().replace('\\', '/');
        boolean toProduct = direction == CopyDirection.PROJECT_TO_PRODUCT;
        if (scope == TransplantScope.ONLINE) {
            String mapped = OnlinePathMapper.mapModuleRelativePath(
                    relativeNormalized, toProduct, config.getBankCode());
            if (direction == CopyDirection.PRODUCT_TO_PROJECT) {
                return OnlinePathMapper.resolveTargetPath(targetRoot, mapped);
            }
            return targetRoot.resolve(mapped.replace('/', '\\'));
        }
        if (scope == TransplantScope.FRONT) {
            String mapped = FrontPathMapper.mapModuleRelativePath(relativeNormalized, toProduct);
            return targetRoot.resolve(mapped.replace('/', '\\'));
        }
        if (scope == TransplantScope.COUNTER) {
            String mapped = CounterPathMapper.mapModuleRelativePath(relativeNormalized, toProduct);
            return CounterPathMapper.resolveTargetPath(targetRoot, mapped, toProduct);
        }
        return targetRoot.resolve(relativePath);
    }

    /**
     * 按模块类型判断是否纳入移植。
     * 所有模块均要求路径含银行简称；联机额外做 bootstrap 路径映射；前端仅 vue/路由。
     */
    boolean shouldIncludeFile(ProjectProductTransplantConfig config, TransplantScope scope, Path file) {
        if (!BusinessScopeMatcher.matches(file, scope, config.getEnabledBusinesses())) {
            return false;
        }
        String bankCode = config.getBankCode();
        if (!OnlinePathMapper.containsBankCode(file, bankCode)) {
            return false;
        }
        if (scope == TransplantScope.FRONT) {
            return isFrontBankPersonalizedFile(file, bankCode);
        }
        if (scope == TransplantScope.ONLINE) {
            return OnlinePathMapper.isOnlineBankPersonalizedPath(file, bankCode);
        }
        return isCounterBankPersonalizedPath(file, bankCode);
    }

    /**
     * 前端个性化文件：views/bank/{bankCode} 下 .vue，以及 router/bank/{bankCode}、reConfirm 下银行路由 .js。
     */
    boolean isFrontBankPersonalizedFile(Path file, String bankCode) {
        if (isBlank(bankCode)) {
            return false;
        }
        String normalized = file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String bank = bankCode.toLowerCase(Locale.ROOT);
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);

        if (fileName.endsWith(".vue")
                && normalized.contains("/views/bank/" + bank + "/")) {
            return true;
        }
        if (fileName.endsWith(".js")
                && normalized.contains("/router/bank/" + bank + "/")) {
            return true;
        }
        return fileName.endsWith(".js")
                && normalized.contains("/reconfirm/")
                && normalized.contains("/bank/" + bank + "/");
    }

    /**
     * 低柜银行个性化路径（路径须含银行简称）。
     */
    boolean isCounterBankPersonalizedPath(Path file, String bankCode) {
        if (isBlank(bankCode)) {
            return false;
        }
        String normalized = file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String bank = bankCode.toLowerCase(Locale.ROOT);
        return normalized.contains("/bank/" + bank + "/")
                || normalized.contains("/bank/" + bank + ".")
                || normalized.endsWith("/bank/" + bank);
    }

    private void validateConfig(ProjectProductTransplantConfig config) {
        if (isBlank(config.getProjectRoot())) {
            throw new IllegalArgumentException("projectRoot 不能为空");
        }
        if (isBlank(config.getProjectBankDirName())) {
            throw new IllegalArgumentException("projectBankDirName 不能为空");
        }
        if (isBlank(config.getBankCode())) {
            throw new IllegalArgumentException("bankCode 不能为空");
        }
        if (isBlank(config.getProductBackendRoot())) {
            throw new IllegalArgumentException("productBackendRoot 不能为空");
        }
        if (isBlank(config.getProductFrontRoot())) {
            throw new IllegalArgumentException("productFrontRoot 不能为空");
        }
        if (isBlank(config.getProductCounterRoot())) {
            throw new IllegalArgumentException("productCounterRoot 不能为空");
        }
        if (config.getEnabledScopes().isEmpty()) {
            throw new IllegalArgumentException("enabledScopes 至少选择一个移植范围");
        }
        if (config.getEnabledBusinesses().isEmpty()) {
            throw new IllegalArgumentException("enabledBusinesses 至少选择一个业务");
        }
        if (!Files.isDirectory(config.getProjectBankRoot())) {
            throw new IllegalArgumentException("工程银行目录不存在: " + config.getProjectBankRoot());
        }
    }

    private boolean shouldSkipDirectory(ProjectProductTransplantConfig config, Path moduleRoot, Path dir) {
        if (dir.equals(moduleRoot)) {
            return false;
        }
        Path relative = moduleRoot.relativize(dir);
        for (Path part : relative) {
            if (config.getSkipDirNames().contains(part.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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

    private void printSummary(String title, ProjectProductTransplantConfig config, TransplantResult result) {
        System.out.println();
        System.out.println("========== " + title + " 汇总 ==========");
        System.out.println("工程银行目录: " + config.getProjectBankRoot().toAbsolutePath());
        System.out.println("银行简称: " + config.getBankCode());
        System.out.println("移植范围: " + config.getEnabledScopes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", ")));
        System.out.println("同步业务: " + config.getEnabledBusinesses().stream()
                .map(b -> b.getLabel() + "(" + b.getConfigKey() + ")")
                .collect(Collectors.joining(", ")));
        System.out.println();
        System.out.println("各模块源/目标目录对照:");
        for (ModuleSectionLog section : result.getModuleSections()) {
            System.out.println("  [" + scopeLabel(section.getScope()) + " / " + section.getModuleName() + "]");
            System.out.println("    源: " + section.getSourceRoot().toAbsolutePath());
            System.out.println("    目标: " + section.getTargetRoot().toAbsolutePath());
            System.out.println("    复制: " + section.getCopiedCount() + " 个文件");
        }
        if (result.getModuleSections().isEmpty()) {
            System.out.println("  （无已执行模块，可能全部被范围开关跳过）");
        }
        System.out.println();
        System.out.println("合计复制: " + result.getCopiedCount() + " 个文件");
        for (Map.Entry<String, Integer> entry : result.getModuleCopiedCount().entrySet()) {
            System.out.println("  - " + entry.getKey() + ": " + entry.getValue());
        }
        System.out.println("跳过提示: " + result.getSkippedFiles().size() + " 条");
        System.out.println("========================================");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
