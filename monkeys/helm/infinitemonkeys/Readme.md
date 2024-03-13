# infinitemonkeys - load testing tool for Wire

This Helm chart provides:

- A group of StatefulSets deploying one monkeyController and N monkeyServers
- Integration Prometheus-Operator via PodMonitors and a Grafana Dashboard

## Architecture

The Controller will instruct the Servers what to do, via files/config.json.
The config schema can be [viewed here](https://github.com/wireapp/kalium/blob/develop/monkeys/schema.json).
Currently, config options for a single backend are exposed via values.yaml, but it is possible
to add several backends and let the Servers send messages between them. In that case, it's recommended to edit
the config.json file directly prior (re)deployment.


## Log output

The Servers echo all logging to stdout. The Controller writes to a local log within it's pod in `/wire/cryptobox/monkeys.log`.


## JVM Optimizations

We've added a block in `values.yaml` exposing various Java options in order to reduce memory footprint of the monkey servers.
At this point, the amount of RAM needed by each monkey is down about 50% compared to non-optimized defaults.


## Limitations

The total number of all users as defined via `monkeyController.userCount` (or directly in config.json backend sections) needs
to be matched by `monkeyServer.replicaCount`, as N+1, since there's always one additional team owner monkey.


## References

1. - [Infinite Monkeys documentation](https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/878182477/Infinite+Monkeys+IM)
2. - [JVM tuning in K8s](https://www.padok.fr/en/blog/jvm-oom)
