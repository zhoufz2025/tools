package com.zhoufz.tools.db;

/**
 * SQL 执行阶段
 *
 * @author zhoufz
 */
public enum SqlScriptPhase {
    /** 表结构 DDL */
    SCHEMA,
    /** 基础数据 initdata */
    INITDATA
}
