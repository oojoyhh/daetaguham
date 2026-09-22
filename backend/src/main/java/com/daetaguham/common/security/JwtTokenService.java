package com.daetaguham.common.security;

import java.time.Duration;
import java.time.Instant;

import com.daetaguham.user.domain.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final String issuer;
	private final Duration accessTokenTtl;

	public JwtTokenService(
			JwtEncoder jwtEncoder,
			@Value("${app.auth.jwt.issuer}") String issuer,
			@Value("${app.auth.jwt.access-token-ttl}") Duration accessTokenTtl
	) {
		this.jwtEncoder = jwtEncoder;
		this.issuer = issuer;
		this.accessTokenTtl = accessTokenTtl;
	}

	public String issue(User user) {
		Instant issuedAt = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(accessTokenTtl))
				.subject(user.getId().toString())
				.claim("phone", user.getPhone())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
