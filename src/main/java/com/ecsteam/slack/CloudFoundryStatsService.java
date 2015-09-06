package com.ecsteam.slack;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import lombok.Data;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.domain.ApplicationStats;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudOrganization;
import org.cloudfoundry.client.lib.domain.InstanceState;
import org.cloudfoundry.client.lib.domain.InstanceStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.ecsteam.slack.SlackbotProperties.CloudFoundryEnvironment;
import com.ecsteam.slack.SlackbotProperties.CloudFoundryProperties;

@Service
public class CloudFoundryStatsService {
	@Autowired
	private SlackbotProperties slackBotProperties;
	
	@Autowired(required = false)
	private TextEncryptorLocator locator;

	public List<String> getAvailableEnvironments() {
		List<String> envs = new LinkedList<>();
		CloudFoundryProperties properties = slackBotProperties.getCloudfoundry();
		for (Map.Entry<String, CloudFoundryEnvironment> entry : properties.getEnvironments().entrySet()) {
			envs.add(String.format("%s (%s)", entry.getKey(), entry.getValue().getDisplayName()));
		}

		Collections.sort(envs);

		return envs;
	}

	public String getDisplayName(String environment) {
		CloudFoundryProperties properties = slackBotProperties.getCloudfoundry();

		CloudFoundryEnvironment envInfo = properties.getEnvironments().get(environment);
		return envInfo.getDisplayName();
	}

	public EnvironmentStats getStats(String environment) throws Exception {
		CloudFoundryProperties properties = slackBotProperties.getCloudfoundry();

		List<OrganizationStats> orgStats = new LinkedList<>();

		CloudFoundryEnvironment envInfo = properties.getEnvironments().get(environment);
		URL apiUrl = new URL(decrypt(envInfo.getUrl()));

		CloudCredentials creds = new CloudCredentials(decrypt(envInfo.getAdminUser()), decrypt(envInfo.getAdminPassword()));
		CloudFoundryClient ops = new CloudFoundryClient(creds, apiUrl, true);

		List<CloudOrganization> orgs = ops.getOrganizations();
		for (CloudOrganization partialOrg : orgs) {
			CloudOrganization org = ops.getOrgByName(partialOrg.getName(), true);
			orgStats.add(organizationStats(org, ops));
		}

		EnvironmentStats stat = new EnvironmentStats();
		stat.setOrgs(orgStats);

		return stat;
	}
	
	public String decrypt(String possiblyEncrypted) {
		if (possiblyEncrypted.startsWith("{cipher}")) {
			if (locator != null) {
				TextEncryptor encryptor = locator.locate(Collections.emptyMap());

				possiblyEncrypted = possiblyEncrypted.substring("{cipher}".length());

				return encryptor.decrypt(possiblyEncrypted);
			}
		}
		
		return possiblyEncrypted;
	}

	private OrganizationStats organizationStats(CloudOrganization org, CloudFoundryClient client) {
		OrganizationStats orgStats = new OrganizationStats();
		orgStats.setName(org.getName());
		orgStats.setQuota((int) org.getQuota().getMemoryLimit());
		orgStats.setMemAllocated(client.getOrganizationMemoryUsage(org));

		int numApps = 0;
		int numInstances = 0;
		int memUsed = 0;
		List<CloudApplication> apps = client.getApplications();
		if (!CollectionUtils.isEmpty(apps)) {
			numApps = apps.size();
			for (CloudApplication app : apps) {
				ApplicationStats appStats = client.getApplicationStats(app.getName());
				List<InstanceStats> instStats = appStats.getRecords();
				for (InstanceStats instStat : instStats) {
					if (instStat.getState() == InstanceState.RUNNING) {
						++numInstances;
						memUsed += instStat.getUsage().getMem() / 1048576;
					}
				}
			}
		}

		orgStats.setMemUsed(memUsed);
		orgStats.setTotalApps(numApps);
		orgStats.setRunningInstances(numInstances);

		return orgStats;
	}

	@Data
	static class EnvironmentStats {
		private Collection<OrganizationStats> orgs;
	}

	@Data
	static class OrganizationStats {
		private String name;

		private int quota;

		private int memUsed;

		private int memAllocated;

		private int totalApps;

		private int runningInstances;
	}
}
