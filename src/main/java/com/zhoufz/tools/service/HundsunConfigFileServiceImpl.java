package com.zhoufz.tools.service;

import com.zhoufz.tools.util.FileUtil;
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
import java.util.Arrays;
import java.util.List;

/**
 * 配置文件备份和还原服务
 * @author zhoufz
 * 日期 2025/11/12
 */
@Service
public class HundsunConfigFileServiceImpl {

    // 要备份和还原的文件名列表
    private static final List<String> TARGET_FILE_NAMES = Arrays.asList(
            "pom.xml",
            "log4j2.xml",
            "application.properties",
            "ServerStarter.java"
    );

    /** classpath 中待备份/还原的绝对路径列表文件 */
    private static final String BACKUP_LIST_FILE = "backUpFile.txt";

    /**
     * 从 sourceRoot 复制指定的配置文件到 targetRoot，保持相对路径结构
     * @param sourceRoot 源目录
     * @param targetRoot 目标目录
     * @return 备份的文件列表
     */
    public List<String> backupFiles(String sourceRoot, String targetRoot) throws IOException {
        List<String> backedUpFiles = new ArrayList<>();
        Path sourcePath = Paths.get(sourceRoot);
        Path targetPath = Paths.get(targetRoot);

        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 跳过 Maven/编译产物目录
                if (isTargetDir(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (shouldBackup(file, sourcePath)) {
                    Path relativePath = sourcePath.relativize(file);
                    Path targetFile = targetPath.resolve(relativePath);

                    Files.createDirectories(targetFile.getParent());
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    String backupInfo = file + " → " + targetFile;
                    System.out.println("[BACKUP] " + backupInfo);
                    backedUpFiles.add(backupInfo);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        return backedUpFiles;
    }

    /**
     * 将 targetRoot 下的配置文件（log4j2.xml、application.properties、ServerStarter.java 等）
     * 按原相对路径替换回 sourceRoot；还原时排除 pom.xml（备份仍可包含 pom）
     * @param sourceRoot 目标目录（要被替换的目录）
     * @param targetRoot 源目录（备份目录）
     * @return 还原的文件列表
     */
    public List<String> restoreFiles(String sourceRoot, String targetRoot) throws IOException {
        List<String> restoredFiles = new ArrayList<>();
        Path sourcePath = Paths.get(sourceRoot);
        Path targetPath = Paths.get(targetRoot);

        Files.walkFileTree(targetPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (isTargetDir(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // 还原时排除 pom，避免覆盖工程依赖与触发 IDEA Maven 自动导入
                if (!"pom.xml".equalsIgnoreCase(file.getFileName().toString())) {
                    System.out.println("[RESTORE][SKIP] " + file);
                    return FileVisitResult.CONTINUE;
                }
                if (shouldBackup(file, targetPath)) {
                    Path relativePath = targetPath.relativize(file);
                    Path sourceFile = sourcePath.resolve(relativePath);

                    Files.createDirectories(sourceFile.getParent());
                    Files.copy(file, sourceFile, StandardCopyOption.REPLACE_EXISTING);
                    String restoreInfo = file + " → " + sourceFile;
                    System.out.println("[RESTORE] " + restoreInfo);
                    restoredFiles.add(restoreInfo);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        return restoredFiles;
    }

    /**
     * 按 backUpFile.txt 中的绝对路径列表，将文件备份到 backupRoot（保留盘符后的相对目录结构）
     *
     * @param backupRoot 备份根目录
     * @return 已备份文件信息列表
     */
    public List<String> backupSpecifiedFiles(String backupRoot) throws IOException {
        List<String> filePaths = FileUtil.readFile(BACKUP_LIST_FILE);
        if (filePaths == null || filePaths.isEmpty()) {
            throw new IOException("备份列表为空或无法读取: " + BACKUP_LIST_FILE);
        }
        Path backupRootPath = Paths.get(backupRoot);
        List<String> backedUpFiles = new ArrayList<>();
        for (String filePath : filePaths) {
            Path sourceFile = Paths.get(filePath.trim());
            if (!Files.isRegularFile(sourceFile)) {
                System.out.println("[BACKUP][SKIP] 源文件不存在: " + sourceFile);
                continue;
            }
            Path targetFile = backupRootPath.resolve(toBackupRelativePath(sourceFile));
            Files.createDirectories(targetFile.getParent());
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            String backupInfo = sourceFile + " → " + targetFile;
            System.out.println("[BACKUP] " + backupInfo);
            backedUpFiles.add(backupInfo);
        }
        return backedUpFiles;
    }

    /**
     * 按 backUpFile.txt 中的绝对路径列表，从 backupRoot 还原文件到原路径
     *
     * @param backupRoot 备份根目录
     * @return 已还原文件信息列表
     */
    public List<String> restoreSpecifiedFiles(String backupRoot) throws IOException {
        List<String> filePaths = FileUtil.readFile(BACKUP_LIST_FILE);
        if (filePaths == null || filePaths.isEmpty()) {
            throw new IOException("备份列表为空或无法读取: " + BACKUP_LIST_FILE);
        }
        Path backupRootPath = Paths.get(backupRoot);
        List<String> restoredFiles = new ArrayList<>();
        for (String filePath : filePaths) {
            Path targetFile = Paths.get(filePath.trim());
            Path backupFile = backupRootPath.resolve(toBackupRelativePath(targetFile));
            if (!Files.isRegularFile(backupFile)) {
                System.out.println("[RESTORE][SKIP] 备份文件不存在: " + backupFile);
                continue;
            }
            Files.createDirectories(targetFile.getParent());
            Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            String restoreInfo = backupFile + " → " + targetFile;
            System.out.println("[RESTORE] " + restoreInfo);
            restoredFiles.add(restoreInfo);
        }
        return restoredFiles;
    }

    /**
     * 将绝对路径转为备份目录下的相对路径（去掉盘符，如 F:\a\b → a\b）
     */
    private static Path toBackupRelativePath(Path absolutePath) {
        Path normalized = absolutePath.normalize();
        Path root = normalized.getRoot();
        if (root != null) {
            return root.relativize(normalized);
        }
        return normalized;
    }

    /**
     * 判断是否为编译产物目录（Maven/Gradle 的 target）
     */
    private static boolean isTargetDir(Path dir) {
        Path name = dir.getFileName();
        return name != null && "target".equalsIgnoreCase(name.toString());
    }

    /**
     * 路径中是否包含 target 目录段（编译产物路径应排除）
     */
    private static boolean containsTargetDir(Path file, Path root) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if ("target".equalsIgnoreCase(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为 Spring 配置文件：application.properties / application.yml，
     * 以及 application-xx.properties、application-dev.yml 等 profile 配置
     */
    private static boolean isApplicationConfig(String fileName) {
        String lower = fileName.toLowerCase();
        if (!(lower.endsWith(".properties") || lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
            return false;
        }
        // application.* 或 application-xx.*
        return lower.startsWith("application.") || lower.startsWith("application-");
    }

    /**
     * 判断文件是否在备份名单中（排除 target 目录下的文件）
     */
    private static boolean shouldBackup(Path file, Path root) {
        if (containsTargetDir(file, root)) {
            return false;
        }
        String fileName = file.getFileName().toString();
        for (String target : TARGET_FILE_NAMES) {
            if (fileName.equalsIgnoreCase(target)) {
                return true;
            }
            // application 配置：兼容 application.* 与 application-xx.*
            if (target.toLowerCase().startsWith("application") && isApplicationConfig(fileName)) {
                return true;
            }
        }
        return false;
    }
}
