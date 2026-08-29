package com.aurevia.authz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebMvcConfiguration implements WebMvcConfigurer {
  private final AdminAuthorizationInterceptor adminAuthorization;

  WebMvcConfiguration(AdminAuthorizationInterceptor adminAuthorization) {
    this.adminAuthorization = adminAuthorization;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(adminAuthorization)
        .addPathPatterns("/internal/v1/registry/**")
        .excludePathPatterns("/internal/v1/registry/subjects/*/superset-assets");
  }
}
