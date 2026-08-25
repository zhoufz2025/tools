package com.zhoufz.tools.db;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地库初始化配置
 *
 * @author zhoufz
 */
@Configuration
@EnableConfigurationProperties(LocalDbInitProperties.class)
public class LocalDbInitConfiguration {

    @Bean
    public LocalDbInitService localDbInitService(LocalDbInitProperties properties) {
        return new LocalDbInitService(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "local.db", name = "auto-init-on-start", havingValue = "true")
    public LocalDbInitRunner localDbInitRunner(LocalDbInitService localDbInitService) {
        return new LocalDbInitRunner(localDbInitService);
    }
}
