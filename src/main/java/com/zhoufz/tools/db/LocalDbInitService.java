package com.zhoufz.tools.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本地 MySQL 建库与基线数据初始化
 *
 * @author zhoufz
 */
public class LocalDbInitService {

    private static final Logger log = LoggerFactory.getLogger(LocalDbInitService.class);

    private final LocalDbInitProperties properties;

    private final SqlScriptCollector scriptCollector = new SqlScriptCollector();

    public LocalDbInitService(LocalDbInitProperties properties) {
        this.properties = properties;
    }

    /**
     * 仅建库（不执行脚本），使用配置中的默认种类
     */
    public LocalDbInitResult createDatabases() {
        return createDatabases(properties.getCategorySet());
    }

    public LocalDbInitResult createDatabases(Set<DbInitCategory> categories) {
        return runInternal(categories, null, false, false);
    }

    /**
     * 建库 + 表结构，使用配置中的默认种类
     */
    public LocalDbInitResult initSchema() {
        return initSchema(properties.getCategorySet());
    }

    public LocalDbInitResult initSchema(Set<DbInitCategory> categories) {
        return runInternal(categories, SqlScriptPhase.SCHEMA, true, properties.isRecreateOnInit());
    }

    /**
     * 仅刷新 initdata，使用配置中的默认种类
     */
    public LocalDbInitResult initData() {
        return initData(properties.getCategorySet());
    }

    public LocalDbInitResult initData(Set<DbInitCategory> categories) {
        return runInternal(categories, SqlScriptPhase.INITDATA, true, false);
    }

    /**
     * 全量：建库 + schema + initdata，使用配置中的默认种类
     */
    public LocalDbInitResult initAll() {
        return initAll(properties.getCategorySet());
    }

    public LocalDbInitResult initAll(Set<DbInitCategory> categories) {
        LocalDbInitResult schemaResult = runInternal(categories, SqlScriptPhase.SCHEMA, true, properties.isRecreateOnInit());
        if (!schemaResult.isSuccess()) {
            return schemaResult;
        }

        LocalDbInitResult dataResult = runInternal(categories, SqlScriptPhase.INITDATA, true, false);
        schemaResult.getExecutedScripts().addAll(dataResult.getExecutedScripts());
        schemaResult.getSkippedScripts().addAll(dataResult.getSkippedScripts());
        schemaResult.getErrors().addAll(dataResult.getErrors());
        schemaResult.setElapsedMs(schemaResult.getElapsedMs() + dataResult.getElapsedMs());
        return schemaResult;
    }

    private LocalDbInitResult runInternal(Set<DbInitCategory> categories, SqlScriptPhase phase,
                                          boolean executeScripts, boolean recreateDatabase) {
        long start = System.currentTimeMillis();
        LocalDbInitResult result = new LocalDbInitResult();
        result.setSelectedCategories(formatCategories(categories));

        try {
            Path sqlRoot = Paths.get(properties.getSqlRoot()).toAbsolutePath().normalize();
            if (!Files.isDirectory(sqlRoot)) {
                throw new IOException("SQL 根目录不存在: " + sqlRoot);
            }

            MysqlCliExecutor executor = new MysqlCliExecutor(properties);
            executor.validateMysqlBin();

            List<DbInitTarget> targets = DbInitTarget.buildForCategories(categories, properties.isIncludeDxfundSingleDb());
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("未选择任何数据库种类");
            }

            log.info("本次刷新种类: {}", result.getSelectedCategories());

            for (DbInitTarget target : targets) {
                String database = target.getDatabaseName();
                if (phase == null || phase == SqlScriptPhase.SCHEMA) {
                    boolean shouldRecreate = recreateDatabase && target.isDropOnRecreate();
                    if (phase == null || shouldRecreate || !databaseExists(executor, database)) {
                        prepareDatabase(executor, database, shouldRecreate, result);
                    }
                }

                if (!executeScripts || phase == null) {
                    continue;
                }

                for (String modulePath : target.getSqlModulePaths()) {
                    Path moduleRoot = sqlRoot.resolve(modulePath).normalize();
                    if (!Files.isDirectory(moduleRoot)) {
                        String msg = "跳过不存在的 SQL 模块: " + moduleRoot;
                        log.warn(msg);
                        result.addSkippedScript(msg);
                        continue;
                    }

                    SqlScriptCollectResult collectResult = scriptCollector.collectWithSkipped(moduleRoot, phase);
                    result.getSkippedScripts().addAll(collectResult.getSkippedScripts());

                    for (Path script : collectResult.getScripts()) {
                        String label = database + " <- " + script;
                        try {
                            log.info("执行 SQL [{}]: {}", phase, label);
                            boolean filterTaTemplate = phase == SqlScriptPhase.SCHEMA;
                            MysqlCliExecutor.ScriptExecutionResult executionResult =
                                    executor.executeScript(database, script, filterTaTemplate);
                            if (!executionResult.isExecuted()) {
                                result.addSkippedScript(label + " (全部语句含模板占位符，共 "
                                        + executionResult.getSkippedStatements() + " 条)");
                                continue;
                            }
                            result.addExecutedScript(label);
                            if (executionResult.isFiltered() && executionResult.getSkippedStatements() > 0) {
                                result.addSkippedScript(label + " (已过滤模板语句 "
                                        + executionResult.getSkippedStatements() + " 条)");
                            }
                        } catch (Exception e) {
                            String error = "执行失败: " + label + " => " + e.getMessage();
                            log.error(error, e);
                            result.addError(error);
                            result.setElapsedMs(System.currentTimeMillis() - start);
                            return result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("本地库初始化异常", e);
            result.addError(e.getMessage());
        }

        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    private String formatCategories(Set<DbInitCategory> categories) {
        return categories.stream()
                .map(DbInitCategory::getCode)
                .collect(Collectors.joining(","));
    }

    private void prepareDatabase(MysqlCliExecutor executor, String database,
                                 boolean recreate, LocalDbInitResult result)
            throws IOException, InterruptedException {
        if (recreate) {
            log.info("DROP DATABASE IF EXISTS {}", database);
            executor.executeStatement("DROP DATABASE IF EXISTS `" + database + "`;");
        }
        log.info("CREATE DATABASE {}", database);
        executor.executeStatement("CREATE DATABASE IF NOT EXISTS `" + database
                + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
        result.addCreatedDatabase(database);
    }

    private boolean databaseExists(MysqlCliExecutor executor, String database) {
        try {
            executor.executeStatement("USE `" + database + "`;");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
