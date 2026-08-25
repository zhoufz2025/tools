package com.zhoufz.tools.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 过滤 SQL 脚本中含 TA 模板占位符（如 ${TACODE}）的语句。
 * 仅匹配 ${TACODE}，不误伤 initdata 中字符串里的 ${DiffFieldName} 等展示模板。
 *
 * @author zhoufz
 */
public class SqlTemplateStatementFilter {

    private static final Pattern TACODE_TEMPLATE_PATTERN = Pattern.compile("\\$\\{TACODE}");

    /**
     * 判断文件是否包含 ${TACODE} 模板占位符
     */
    public static boolean containsTaTemplateMarker(Path sqlFile) throws IOException {
        try (java.io.BufferedReader reader = Files.newBufferedReader(sqlFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (containsTaTemplateMarker(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean containsTaTemplateMarker(String sql) {
        return TACODE_TEMPLATE_PATTERN.matcher(sql).find();
    }

    /**
     * 过滤含 ${TACODE} 的语句
     */
    public static FilterResult filter(String sql) {
        StringBuilder output = new StringBuilder();
        StringBuilder statement = new StringBuilder();
        int keptStatements = 0;
        int skippedStatements = 0;

        for (String line : sql.split("\n", -1)) {
            statement.append(line).append('\n');
            if (isStatementEnd(line)) {
                if (containsTaTemplateMarker(statement)) {
                    skippedStatements++;
                } else {
                    output.append(statement);
                    keptStatements++;
                }
                statement.setLength(0);
            }
        }

        if (statement.length() > 0) {
            if (containsTaTemplateMarker(statement)) {
                skippedStatements++;
            } else {
                output.append(statement);
                keptStatements++;
            }
        }

        return new FilterResult(output.toString(), keptStatements, skippedStatements);
    }

    /**
     * 过滤含 ${TACODE} 的语句；含 DELIMITER 的脚本原样返回（由调用方保证不含模板）
     */
    public static FilterResult filter(Path sqlFile) throws IOException {
        List<String> lines = Files.readAllLines(sqlFile, StandardCharsets.UTF_8);
        if (containsDelimiter(lines)) {
            return FilterResult.unfiltered(String.join("\n", lines) + "\n");
        }
        return filter(String.join("\n", lines) + "\n");
    }

    private static boolean containsDelimiter(List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim().toUpperCase(Locale.ROOT);
            if (trimmed.startsWith("DELIMITER ")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStatementEnd(String line) {
        String trimmed = line.trim();
        return trimmed.endsWith(";");
    }

    private static boolean containsTaTemplateMarker(CharSequence text) {
        return TACODE_TEMPLATE_PATTERN.matcher(text).find();
    }

    /**
     * 过滤结果
     */
    public static class FilterResult {

        private final String sql;

        private final int keptStatements;

        private final int skippedStatements;

        private final boolean filtered;

        private FilterResult(String sql, int keptStatements, int skippedStatements, boolean filtered) {
            this.sql = sql;
            this.keptStatements = keptStatements;
            this.skippedStatements = skippedStatements;
            this.filtered = filtered;
        }

        public static FilterResult unfiltered(String sql) {
            return new FilterResult(sql, -1, 0, false);
        }

        public FilterResult(String sql, int keptStatements, int skippedStatements) {
            this(sql, keptStatements, skippedStatements, true);
        }

        public String getSql() {
            return sql;
        }

        public int getKeptStatements() {
            return keptStatements;
        }

        public int getSkippedStatements() {
            return skippedStatements;
        }

        public boolean isFiltered() {
            return filtered;
        }

        public boolean hasExecutableSql() {
            return sql != null && !sql.trim().isEmpty();
        }
    }
}
