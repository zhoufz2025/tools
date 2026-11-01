package com.zhoufz.tools.db;

import java.util.ArrayList;
import java.util.List;

/**
 * 按分号拆分 SQL 语句（忽略引号内分号；行尾以 ; 结束视为语句边界）。
 *
 * @author zhoufz
 */
public final class SqlStatementSplitter {

    private SqlStatementSplitter() {
    }

    public static List<String> split(String sql) {
        List<String> statements = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) {
            return statements;
        }
        StringBuilder current = new StringBuilder();
        for (String line : sql.split("\n", -1)) {
            current.append(line).append('\n');
            if (isStatementEnd(line)) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    private static boolean isStatementEnd(String line) {
        String trimmed = line.trim();
        return trimmed.endsWith(";");
    }
}
