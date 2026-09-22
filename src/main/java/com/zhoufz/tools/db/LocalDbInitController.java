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

    private final LocalDbInitProperties localDbInitProperties;

    public LocalDbInitController(LocalDbInitService localDbInitService,
                                 LocalDbInitProperties localDbInitProperties) {
        this.localDbInitService = localDbInitService;
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
}
