package com.daetaguham.common.config;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ApiSecurityErrorHandler errorHandler
	) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/auth/signup",
								"/auth/login",
								"/health",
								"/actuator/health",
								"/actuator/info"
						).permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler)
				)
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(errorHandler)
						.accessDeniedHandler(errorHandler)
				);
		return http.build();
	}
}
