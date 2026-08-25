package com.zhoufz.tools.db;

import java.nio.file.Path;
import java.util.Locale;

/**
 * DDL 脚本类型，用于保证执行顺序：先建表、后建索引
 *
 * @author zhoufz
 */
public enum SchemaScriptType {

    /** 建表脚本 *.table.sql */
    TABLE(0),

    /** 建索引脚本 *.index.sql */
    INDEX(1),

    /** 存储过程等其它脚本 */
    OTHER(2);

    private final int order;

    SchemaScriptType(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }

    public static SchemaScriptType fromFileName(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains(".table.")) {
            return TABLE;
        }
        if (name.contains(".index.")) {
            return INDEX;
        }
        return OTHER;
    }
}
