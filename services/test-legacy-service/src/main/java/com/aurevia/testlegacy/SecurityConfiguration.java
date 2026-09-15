package com.aurevia.testlegacy;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;

@Configuration
class SecurityConfiguration {
  @Bean SecurityFilterChain security(HttpSecurity http,LegacyCredentials credentials) throws Exception {
    return http.csrf(csrf -> csrf.disable())
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth.requestMatchers("/health","/auth/token").permitAll().anyRequest().authenticated())
      .exceptionHandling(errors -> errors.authenticationEntryPoint((request,response,error) -> response.setStatus(401)))
      .addFilterBefore(new OncePerRequestFilter() {
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
          String auth=request.getHeader("Authorization");
          if (auth!=null && auth.startsWith("Bearer ") && credentials.accepts(auth.substring(7))) {
            SecurityContextHolder.getContext().setAuthentication(
              new UsernamePasswordAuthenticationToken("legacy-test-service",null,List.of()));
          }
          chain.doFilter(request,response);
        }
      }, AnonymousAuthenticationFilter.class).build();
  }
}
