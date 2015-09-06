package com.ecsteam.slack;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.ecsteam.slack.CloudFoundryStatsService.EnvironmentStats;
import com.ecsteam.slack.CloudFoundryStatsService.OrganizationStats;

@RestController
public class SlackChatController {
	@Autowired
	private CloudFoundryStatsService service;

	@Autowired
	private SlackbotProperties slackbotProperties;

	private static final Log logger = LogFactory.getLog(SlackChatController.class);

	@RequestMapping(value = "/docommand", method = RequestMethod.POST)
	public String doCommand(@RequestParam("token") String token, @RequestParam("command") String command,
			@RequestParam("text") String commandText) throws Exception {
		String requiredToken = service.decrypt(slackbotProperties.getBotToken());

		if (!token.equals(requiredToken)) {
			throw new AccessDeniedException("Unacceptable token");
		}

		String[] words = commandText.split(" ");

		String subcommand = words[0].toLowerCase(Locale.ENGLISH);
		switch (subcommand) {
		case "envs":
			return getEnvs();
		case "stats":
			return sendStats(words[1]);
		default:
			return new StringBuilder("Usage: ").append(command).append(" envs|stats &lt;env-name&gt;").toString();
		}
	}

	private String getEnvs() {
		List<String> envs = service.getAvailableEnvironments();

		StringBuilder text = new StringBuilder("```\nAvailable Environments\n=====================\n");
		for (String env : envs) {
			text.append(env).append('\n');
		}

		text.append("```\n");

		return text.toString();
	}

	private String getMessage(String env) throws Exception {
		EnvironmentStats stats = service.getStats(env);
		String displayName = service.getDisplayName(env);

		StringBuilder text = new StringBuilder("```\nCurrent Usage Stats for ").append(displayName).append("\n");
		String header1 = "Organization |  Running  | Total | Used  | Allocated |  Quota  | Used %";
		String header2 = "             | Instances | Apps  | RAM   |    RAM    |         |";

		text.append(header1).append("\n");
		text.append(header2).append("\n");
		text.append(repeat("=", header1.length())).append("\n");
		for (OrganizationStats org : stats.getOrgs()) {
			int percent = Math.round(100 * (1.0f * org.getMemUsed()) / org.getQuota());

			text.append(padRight(org.getName(), "Organization".length())).append(" | ");
			text.append(padRight(String.valueOf(org.getRunningInstances()), "Instances".length())).append(" | ");
			text.append(padRight(String.valueOf(org.getTotalApps()), "Total".length())).append(" | ");
			text.append(padRight(numToSize(org.getMemUsed()), "Used".length())).append(" | ");
			text.append(padRight(numToSize(org.getMemAllocated()), "Allocated".length())).append(" | ");
			text.append(padRight(numToSize(org.getQuota()), " Quota".length())).append("  | ");
			text.append(String.format("%d%%", percent));
			text.append("\n");
		}

		text.append("```\n");
		return text.toString();
	}

	private String repeat(String toRepeat, int n) {
		return new String(new char[n]).replace("\0", toRepeat);
	}

	private String sendStats(final String env) throws Exception {
		final String webhookUrl = service.decrypt(slackbotProperties.getWebhookUrl());
		new Thread(new Runnable() {
			@Override
			public void run() {
				logger.debug("Send thread started");
				try {
					String text = getMessage(env);

					HttpHeaders headers = new HttpHeaders();
					headers.setContentType(MediaType.APPLICATION_JSON);
					RequestEntity<Map<String, String>> request = new RequestEntity<Map<String, String>>(
							Collections.singletonMap("text", text), headers, HttpMethod.POST, new URI(webhookUrl));

					logger.debug(text);
					(new RestTemplate()).exchange(request, String.class);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable t) {
					throw new RuntimeException(t);
				}
			}
		}).run();

		return "Your message will appear in the channel in a few seconds. Be patient, it can take a while.";
	}

	private String padRight(String s, int width) {
		return String.format("%1$-" + width + "s", s);
	}

	private String numToSize(int mb) {
		if (mb < 1024) {
			return String.format("%d M", mb);
		} else {
			float gb = Math.round(1000 * (mb / 1024.0f)) / 1000.0f;
			return String.format("%.1f G", gb);
		}
	}
}
