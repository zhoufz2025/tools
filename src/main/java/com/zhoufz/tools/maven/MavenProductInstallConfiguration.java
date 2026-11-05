package com.zhoufz.tools.maven;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhoufz
 */
@Configuration
@EnableConfigurationProperties(MavenProductInstallProperties.class)
public class MavenProductInstallConfiguration {
}
