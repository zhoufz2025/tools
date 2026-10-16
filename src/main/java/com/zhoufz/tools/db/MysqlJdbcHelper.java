package com.zhoufz.tools.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

/**
 * 基于 JDBC 连接本地 MySQL（适合查询与短 DDL，如建视图）
 *
 * @author zhoufz
 */
public class MysqlJdbcHelper {

    private final LocalDbInitProperties properties;

    public MysqlJdbcHelper(LocalDbInitProperties properties) {
        this.properties = properties;
    }

    public Connection openConnection(String database) throws SQLException {
        String url = "jdbc:mysql://" + properties.getHost() + ":" + properties.getPort()
                + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Shanghai";
        Properties props = new Properties();
        props.setProperty("user", properties.getUsername());
        if (properties.getPassword() != null) {
            props.setProperty("password", properties.getPassword());
        }
        return DriverManager.getConnection(url, props);
    }

    public <T> List<T> query(String database, String sql, Function<ResultSet, T> mapper) throws SQLException {
        List<T> rows = new ArrayList<>();
        try (Connection connection = openConnection(database);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows.add(mapper.apply(resultSet));
            }
        }
        return rows;
    }

    public int queryInt(String database, String sql, int defaultValue) throws SQLException {
        try (Connection connection = openConnection(database);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return defaultValue;
        }
    }

    public List<String> queryStrings(String database, String sql) throws SQLException {
        return query(database, sql, rs -> {
            try {
                return rs.getString(1);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public void execute(String database, String sql) throws SQLException {
        try (Connection connection = openConnection(database);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public boolean databaseExists(String database) {
        try {
            List<String> names = queryStrings("information_schema",
                    "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '"
                            + escapeLiteral(database) + "'");
            return !names.isEmpty();
        } catch (SQLException e) {
            return false;
        }
    }

    public static String escapeLiteral(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }

    public static String quoteIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("标识符不能为空");
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法标识符: " + name);
        }
        return "`" + name + "`";
    }
}
