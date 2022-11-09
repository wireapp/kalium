# testservice

Provides a REST service for sending test messages

## How to run

In root folder execute:

```shell
./gradlew :testservice:run
```

Create fat jar for standalone execution:

```shell
./gradlew :testservice:jar
```

Run fat jar:

```shell
java -jar testservice/build/libs/testservice-*-all.jar server testservice/config.yml
```

## Installation

### Linux

Create log directory and give it the right user permissions:
```
mkdir -p /var/log/kalium-testservice
chmod <user>:<user> /var/log/kalium-testservice
```

Install systemd service as user:
```
mkdir -p ${HOME}/.config/systemd/user/
```

Create file `${HOME}/.config/systemd/user/kalium-testservice.service` with following content:
```
[Unit]
Description=kalium-testservice
After=network.target
[Service]
LimitNOFILE=infinity
LimitNPROC=infinity
LimitCORE=infinity
TimeoutStartSec=8
WorkingDirectory=${WORKSPACE}
Environment="PATH=/usr/bin:/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin"
ExecStart=java -Djava.library.path=${WORKSPACE}/native/libs/ -jar ${WORKSPACE}/testservice/build/libs/testservice-0.0.1-SNAPSHOT-all.jar server ${WORKSPACE}/testservice/config.yml
Restart=always
[Install]
WantedBy=default.target
```

Restart service:
```
systemctl --user daemon-reload
systemctl --user restart kalium-testservice
```

## Random number generation

On Linux systems with no peripherals (like mouse, etc.) attached the entropy generation
for random numbers is too low and kalium process might hang until enough entropy is generated.

As a workaround it is recommended to install **haveged**.

```shell
apt-get install haveged
systemctl enable haveged
systemctl start haveged
```
