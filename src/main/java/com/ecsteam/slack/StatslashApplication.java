package com.ecsteam.slack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@SpringBootApplication
@EnableConfigServer
public class StatslashApplication {

	public static void main(String[] args) {
		SpringApplication.run(StatslashApplication.class, args);
	}

	@Configuration
	@EnableConfigurationProperties
	public static class PropertyConfiguration {
		@Bean
		@RefreshScope
		public SlackbotProperties properties() {
			return new SlackbotProperties();
		}
	}

	@Configuration
	public static class Security extends WebSecurityConfigurerAdapter {

		@Override
		public void configure(WebSecurity web) throws Exception {
			web.ignoring().anyRequest();
		}
	}
}
