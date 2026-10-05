package com.kitapsepeti.gateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

	private String jwksUrl = "http://localhost:8081/.well-known/jwks.json";
	private int cacheTtlSeconds = 300;
	private List<String> protectedPaths = new ArrayList<>(List.of("/api/protected/**"));

	public String getJwksUrl() {
		return jwksUrl;
	}

	public void setJwksUrl(String jwksUrl) {
		this.jwksUrl = jwksUrl;
	}

	public int getCacheTtlSeconds() {
		return cacheTtlSeconds;
	}

	public void setCacheTtlSeconds(int cacheTtlSeconds) {
		this.cacheTtlSeconds = cacheTtlSeconds;
	}

	public List<String> getProtectedPaths() {
		return protectedPaths;
	}

	public void setProtectedPaths(List<String> protectedPaths) {
		this.protectedPaths = protectedPaths;
	}

}
