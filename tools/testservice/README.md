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

Build inside container:
```shell
docker build --platform linux/arm64 -t testservice_build_env -f testservice/Dockerfile .
docker create --name temp_container testservice_build_env
docker cp temp_container:/app/testservice/build/libs/testservice-0.0.1-SNAPSHOT-all.jar ./testservice/testservice-0.0.1-SNAPSHOT-all.jar
(optional) docker cp temp_container:/app/native/libs ./native/
docker rm temp_container
```

Run Ansible script with:

```shell
cd testservice/ansible
ansible-playbook -i hosts.ini site.yml --diff
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
