# slack-cf-stats

A Spring Boot app to accept requests from a Slack slash command, and report the
statistics of a given CF foundation's usage (memory, apps, and instances per org).

## Basic Usage

**Note**: Because this app is also its own config server, you must use a java
buildpack with unlimited JCE. I've created one [here](https://github.com/jghiloni/java-buildpack.git).

```bash
$ mvn clean package
$ cf push slack-stats \
     -p target/slack-cf-stats-0.0.1-SNAPSHOT.jar \
     -m 512M \
     -b https://github.com/jghiloni/java-buildpack.git
     --no-start
$ cf se slack-stats KEYSTORE_PASSWORD ${your_keystore_password}
$ cf start slack-stats
```
