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
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if (shouldBackup(fileName)) {
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
     * 将 targetRoot 下的配置文件（pom.xml、log4j2.xml、application.properties、ServerStarter.java）
     * 按原相对路径替换回 sourceRoot
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
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if (shouldBackup(fileName)) {
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
     * 判断文件是否在备份名单中
     */
    private static boolean shouldBackup(String fileName) {
        for (String target : TARGET_FILE_NAMES) {
            if (fileName.equalsIgnoreCase(target)) {
                return true;
            }
            // 对于 application.* 也允许匹配，比如 application.yml
            if (target.startsWith("application.") && fileName.startsWith("application.")) {
                return true;
            }
        }
        return false;
    }
}
