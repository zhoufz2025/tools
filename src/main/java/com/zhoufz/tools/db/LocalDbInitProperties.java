package com.zhoufz.tools.db;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地 MySQL 初始化配置
 *
 * @author zhoufz
 */
@ConfigurationProperties(prefix = "local.db")
public class LocalDbInitProperties {

    /** SQL 基线脚本根目录，指向 app/sql */
    private String sqlRoot = "/Users/zhoufz/hundsun/lcpt60/git/Sources/app/sql";

    /** mysql 客户端路径 */
    private String mysqlBin = "/opt/homebrew/opt/mysql@8.4/bin/mysql";

    private String host = "127.0.0.1";

    private int port = 3306;

    private String username = "root";

    private String password = "";

    /** 初始化前是否 DROP 并重建库（全量刷新） */
    private boolean recreateOnInit = true;

    /** 是否创建并初始化 dxfundsingledb（基金批量单库，可选） */
    private boolean includeDxfundSingleDb = false;

    /** 是否在应用启动时自动执行全量初始化 */
    private boolean autoInitOnStart = false;

    /**
     * 默认刷新的数据库种类，逗号分隔：pub,dxfund,dxasset,dxtrust,fina
     * 未传参时使用该配置
     */
    private String categories = "pub,dxfund,dxasset,dxtrust";

    public String getSqlRoot() {
        return sqlRoot;
    }

    public void setSqlRoot(String sqlRoot) {
        this.sqlRoot = sqlRoot;
    }

    public String getMysqlBin() {
        return mysqlBin;
    }

    public void setMysqlBin(String mysqlBin) {
        this.mysqlBin = mysqlBin;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isRecreateOnInit() {
        return recreateOnInit;
    }

    public void setRecreateOnInit(boolean recreateOnInit) {
        this.recreateOnInit = recreateOnInit;
    }

    public boolean isIncludeDxfundSingleDb() {
        return includeDxfundSingleDb;
    }

    public void setIncludeDxfundSingleDb(boolean includeDxfundSingleDb) {
        this.includeDxfundSingleDb = includeDxfundSingleDb;
    }

    public boolean isAutoInitOnStart() {
        return autoInitOnStart;
    }

    public void setAutoInitOnStart(boolean autoInitOnStart) {
        this.autoInitOnStart = autoInitOnStart;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public java.util.Set<DbInitCategory> getCategorySet() {
        return DbInitCategory.parseCsv(categories);
    }
}
