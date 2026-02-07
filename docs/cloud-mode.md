# Cloud Mode (Public Server)

This lets ProjectPilot work from anywhere with a single public URL.

## 1) Server Requirements
- Java 21 (JDK recommended)
- Postgres (recommended) or SQLite (local-only/testing)
- Public domain with HTTPS (reverse proxy)

## 2) Database (Postgres)
Example commands:
```sql
CREATE DATABASE projectpilot;
CREATE USER projectpilot WITH PASSWORD 'strong-password';
GRANT ALL PRIVILEGES ON DATABASE projectpilot TO projectpilot;
```

Environment variables:
```bash
export PROJECTPILOT_DB_URL="jdbc:postgresql://127.0.0.1:5432/projectpilot"
export PROJECTPILOT_DB_USER="projectpilot"
export PROJECTPILOT_DB_PASSWORD="strong-password"
```

## 3) Run the Cloud Server
From the repo root:
```bash
./scripts/run-cloud-server.sh
```

Optional server settings:
```bash
export PROJECTPILOT_HTTP_PORT=8090
export PROJECTPILOT_WS_PORT=8091
export PROJECTPILOT_RATE_LIMIT_RPM=1200
export PROJECTPILOT_PUBLIC_URL="http://projectpilot.duckdns.org:8090"
```

## 4) Reverse Proxy + TLS (Recommended)
Expose HTTPS/WSS on port 443 and keep 8090/8091 private.

Example Caddyfile:
```text
pilot.yourdomain.com {
  reverse_proxy /ws* 127.0.0.1:8091
  reverse_proxy /api/* 127.0.0.1:8090
  reverse_proxy / 127.0.0.1:8090
}
```

Example Nginx (with simple rate limit):
```nginx
limit_req_zone $binary_remote_addr zone=pp_limit:10m rate=20r/s;

server {
  listen 443 ssl;
  server_name pilot.yourdomain.com;

  location /ws {
    proxy_pass http://127.0.0.1:8091;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "Upgrade";
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  }

  location / {
    limit_req zone=pp_limit burst=40 nodelay;
    proxy_pass http://127.0.0.1:8090;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  }
}
```

## 5) Built-in HTTPS/WSS (Optional)
If you want ProjectPilot itself to serve HTTPS/WSS (no reverse proxy),
set these environment variables and open the HTTPS/WSS ports in your firewall:

```bash
export PROJECTPILOT_HTTPS_PORT=8443
export PROJECTPILOT_WS_PORT=8444
export PROJECTPILOT_KEYSTORE_PATH="/path/to/projectpilot.p12"
export PROJECTPILOT_KEYSTORE_PASSWORD="changeit"
export PROJECTPILOT_KEYSTORE_TYPE="PKCS12"
```

Generate a self-signed cert for testing:
```bash
keytool -genkeypair -alias projectpilot -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore projectpilot.p12 -storepass changeit \
  -dname "CN=projectpilot.duckdns.org" -validity 3650
```

For production, use a real certificate (Let's Encrypt) or a reverse proxy.

## 6) Client Setup (Cloud Mode)
- Open ProjectPilot
- Choose **Cloud** and enter: `http://projectpilot.duckdns.org:8090`
- If you use HTTPS on a separate WS port, append `?ws=PORT`:
  `https://pilot.yourdomain.com:8443?ws=8444`
- Log in with the same user accounts stored in the server DB

## Dev-only: Trust self-signed certs
If you use a self-signed cert, set this on the client machine:
```bash
export PROJECTPILOT_TRUST_ALL_SSL=true
```

## Optional: DuckDNS for a free hostname
DuckDNS gives you a free URL like `projectpilot.duckdns.org`.
If you use it, update the hostname regularly (or enable auto-update in the app).

## 7) Windows auto-start (optional)
Install a scheduled task to start the cloud server on boot:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\install-cloud-startup.ps1
```
Remove it:
```powershell
powershell -ExecutionPolicy Bypass -File scripts\uninstall-cloud-startup.ps1
```

## 8) Backups
Postgres backup:
```bash
pg_dump -U projectpilot -Fc projectpilot > /backups/projectpilot-$(date +%F).dump
```

SQLite backup (if used):
```bash
cp ~/.projectpilot/projectpilot.db /backups/projectpilot-$(date +%F).db
```
