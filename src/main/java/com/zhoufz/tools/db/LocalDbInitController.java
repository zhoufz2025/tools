package com.zhoufz.tools.db;

import com.zhoufz.tools.dto.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地 MySQL 初始化接口
 *
 * @author zhoufz
 */
@RestController
@RequestMapping("/hundsun/db")
public class LocalDbInitController {

    private final LocalDbInitService localDbInitService;

    private final ShardingViewService shardingViewService;

    private final SpsqlDdlRefreshService spsqlDdlRefreshService;

    private final LocalDbInitProperties localDbInitProperties;

    public LocalDbInitController(LocalDbInitService localDbInitService,
                                 ShardingViewService shardingViewService,
                                 SpsqlDdlRefreshService spsqlDdlRefreshService,
                                 LocalDbInitProperties localDbInitProperties) {
        this.localDbInitService = localDbInitService;
        this.shardingViewService = shardingViewService;
        this.spsqlDdlRefreshService = spsqlDdlRefreshService;
        this.localDbInitProperties = localDbInitProperties;
    }

    /**
     * 查看支持的数据库种类
     */
    @GetMapping("/categories")
    public Result<List<Map<String, String>>> listCategories() {
        List<Map<String, String>> list = new ArrayList<>();
        for (DbInitCategory category : DbInitCategory.values()) {
            Map<String, String> item = new HashMap<>();
            item.put("code", category.getCode());
            item.put("label", category.getLabel());
            item.put("databases", category.getDatabaseNames());
            list.add(item);
        }
        return Result.success("可选数据库种类", list);
    }

    /**
     * 仅建库（CREATE DATABASE IF NOT EXISTS）
     *
     * @param categories 可选，逗号分隔：pub,dxfund,dxasset,dxtrust,fina
     */
    @RequestMapping(value = "/createDatabases", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<LocalDbInitResult> createDatabases(
            @RequestParam(value = "categories", required = false) String categories) {
        return wrap(localDbInitService.createDatabases(resolveCategories(categories)));
    }

    /**
     * 建库 + 执行表结构 DDL
     */
    @RequestMapping(value = "/initSchema", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<LocalDbInitResult> initSchema(
            @RequestParam(value = "categories", required = false) String categories) {
        return wrap(localDbInitService.initSchema(resolveCategories(categories)));
    }

    /**
     * 仅执行 initdata（表须已存在）
     */
    @RequestMapping(value = "/initData", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<LocalDbInitResult> initData(
            @RequestParam(value = "categories", required = false) String categories) {
        return wrap(localDbInitService.initData(resolveCategories(categories)));
    }

    /**
     * 全量：建库 + DDL + initdata
     */
    @RequestMapping(value = "/initAll", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<LocalDbInitResult> initAll(
            @RequestParam(value = "categories", required = false) String categories) {
        return wrap(localDbInitService.initAll(resolveCategories(categories)));
    }

    /**
     * 全量：建库 + DDL + initdata
     */
    @RequestMapping(value = "/init", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<LocalDbInitResult> init(
            @RequestParam(value = "categories", required = false) String categories) {
        return wrap(localDbInitService.initAll(resolveCategories(categories)));
    }

    /**
     * 根据 tbareainfo 分表数生成 UNION ALL 视图（可重复执行）
     * <p>
     * 示例：
     * <ul>
     *   <li>自动识别：{@code /hundsun/db/createShardingViews?databases=lcptpub}</li>
     *   <li>指定表：{@code /hundsun/db/createShardingViews?databases=lcptpub&amp;tables=tbclient}</li>
     *   <li>按种类：{@code /hundsun/db/createShardingViews?categories=pub,dxfund}</li>
     * </ul>
     *
     * @param categories 可选，逗号分隔种类；与 databases 二选一优先 databases
     * @param databases  可选，逗号分隔库名，如 lcptpub,dxfundtrans1
     * @param tables     可选，逗号分隔基表名，如 tbclient,tbclientadd；不传则自动识别完整分表组
     */
    @RequestMapping(value = "/createShardingViews", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<ShardingViewResult> createShardingViews(
            @RequestParam(value = "categories", required = false) String categories,
            @RequestParam(value = "databases", required = false) String databases,
            @RequestParam(value = "tables", required = false) String tables) {
        List<String> dbList = ShardingViewService.parseCsv(databases);
        List<String> tableList = ShardingViewService.parseCsv(tables);
        Set<DbInitCategory> categorySet = dbList.isEmpty() ? resolveCategories(categories) : null;
        ShardingViewResult result = shardingViewService.createViews(categorySet, dbList, tableList);
        return wrapViewResult(result);
    }

    private Set<DbInitCategory> resolveCategories(String categories) {
        if (StringUtils.hasText(categories)) {
            return DbInitCategory.parseCsv(categories);
        }
        return localDbInitProperties.getCategorySet();
    }

    private Result<LocalDbInitResult> wrap(LocalDbInitResult result) {
        if (result.isSuccess()) {
            return Result.success("本地库初始化完成，种类=" + result.getSelectedCategories()
                    + "，耗时 " + result.getElapsedMs() + " ms", result);
        }
        return Result.error("本地库初始化失败: " + String.join("; ", result.getErrors()));
    }

    private Result<ShardingViewResult> wrapViewResult(ShardingViewResult result) {
        if (result.isSuccess()) {
            return Result.success("分表视图生成完成，库=" + result.getDatabases()
                    + "，分表数=" + result.getShardCount()
                    + "，新建/更新 " + result.getCreatedViews().size() + " 个"
                    + "，耗时 " + result.getElapsedMs() + " ms", result);
        }
        Result<ShardingViewResult> error = Result.error(
                "分表视图生成失败: " + String.join("; ", result.getErrors()));
        error.setData(result);
        return error;
    }

    /**
     * spsql 模块列表（可多选一起刷 / 单系统刷）
     */
    @GetMapping("/spsql/modules")
    public Result<List<Map<String, String>>> listSpsqlModules() {
        List<Map<String, String>> list = new ArrayList<>();
        for (SpsqlModule module : SpsqlModule.values()) {
            Map<String, String> item = new HashMap<>();
            item.put("code", module.getCode());
            item.put("label", module.getLabel());
            item.put("dirName", module.getDirName());
            item.put("pubDatabases", String.join(",", module.getPubDatabases()));
            item.put("transDatabases", String.join(",", module.getTransDatabases()));
            list.add(item);
        }
        return Result.success("spsql 模块", list);
    }

    /**
     * 列出所选模块的发包版本（并集，新版本在前）
     *
     * @param modules 逗号分隔：pub,dxfund,dxasset,dxtrust,fina；空=全部
     */
    @GetMapping("/spsql/versions")
    public Result<Map<String, Object>> listSpsqlVersions(
            @RequestParam(value = "modules", required = false) String modules) {
        try {
            Set<SpsqlModule> moduleSet = SpsqlModule.parseCsv(modules);
            Map<String, List<String>> perModule = spsqlDdlRefreshService.listVersions(moduleSet);
            List<String> union = spsqlDdlRefreshService.listUnionVersions(moduleSet);
            Map<String, Object> data = new HashMap<>();
            data.put("spsqlRoot", spsqlDdlRefreshService.resolveSpsqlRoot().toString());
            data.put("perModule", perModule);
            data.put("versions", union);
            return Result.success("共 " + union.size() + " 个版本", data);
        } catch (Exception e) {
            return Result.error("列出 spsql 版本失败: " + e.getMessage());
        }
    }

    /**
     * 预览将执行的标准 mysql.DDL 文件
     */
    @GetMapping("/spsql/preview")
    public Result<List<Map<String, String>>> previewSpsqlDdl(
            @RequestParam("version") String version,
            @RequestParam(value = "modules", required = false) String modules) {
        try {
            Set<SpsqlModule> moduleSet = SpsqlModule.parseCsv(modules);
            List<Map<String, String>> rows = spsqlDdlRefreshService.preview(moduleSet, version);
            return Result.success("将执行 " + rows.size() + " 个脚本任务", rows);
        } catch (Exception e) {
            return Result.error("预览失败: " + e.getMessage());
        }
    }

    /**
     * 按发包版本刷新标准 mysql.DDL（排除 BANK/taclear）。
     * 拆语句执行；已存在类错误忽略；可选账本跳过已成功语句。
     *
     * @param useLedger 默认 true
     */
    @RequestMapping(value = "/spsql/apply", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<SpsqlDdlRefreshResult> applySpsqlDdl(
            @RequestParam("version") String version,
            @RequestParam(value = "modules", required = false) String modules,
            @RequestParam(value = "useLedger", required = false, defaultValue = "true") boolean useLedger) {
        Set<SpsqlModule> moduleSet = SpsqlModule.parseCsv(modules);
        SpsqlDdlRefreshResult result = spsqlDdlRefreshService.apply(moduleSet, version, useLedger);
        if (result.isSuccess()) {
            return Result.success("spsql DDL 刷新完成，版本=" + result.getPackageVersion()
                    + "，成功语句=" + result.getSuccessStatements()
                    + "，忽略已存在=" + result.getIgnoredCount()
                    + "，账本跳过=" + result.getLedgerSkipCount()
                    + "，耗时 " + result.getElapsedMs() + " ms", result);
        }
        // message 只给短摘要，明细在 data.errors，避免前端 ElMessage 整屏挡住
        int errCount = result.getErrors() == null ? 0 : result.getErrors().size();
        String first = errCount == 0 ? "未知错误" : result.getErrors().get(0);
        if (first.length() > 160) {
            first = first.substring(0, 160) + "...";
        }
        Result<SpsqlDdlRefreshResult> error = Result.error(
                "spsql DDL 刷新失败（" + errCount + " 条），首条: " + first + "；详见下方错误列表");
        error.setData(result);
        return error;
    }
}
