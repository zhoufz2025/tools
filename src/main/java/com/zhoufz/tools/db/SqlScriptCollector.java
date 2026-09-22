package com.zhoufz.tools.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 按 IFMS 基线约定收集 mysql 脚本执行顺序
 *
 * @author zhoufz
 */
public class SqlScriptCollector {

    private static final String MYSQL_SCHEMA_DIR = "base/000schema/mysql";

    private static final String TACLEAR_MARKER = "taclear";

    private static final List<String> WORKFLOW_MYSQL_ORDER = java.util.Arrays.asList(
            "workflow.activiti.mysql.table.sql",
            "workflow.idx_mysql.sql",
            "workflow_initdata.mysql.sql"
    );

    /**
     * 收集某 SQL 模块目录下指定阶段的脚本列表
     */
    public List<Path> collect(Path moduleRoot, SqlScriptPhase phase) throws IOException {
        return collectWithSkipped(moduleRoot, phase).getScripts();
    }

    /**
     * 收集脚本并返回跳过的 taclear 文件说明
     */
    public SqlScriptCollectResult collectWithSkipped(Path moduleRoot, SqlScriptPhase phase) throws IOException {
        if (!Files.isDirectory(moduleRoot)) {
            throw new IOException("SQL 模块目录不存在: " + moduleRoot);
        }

        if (phase == SqlScriptPhase.SCHEMA) {
            return collectSchema(moduleRoot);
        }
        return collectInitData(moduleRoot);
    }

    private SqlScriptCollectResult collectSchema(Path moduleRoot) throws IOException {
        Path schemaDir = moduleRoot.resolve(MYSQL_SCHEMA_DIR);
        if (!Files.isDirectory(schemaDir)) {
            return SqlScriptCollectResult.empty();
        }

        List<String> skipped = SqlScriptCollectResult.newSkippedList();
        List<Path> scripts;
        try (Stream<Path> stream = Files.list(schemaDir)) {
            scripts = stream
                    .filter(this::isSqlFile)
                    .filter(path -> {
                        if (isTaclearFile(path)) {
                            skipped.add("跳过 taclear 脚本: " + path);
                            return false;
                        }
                        return true;
                    })
                    .sorted(schemaFileComparator())
                    .collect(Collectors.toList());
        }
        return SqlScriptCollectResult.of(scripts, skipped);
    }

    private SqlScriptCollectResult collectInitData(Path moduleRoot) throws IOException {
        List<Path> result = new java.util.ArrayList<>();
        List<String> skipped = SqlScriptCollectResult.newSkippedList();

        Path baseInit = moduleRoot.resolve("base/initdata");
        Path workflowRoot = baseInit.resolve("workflow");
        if (Files.isDirectory(baseInit)) {
            Path workflowMysql = workflowRoot.resolve("mysql");
            if (Files.isDirectory(workflowMysql)) {
                for (String fileName : WORKFLOW_MYSQL_ORDER) {
                    Path file = workflowMysql.resolve(fileName);
                    if (Files.isRegularFile(file)) {
                        result.add(file);
                    }
                }
            }
            // 排除整个 workflow 目录（仅保留上方 mysql 子目录 3 个脚本，跳过 ora/ob/pg）
            collectSqlFilesRecursive(baseInit, workflowRoot, result, skipped);
        }

        appendInitDir(result, skipped, moduleRoot.resolve("center/initdata"));
        appendInitDir(result, skipped, moduleRoot.resolve("ifmcounter/initdata"));
        appendInitDir(result, skipped, moduleRoot.resolve("monitor/initdata"));

        return SqlScriptCollectResult.of(result, skipped);
    }

    private void appendInitDir(List<Path> result, List<String> skipped, Path initDir) throws IOException {
        if (!Files.isDirectory(initDir)) {
            return;
        }
        collectSqlFilesRecursive(initDir, null, result, skipped);
    }

    private void collectSqlFilesRecursive(Path root, Path excludeSubtree, List<Path> result,
                                          List<String> skipped) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isSqlFile)
                    .filter(path -> excludeSubtree == null || !path.startsWith(excludeSubtree))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> {
                        if (isNonMysqlWorkflowFile(path)) {
                            skipped.add("跳过非 MySQL 工作流脚本: " + path);
                            return;
                        }
                        if (isNonMysqlVendorFile(path)) {
                            skipped.add("跳过非 MySQL 脚本: " + path);
                            return;
                        }
                        if (isConfirmLaterFile(path)) {
                            skipped.add("跳过需现场确认脚本: " + path);
                            return;
                        }
                        result.add(path);
                    });
        }
    }

    private boolean isNonMysqlWorkflowFile(Path path) {
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/initdata/workflow/")
                && !normalized.contains("/initdata/workflow/mysql/");
    }

    private boolean isNonMysqlVendorFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains(".mysql.") || name.endsWith(".mysql.sql") || name.contains("_mysql.")) {
            return false;
        }
        return name.endsWith(".ora.sql")
                || name.endsWith(".oracle.sql")
                || name.endsWith("_oracle.sql")
                || name.endsWith(".pg.sql")
                || name.endsWith("_pg.sql")
                || name.endsWith(".ob.sql")
                || name.endsWith("_ob.sql")
                || name.contains(".ora.")
                || name.contains(".oracle.")
                || name.contains(".pg.")
                || name.contains(".ob.");
    }

    private boolean isConfirmLaterFile(Path path) {
        return path.getFileName().toString().contains("需要确认");
    }

    private boolean isSqlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".sql");
    }

    private boolean isTaclearFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).contains(TACLEAR_MARKER);
    }

    /**
     * 保证同一模块内：先执行所有建表脚本（*.table.sql），再执行所有建索引脚本（*.index.sql）
     */
    static Comparator<Path> schemaFileComparator() {
        return Comparator.<Path>comparingInt(path -> SchemaScriptType.fromFileName(path).getOrder())
                .thenComparing(path -> path.getFileName().toString());
    }
}
