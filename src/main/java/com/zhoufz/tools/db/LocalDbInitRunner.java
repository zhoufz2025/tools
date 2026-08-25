package com.zhoufz.tools.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

/**
 * 应用启动时自动执行全量库初始化
 *
 * @author zhoufz
 */
public class LocalDbInitRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDbInitRunner.class);

    private final LocalDbInitService localDbInitService;

    public LocalDbInitRunner(LocalDbInitService localDbInitService) {
        this.localDbInitService = localDbInitService;
    }

    @Override
    public void run(String... args) {
        log.info("local.db.auto-init-on-start=true，开始自动初始化本地数据库...");
        LocalDbInitResult result = localDbInitService.initAll();
        if (result.isSuccess()) {
            log.info("自动初始化完成，执行脚本 {} 个，耗时 {} ms",
                    result.getExecutedScripts().size(), result.getElapsedMs());
        } else {
            log.error("自动初始化失败: {}", result.getErrors());
        }
    }
}
