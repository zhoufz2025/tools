package com.zhoufz.tools.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 通过 mysql 命令行客户端执行 SQL（适合大体积 DDL）
 *
 * @author zhoufz
 */
public class MysqlCliExecutor {

    private final LocalDbInitProperties properties;

    public MysqlCliExecutor(LocalDbInitProperties properties) {
        this.properties = properties;
    }

    public void validateMysqlBin() throws IOException {
        Path mysqlBin = Paths.get(properties.getMysqlBin());
        if (!Files.isExecutable(mysqlBin)) {
            throw new IOException("mysql 客户端不可执行，请检查 local.db.mysql-bin: " + mysqlBin);
        }
    }

    public void executeStatement(String sql) throws IOException, InterruptedException {
        List<String> command = buildBaseCommand(null);
        command.add("-e");
        command.add(sql);

        runCommand(command, null);
    }

    /**
     * 执行 SQL 脚本
     *
     * @param filterTaTemplate 是否过滤含 ${TACODE} 的语句（initSchema 为 true，initData 为 false）
     */
    public ScriptExecutionResult executeScript(String database, Path sqlFile, boolean filterTaTemplate)
            throws IOException, InterruptedException {
        Path executeFile = sqlFile;
        Path tempFile = null;
        int skippedStatements = 0;
        boolean filtered = false;

        try {
            if (SqlScriptSanitizer.containsDelimiter(sqlFile)) {
                // 存储过程等含 DELIMITER 的脚本原样执行
            } else {
                String sql = SqlScriptSanitizer.readAndStripComments(sqlFile);
                SqlTemplateStatementFilter.FilterResult placeholderResult =
                        SqlPlaceholderStatementFilter.filter(sql);
                if (placeholderResult.isFiltered()) {
                    skippedStatements += placeholderResult.getSkippedStatements();
                    filtered = true;
                    if (!placeholderResult.hasExecutableSql()) {
                        return ScriptExecutionResult.skippedFile(skippedStatements);
                    }
                    sql = placeholderResult.getSql();
                }
                if (filterTaTemplate && SqlTemplateStatementFilter.containsTaTemplateMarker(sql)) {
                    SqlTemplateStatementFilter.FilterResult filterResult =
                            SqlTemplateStatementFilter.filter(sql);
                    skippedStatements += filterResult.getSkippedStatements();
                    filtered = filterResult.isFiltered() || filtered;
                    if (!filterResult.hasExecutableSql()) {
                        return ScriptExecutionResult.skippedFile(skippedStatements);
                    }
                    sql = filterResult.getSql();
                }
                if (!filterTaTemplate) {
                    sql = SqlScriptSanitizer.rewriteInsertToIgnore(sql);
                }
                tempFile = Files.createTempFile("lcpt-sql-prepared-", ".sql");
                Files.write(tempFile, sql.getBytes(StandardCharsets.UTF_8));
                executeFile = tempFile;
            }

            List<String> command = buildBaseCommand(database);
            runCommand(command, executeFile);
            return ScriptExecutionResult.executed(filtered, skippedStatements);
        } finally {
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private List<String> buildBaseCommand(String database) {
        List<String> command = new ArrayList<>();
        command.add(properties.getMysqlBin());
        command.add("-h" + properties.getHost());
        command.add("-P" + String.valueOf(properties.getPort()));
        command.add("-u" + properties.getUsername());
        if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
            command.add("-p" + properties.getPassword());
        }
        command.add("--default-character-set=utf8mb4");
        if (database != null && !database.isEmpty()) {
            command.add(database);
        }
        return command;
    }

    private void runCommand(List<String> command, Path inputFile) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        if (inputFile != null) {
            builder.redirectInput(inputFile.toFile());
        }

        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        boolean finished = process.waitFor(6, TimeUnit.HOURS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("mysql 执行超时: " + commandLine(command, inputFile));
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("mysql 执行失败(exit=" + exitCode + "): "
                    + commandLine(command, inputFile) + "\n" + output);
        }
    }

    private String commandLine(List<String> command, Path inputFile) {
        StringBuilder sb = new StringBuilder();
        for (String part : command) {
            if (part.startsWith("-p") && part.length() > 2) {
                sb.append("-p*** ");
            } else {
                sb.append(part).append(' ');
            }
        }
        if (inputFile != null) {
            sb.append("< ").append(inputFile);
        }
        return sb.toString().trim();
    }

    /**
     * 脚本执行结果
     */
    public static class ScriptExecutionResult {

        private final boolean executed;

        private final boolean filtered;

        private final int skippedStatements;

        private ScriptExecutionResult(boolean executed, boolean filtered, int skippedStatements) {
            this.executed = executed;
            this.filtered = filtered;
            this.skippedStatements = skippedStatements;
        }

        public static ScriptExecutionResult executed(boolean filtered, int skippedStatements) {
            return new ScriptExecutionResult(true, filtered, skippedStatements);
        }

        public static ScriptExecutionResult skippedFile(int skippedStatements) {
            return new ScriptExecutionResult(false, true, skippedStatements);
        }

        public boolean isExecuted() {
            return executed;
        }

        public boolean isFiltered() {
            return filtered;
        }

        public int getSkippedStatements() {
            return skippedStatements;
        }
    }
}
