package com.zhoufz.tools.db;

import java.util.Locale;

/**
 * 处理基线 initdata 中未绑定的 JDBC {@code ?} 占位符。
 * <p>
 * 部分参数脚本按库类型/现场情况手工填值，直接导入 MySQL 会 1064。
 * 本地 MySQL 初始化规则：
 * <ul>
 *   <li>{@code T2H0511_UES_SHAREEXTTT}：基线注释约定 mysql/tidb 为 1，替换为 {@code '1'}</li>
 *   <li>其余含未绑定 {@code ?} 的语句跳过</li>
 *   <li>字符串内的 {@code ?}（cron、中文乱码替换符等）保留</li>
 * </ul>
 */
public final class SqlPlaceholderStatementFilter {

    private static final String MYSQL_SHARE_EXT_PARAM = "T2H0511_UES_SHAREEXTTT";

    private SqlPlaceholderStatementFilter() {
    }

    public static SqlTemplateStatementFilter.FilterResult filter(String sql) {
        StringBuilder output = new StringBuilder();
        StringBuilder statement = new StringBuilder();
        int keptStatements = 0;
        int skippedStatements = 0;
        boolean rewritten = false;

        for (String line : sql.split("\n", -1)) {
            statement.append(line).append('\n');
            if (!isStatementEnd(line)) {
                continue;
            }
            String current = statement.toString();
            statement.setLength(0);
            if (!containsUnboundJdbcPlaceholder(current)) {
                output.append(current);
                keptStatements++;
                continue;
            }
            if (containsIgnoreCase(current, MYSQL_SHARE_EXT_PARAM)) {
                output.append(replaceUnboundPlaceholder(current, "1"));
                keptStatements++;
                rewritten = true;
                continue;
            }
            skippedStatements++;
        }

        if (statement.length() > 0) {
            String current = statement.toString();
            if (!containsUnboundJdbcPlaceholder(current)) {
                output.append(current);
                keptStatements++;
            } else if (containsIgnoreCase(current, MYSQL_SHARE_EXT_PARAM)) {
                output.append(replaceUnboundPlaceholder(current, "1"));
                keptStatements++;
                rewritten = true;
            } else {
                skippedStatements++;
            }
        }

        boolean filtered = rewritten || skippedStatements > 0;
        return new SqlTemplateStatementFilter.FilterResult(output.toString(), keptStatements, skippedStatements,
                filtered);
    }

    public static boolean containsUnboundJdbcPlaceholder(CharSequence text) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (ch == '\'' && !inDoubleQuote) {
                if (inSingleQuote && next == '\'') {
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                if (inDoubleQuote && next == '"') {
                    i++;
                    continue;
                }
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '?' && !inSingleQuote && !inDoubleQuote) {
                return true;
            }
        }
        return false;
    }

    static String replaceUnboundPlaceholder(String statement, String mysqlValue) {
        StringBuilder output = new StringBuilder(statement.length() + 8);
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < statement.length(); i++) {
            char ch = statement.charAt(i);
            char next = i + 1 < statement.length() ? statement.charAt(i + 1) : '\0';
            if (ch == '\'' && !inDoubleQuote) {
                if (inSingleQuote && next == '\'') {
                    output.append(ch).append(next);
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
                output.append(ch);
            } else if (ch == '"' && !inSingleQuote) {
                if (inDoubleQuote && next == '"') {
                    output.append(ch).append(next);
                    i++;
                    continue;
                }
                inDoubleQuote = !inDoubleQuote;
                output.append(ch);
            } else if (ch == '?' && !inSingleQuote && !inDoubleQuote) {
                output.append('\'').append(mysqlValue).append('\'');
            } else {
                output.append(ch);
            }
        }
        return output.toString();
    }

    private static boolean containsIgnoreCase(String text, String token) {
        return text.toUpperCase(Locale.ROOT).contains(token.toUpperCase(Locale.ROOT));
    }

    private static boolean isStatementEnd(String line) {
        return line.trim().endsWith(";");
    }
}
