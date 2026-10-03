#!/bin/sh
# Rodado pelo GitHub Actions como usuário `deploy`, via SSH: a chave só pode executar
# este script (forced command em ~deploy/.ssh/authorized_keys). Mudanças no site do
# Nginx (deploy/nginx/) não são aplicadas aqui: recarregue o Nginx como root.
set -eu
cd /opt/padelmatch
git fetch -q origin main
git checkout -q -B main origin/main
docker compose -f docker-compose.prod.yml up -d --build
docker image prune -f >/dev/null
