package com.ecsteam.slack;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "slackbot")
public class SlackbotProperties {
	private CloudFoundryProperties cloudfoundry;
	
	private String webhookUrl;
	
	private String botToken;
	
	@Data
	public static class CloudFoundryProperties {
		private Map<String, CloudFoundryEnvironment> environments = new HashMap<>();
	}

	@Data
	public static class CloudFoundryEnvironment {
		private String displayName;

		private String url;

		private String adminUser;

		private String adminPassword;
	}
}
