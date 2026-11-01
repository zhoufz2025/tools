package com.zhoufz.tools.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 按 spsql 发包版本刷新标准 MySQL DDL（排除 BANK / taclear / 非 mysql）。
 * <p>策略：拆语句执行；表/列/索引已存在（1050/1060/1061 等）则忽略并继续；
 * 可选账本跳过已成功语句；INSERT 改写为 INSERT IGNORE。</p>
 *
 * @author zhoufz
 */
public class SpsqlDdlRefreshService {

    private static final Logger log = LoggerFactory.getLogger(SpsqlDdlRefreshService.class);

    private static final String LEDGER_DB = DbInitTarget.PUB_DATABASE;

    private static final String LEDGER_TABLE = "tb_tools_spsql_ddl_applied";

    /** MySQL：表已存在 / 列已存在 / 索引已存在 / 删不存在的列或键 */
    private static final Set<Integer> IGNORABLE_SQL_STATES = new LinkedHashSet<>();

    static {
        IGNORABLE_SQL_STATES.add(1050);
        IGNORABLE_SQL_STATES.add(1060);
        IGNORABLE_SQL_STATES.add(1061);
        IGNORABLE_SQL_STATES.add(1091);
    }

    private final LocalDbInitProperties properties;

    private final MysqlJdbcHelper jdbcHelper;

    public SpsqlDdlRefreshService(LocalDbInitProperties properties) {
        this.properties = properties;
        this.jdbcHelper = new MysqlJdbcHelper(properties);
    }

    public Path resolveSpsqlRoot() {
        String root = properties.getSpsqlRoot();
        if (root == null || root.trim().isEmpty()) {
            throw new IllegalArgumentException("未配置 local.db.spsql-root");
        }
        return Paths.get(root.trim()).toAbsolutePath().normalize();
    }

    /**
     * 列出各模块可用发包版本（目录名 IFMS6.0V…）。
     */
    public Map<String, List<String>> listVersions(Set<SpsqlModule> modules) throws IOException {
        Path spsqlRoot = resolveSpsqlRoot();
        if (!Files.isDirectory(spsqlRoot)) {
            throw new IOException("spsql 根目录不存在: " + spsqlRoot);
        }
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (SpsqlModule module : modules) {
            Path moduleDir = spsqlRoot.resolve(module.getDirName());
            List<String> versions = new ArrayList<>();
            if (Files.isDirectory(moduleDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(moduleDir)) {
                    for (Path child : stream) {
                        if (Files.isDirectory(child)) {
                            String name = child.getFileName().toString();
                            if (name.startsWith("IFMS") || name.startsWith("ifms")) {
                                versions.add(name);
                            }
                        }
                    }
                }
                versions.sort(Comparator.reverseOrder());
            }
            map.put(module.getCode(), versions);
        }
        return map;
    }

    /**
     * 所选模块版本并集（倒序），便于 UI 选一个版本批量刷。
     */
    public List<String> listUnionVersions(Set<SpsqlModule> modules) throws IOException {
        Set<String> union = new TreeSet<>(Comparator.reverseOrder());
        for (List<String> versions : listVersions(modules).values()) {
            union.addAll(versions);
        }
        return new ArrayList<>(union);
    }

    /**
     * 预览将执行的脚本。
     * <p>含相对路径（带 pub/trans），避免同名 mysql.DDL 分不清片区。</p>
     */
    public List<Map<String, String>> preview(Set<SpsqlModule> modules, String packageVersion) throws IOException {
        Path spsqlRoot = resolveSpsqlRoot();
        List<ScriptJob> jobs = collectJobs(modules, packageVersion);
        List<Map<String, String>> rows = new ArrayList<>();
        for (ScriptJob job : jobs) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("module", job.module.getCode());
            row.put("moduleLabel", job.module.getLabel());
            row.put("area", job.area);
            row.put("areaLabel", areaLabel(job.area));
            row.put("database", job.database);
            row.put("file", job.file.getFileName().toString());
            // 相对 spsql 根：spsql-fina/.../trans/fina/xxx.mysql.DDL.sql
            row.put("relPath", toRelPath(spsqlRoot, job.file));
            row.put("path", job.file.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String areaLabel(String area) {
        if ("pub".equalsIgnoreCase(area)) {
            return "公共片区";
        }
        if ("trans".equalsIgnoreCase(area)) {
            return "交易片区";
        }
        return area == null ? "" : area;
    }

    private static String toRelPath(Path spsqlRoot, Path file) {
        try {
            Path absRoot = spsqlRoot.toAbsolutePath().normalize();
            Path absFile = file.toAbsolutePath().normalize();
            if (absFile.startsWith(absRoot)) {
                return absRoot.relativize(absFile).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
            // fall through
        }
        return file.toString().replace('\\', '/');
    }

    /**
     * 执行刷新。
     *
     * @param useLedger true 时跳过账本中已成功的语句，并记录本次成功语句
     */
    public SpsqlDdlRefreshResult apply(Set<SpsqlModule> modules, String packageVersion, boolean useLedger) {
        long start = System.currentTimeMillis();
        SpsqlDdlRefreshResult result = new SpsqlDdlRefreshResult();
        result.setPackageVersion(packageVersion == null ? "" : packageVersion.trim());
        result.setModules(String.join(",", SpsqlModule.codesOf(modules)));
        result.setUseLedger(useLedger);

        try {
            if (packageVersion == null || packageVersion.trim().isEmpty()) {
                throw new IllegalArgumentException("发包版本不能为空");
            }
            List<ScriptJob> jobs = collectJobs(modules, packageVersion.trim());
            if (jobs.isEmpty()) {
                result.addSkippedFile("未找到可执行的标准 mysql.DDL 脚本（已排除 BANK/taclear）");
                result.setElapsedMs(System.currentTimeMillis() - start);
                return result;
            }
            // 发包 DDL 大量 call proc_addindex/proc_addcolumn…，依赖基线存储过程；缺一个会刷出成百上千条假失败
            ensureHelperProcedures(jobs, result);
            if (useLedger) {
                ensureLedgerTable();
            }
            for (ScriptJob job : jobs) {
                applyFile(job, useLedger, result);
            }
        } catch (Exception e) {
            log.error("spsql DDL 刷新失败", e);
            result.addError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        result.setElapsedMs(System.currentTimeMillis() - start);
        if (!result.getErrors().isEmpty()) {
            log.warn("spsql DDL 刷新结束有错误: version={}, modules={}, errors={}, 首条={}",
                    result.getPackageVersion(), result.getModules(), result.getErrors().size(),
                    result.getErrors().get(0));
            for (String err : result.getErrors()) {
                log.warn("spsql DDL 错误明细: {}", err);
            }
        } else {
            log.info("spsql DDL 刷新成功: version={}, modules={}, ok={}, ignored={}, ledgerSkip={}, {}ms",
                    result.getPackageVersion(), result.getModules(), result.getSuccessStatements(),
                    result.getIgnoredCount(), result.getLedgerSkipCount(), result.getElapsedMs());
        }
        return result;
    }

    private List<ScriptJob> collectJobs(Set<SpsqlModule> modules, String packageVersion) throws IOException {
        Path spsqlRoot = resolveSpsqlRoot();
        List<ScriptJob> jobs = new ArrayList<>();
        for (SpsqlModule module : modules) {
            Path versionRoot = spsqlRoot.resolve(module.getDirName()).resolve(packageVersion);
            if (!Files.isDirectory(versionRoot)) {
                continue;
            }
            Path pubDir = versionRoot.resolve("pub").resolve(module.getBizDir());
            // 先 proc（含 proc_modpk 等）再 DDL，否则 call 会报过程不存在
            collectMysqlScriptFiles(module, "pub", pubDir, module.getPubDatabases(), jobs, true);
            collectMysqlScriptFiles(module, "pub", pubDir, module.getPubDatabases(), jobs, false);

            if (module.getTransDatabases().length > 0) {
                Path transDir = versionRoot.resolve("trans").resolve(module.getBizDir());
                collectMysqlScriptFiles(module, "trans", transDir, module.getTransDatabases(), jobs, true);
                collectMysqlScriptFiles(module, "trans", transDir, module.getTransDatabases(), jobs, false);
            }
        }
        return jobs;
    }

    private void collectMysqlScriptFiles(SpsqlModule module, String area, Path dir, String[] databases,
                                         List<ScriptJob> jobs, boolean procFiles) throws IOException {
        if (!Files.isDirectory(dir) || databases == null || databases.length == 0) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.sql")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String name = file.getFileName().toString();
                boolean match = procFiles ? isStandardMysqlProc(name) : isStandardMysqlDdl(name);
                if (match) {
                    files.add(file);
                }
            }
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        String packageVersion = dir.getParent() == null || dir.getParent().getParent() == null
                ? ""
                : dir.getParent().getParent().getFileName().toString();
        for (Path file : files) {
            for (String database : databases) {
                jobs.add(new ScriptJob(module, area, database, packageVersion, file, procFiles));
            }
        }
    }

    /**
     * 标准包：*.mysql.DDL.sql（非 proc），排除 BANK、taclear。
     */
    static boolean isStandardMysqlDdl(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".mysql.ddl.sql") || lower.contains(".mysql.proc.ddl.sql")) {
            return false;
        }
        return !isBankOrTaclear(lower);
    }

    /**
     * 标准包存储过程：*.mysql.proc.DDL.sql（如 proc_modpk），排除 BANK、taclear。
     */
    static boolean isStandardMysqlProc(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".mysql.proc.ddl.sql")) {
            return false;
        }
        return !isBankOrTaclear(lower);
    }

    private static boolean isBankOrTaclear(String lowerFileName) {
        return lowerFileName.contains("-bank-")
                || lowerFileName.contains("_bank_")
                || lowerFileName.contains("taclear");
    }

    /**
     * 对本次涉及的每个目标库补齐基线 helper 存储过程（含 DELIMITER，走 mysql 客户端）。
     * 现场常见：库已建表但缺 proc_addindex（基线后补），导致发包 DDL 中每条 call 都报错。
     */
    private void ensureHelperProcedures(List<ScriptJob> jobs, SpsqlDdlRefreshResult result) throws Exception {
        Path procFile = resolveCreateProcMysqlFile();
        if (!Files.isRegularFile(procFile)) {
            throw new IOException("未找到基线存储过程脚本: " + procFile
                    + "（请检查 local.db.sql-root）");
        }
        Set<String> databases = new LinkedHashSet<>();
        for (ScriptJob job : jobs) {
            databases.add(job.database);
        }
        MysqlCliExecutor executor = new MysqlCliExecutor(properties);
        executor.validateMysqlBin();
        for (String database : databases) {
            if (!jdbcHelper.databaseExists(database)) {
                continue;
            }
            if (procedureExists(database, "proc_addindex")
                    && procedureExists(database, "proc_addcolumn")
                    && procedureExists(database, "proc_droptable")) {
                continue;
            }
            log.info("目标库 {} 缺少 helper 存储过程，正在导入 {}", database, procFile.getFileName());
            executor.executeScript(database, procFile, false);
            result.addExecutedFile(database + " <- 补齐基线存储过程 " + procFile.getFileName());
            if (!procedureExists(database, "proc_addindex")) {
                throw new SQLException("导入后仍缺少 proc_addindex，库=" + database
                        + "，脚本=" + procFile);
            }
        }
    }

    private Path resolveCreateProcMysqlFile() {
        String sqlRoot = properties.getSqlRoot();
        if (sqlRoot == null || sqlRoot.trim().isEmpty()) {
            throw new IllegalArgumentException("未配置 local.db.sql-root");
        }
        return Paths.get(sqlRoot.trim()).toAbsolutePath().normalize()
                .resolve("sql-pub")
                .resolve("pub")
                .resolve("pub")
                .resolve("base")
                .resolve("000schema")
                .resolve("mysql")
                .resolve("000create_proc_mysql.sql");
    }

    private boolean procedureExists(String database, String procedureName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.ROUTINES"
                + " WHERE ROUTINE_SCHEMA=? AND ROUTINE_NAME=? AND ROUTINE_TYPE='PROCEDURE' LIMIT 1";
        try (Connection connection = jdbcHelper.openConnection(database);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, database);
            ps.setString(2, procedureName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void applyFile(ScriptJob job, boolean useLedger, SpsqlDdlRefreshResult result) {
        // 标签带 pub|trans 相对路径，避免同名文件分不清片区
        String rel = toRelPath(resolveSpsqlRoot(), job.file);
        String label = job.database + " <- [" + job.module.getCode() + "/" + areaLabel(job.area) + "] " + rel;
        try {
            if (!jdbcHelper.databaseExists(job.database)) {
                result.addSkippedFile(label + " (库不存在，请先本地库初始化)");
                return;
            }
            if (job.procFile) {
                // 发包存储过程脚本：无 DELIMITER，需包装后整文件走 mysql 客户端
                applyProcFileWithCli(job, label, result);
                return;
            }
            if (SqlScriptSanitizer.containsDelimiter(job.file)) {
                // 含 DELIMITER 的脚本整文件执行，失败则记错误（少见）
                applyWholeFileWithCli(job, label, result);
                return;
            }
            String sql = SqlScriptSanitizer.readAndStripComments(job.file);
            sql = SqlScriptSanitizer.stripBomChar(sql);
            sql = SqlScriptSanitizer.rewriteInsertToIgnore(sql);
            List<String> statements = SqlStatementSplitter.split(sql);
            if (statements.isEmpty()) {
                result.addSkippedFile(label + " (无有效语句)");
                return;
            }
            int fileOk = 0;
            try (Connection connection = jdbcHelper.openConnection(job.database);
                 Statement statement = connection.createStatement()) {
                for (String stmt : statements) {
                    String normalized = SqlScriptSanitizer.stripBomChar(stmt).trim();
                    if (normalized.isEmpty() || ";".equals(normalized)) {
                        continue;
                    }
                    String hash = sha256(normalized);
                    if (useLedger && isLedgerApplied(job, hash)) {
                        result.addLedgerSkip(shortPreview(normalized));
                        continue;
                    }
                    try {
                        statement.execute(normalized);
                        fileOk++;
                        result.addSuccessStatement();
                        if (useLedger) {
                            markLedgerApplied(job, hash, normalized);
                        }
                    } catch (SQLException e) {
                        if (isIgnorable(e)) {
                            result.addIgnored(sqlErrorPreview(e, normalized));
                            if (useLedger) {
                                markLedgerApplied(job, hash, normalized);
                            }
                        } else {
                            result.addError(label + " => " + e.getMessage() + " | " + shortPreview(normalized));
                        }
                    }
                }
            }
            result.addExecutedFile(label + " (ok=" + fileOk + ")");
            log.info("spsql DDL 完成: {}", label);
        } catch (Exception e) {
            result.addError(label + " => " + e.getMessage());
        }
    }

    private void applyWholeFileWithCli(ScriptJob job, String label, SpsqlDdlRefreshResult result) {
        try {
            MysqlCliExecutor executor = new MysqlCliExecutor(properties);
            executor.validateMysqlBin();
            executor.executeScript(job.database, job.file, true);
            result.addExecutedFile(label + " (整文件/DELIMITER)");
            result.addSuccessStatement();
        } catch (Exception e) {
            result.addError(label + " => " + e.getMessage());
        }
    }

    /**
     * 执行 *.mysql.proc.DDL.sql：补 DELIMITER 后走 mysql 客户端（JDBC 拆语句会打断过程体）。
     */
    private void applyProcFileWithCli(ScriptJob job, String label, SpsqlDdlRefreshResult result) {
        Path tempFile = null;
        try {
            String wrapped = SqlScriptSanitizer.wrapProceduresWithDelimiter(
                    SqlScriptSanitizer.readSqlContent(job.file));
            tempFile = Files.createTempFile("spsql-proc-", ".sql");
            Files.write(tempFile, wrapped.getBytes(StandardCharsets.UTF_8));
            MysqlCliExecutor executor = new MysqlCliExecutor(properties);
            executor.validateMysqlBin();
            executor.executeScript(job.database, tempFile, false);
            result.addExecutedFile(label + " (proc/整文件)");
            result.addSuccessStatement();
            log.info("spsql proc 完成: {}", label);
        } catch (Exception e) {
            result.addError(label + " => " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    private boolean isIgnorable(SQLException e) {
        int code = e.getErrorCode();
        if (IGNORABLE_SQL_STATES.contains(code)) {
            return true;
        }
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("already exists")
                || lower.contains("duplicate column")
                || lower.contains("duplicate key name")
                || lower.contains("check that column/key exists");
    }

    private void ensureLedgerTable() throws SQLException {
        if (!jdbcHelper.databaseExists(LEDGER_DB)) {
            throw new SQLException("账本库不存在: " + LEDGER_DB + "，请先初始化公共库");
        }
        String ddl = "CREATE TABLE IF NOT EXISTS " + LEDGER_TABLE + " ("
                + "id BIGINT NOT NULL AUTO_INCREMENT,"
                + "module_code VARCHAR(32) NOT NULL,"
                + "package_ver VARCHAR(64) NOT NULL,"
                + "area VARCHAR(16) NOT NULL,"
                + "database_name VARCHAR(64) NOT NULL,"
                + "file_name VARCHAR(255) NOT NULL,"
                + "stmt_hash CHAR(64) NOT NULL,"
                + "stmt_preview VARCHAR(500) NOT NULL DEFAULT '',"
                + "applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (id),"
                + "UNIQUE KEY uk_spsql_ddl (module_code, package_ver, area, database_name, file_name, stmt_hash)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tools spsql DDL 已执行账本'";
        jdbcHelper.execute(LEDGER_DB, ddl);
    }

    private boolean isLedgerApplied(ScriptJob job, String hash) throws SQLException {
        String sql = "SELECT 1 FROM " + LEDGER_TABLE
                + " WHERE module_code=? AND package_ver=? AND area=? AND database_name=? AND file_name=? AND stmt_hash=? LIMIT 1";
        try (Connection connection = jdbcHelper.openConnection(LEDGER_DB);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, job.module.getCode());
            ps.setString(2, job.packageVersion());
            ps.setString(3, job.area);
            ps.setString(4, job.database);
            ps.setString(5, job.file.getFileName().toString());
            ps.setString(6, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void markLedgerApplied(ScriptJob job, String hash, String stmt) throws SQLException {
        String sql = "INSERT IGNORE INTO " + LEDGER_TABLE
                + " (module_code, package_ver, area, database_name, file_name, stmt_hash, stmt_preview)"
                + " VALUES (?,?,?,?,?,?,?)";
        try (Connection connection = jdbcHelper.openConnection(LEDGER_DB);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, job.module.getCode());
            ps.setString(2, job.packageVersion());
            ps.setString(3, job.area);
            ps.setString(4, job.database);
            ps.setString(5, job.file.getFileName().toString());
            ps.setString(6, hash);
            ps.setString(7, shortPreview(stmt));
            ps.executeUpdate();
        }
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static String shortPreview(String stmt) {
        String oneLine = stmt.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > 180) {
            return oneLine.substring(0, 180) + "...";
        }
        return oneLine;
    }

    private static String sqlErrorPreview(SQLException e, String stmt) {
        return "errno=" + e.getErrorCode() + " " + shortPreview(stmt);
    }

    private static final class ScriptJob {
        private final SpsqlModule module;
        private final String area;
        private final String database;
        private final String packageVersion;
        private final Path file;
        private final boolean procFile;

        private ScriptJob(SpsqlModule module, String area, String database, String packageVersion,
                          Path file, boolean procFile) {
            this.module = module;
            this.area = area;
            this.database = database;
            this.packageVersion = packageVersion;
            this.file = file;
            this.procFile = procFile;
        }

        private String packageVersion() {
            return packageVersion;
        }
    }
}
