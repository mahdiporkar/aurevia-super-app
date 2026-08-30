package com.aurevia.authz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
class SecurityConfig {
  @Bean
  @ConditionalOnProperty(name="aurevia.internal.authentication-mode",havingValue="basic",matchIfMissing=true)
  SecurityFilterChain internalSecurity(HttpSecurity http,
      @Value("${aurevia.internal.username}") String username,
      @Value("${aurevia.internal.password}") String password) throws Exception {
    return http.csrf(csrf -> csrf.ignoringRequestMatchers("/internal/**"))
        .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**").permitAll().anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults()).build();
  }

  @Bean
  @ConditionalOnProperty(name="aurevia.internal.authentication-mode",havingValue="basic",matchIfMissing=true)
  UserDetailsService internalUser(@Value("${aurevia.internal.username}") String username,
      @Value("${aurevia.internal.password}") String password) {
    var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    return new InMemoryUserDetailsManager(User.withUsername(username)
        .password(encoder.encode(password)).roles("BFF").build());
  }

  @Bean
  @ConditionalOnProperty(name="aurevia.internal.authentication-mode",havingValue="mtls")
  SecurityFilterChain workloadIdentitySecurity(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.ignoringRequestMatchers("/internal/**"))
        .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**").permitAll()
            .anyRequest().hasRole("BFF"))
        .x509(x509 -> x509.subjectPrincipalRegex("CN=(.*?)(?:,|$)"))
        .build();
  }

  @Bean
  @ConditionalOnProperty(name="aurevia.internal.authentication-mode",havingValue="mtls")
  UserDetailsService workloadIdentities(
      @Value("${aurevia.internal.bff-identity:aurevia-bff}") String bffIdentity) {
    return username -> {
      if (!bffIdentity.equals(username)) {
        throw new org.springframework.security.core.userdetails.UsernameNotFoundException(username);
      }
      return User.withUsername(username).password("{noop}certificate-authenticated").roles("BFF").build();
    };
  }
}
