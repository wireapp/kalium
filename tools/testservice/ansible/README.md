# Ansible playbook for kalium testservice

Deploys `quay.io/wire/testservice` as a Docker container on `kalium.qa.zinfra.io` (office Proxmox, QA Lab), fronted by nginx.

## Usage

```bash
# Provision / update the host
ansible-playbook -i hosts.ini kalium.yml --diff -u firstname.lastname

# Deploy a new container version (on the host)
ssh firstname.lastname@kalium.qa.zinfra.io
cd /opt/kalium-testservice
sudo docker compose down; sudo docker compose pull; sudo docker compose up -d --remove-orphans
```

## What it does

- Installs Docker CE and nginx
- Deploys `docker-compose.yml` and `config.yml` to `/opt/kalium-testservice/`
- Runs `quay.io/wire/testservice:latest` with port 8080 (app) bound to localhost (metrics port 8081 stays internal to the container)
- Configures nginx to reverse-proxy port 80 to the app

## Layout on host

```
/opt/kalium-testservice/
  docker-compose.yml  # container definition
  config.yml          # dropwizard config (mounted into container)
  logs/               # application logs (writable by container)
```
