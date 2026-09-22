package com.daetaguham.common.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

	private final SecretKey secretKey;
	private final String issuer;

	public JwtConfig(
			@Value("${app.auth.jwt.secret}") String secret,
			@Value("${app.auth.jwt.issuer}") String issuer
	) {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < 32) {
			throw new IllegalStateException("JWT secret must be at least 32 bytes");
		}
		this.secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
		this.issuer = issuer;
	}

	@Bean
	JwtEncoder jwtEncoder() {
		return NimbusJwtEncoder.withSecretKey(secretKey)
				.algorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
		return decoder;
	}
}
