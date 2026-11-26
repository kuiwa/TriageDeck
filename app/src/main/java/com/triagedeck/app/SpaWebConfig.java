package com.triagedeck.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * OIDC 授权码登录的 redirect_uri 是 /callback，该路径不存在对应静态文件，
 * 因此 forward 到 SPA 入口 index.html，由前端 oidc-client-ts 完成 code 兑换。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/callback").setViewName("forward:/index.html");
    }
}
