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

    private final LocalDbInitProperties localDbInitProperties;

    public LocalDbInitController(LocalDbInitService localDbInitService,
                                 ShardingViewService shardingViewService,
                                 LocalDbInitProperties localDbInitProperties) {
        this.localDbInitService = localDbInitService;
        this.shardingViewService = shardingViewService;
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
}
