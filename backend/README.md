# mirror-backend

A small custom Kotlin backend (Ktor) providing real account sign-up/login for the Mirror
Android app, in place of a third-party auth provider. Pure Kotlin, no Firebase.

## What it does

- `POST /auth/signup` `{ "email": "...", "password": "..." }` → creates an account, returns a JWT
- `POST /auth/login` `{ "email": "...", "password": "..." }` → verifies credentials, returns a JWT
- `GET /auth/me` (header `Authorization: Bearer <token>`) → returns the signed-in user

Passwords are hashed with bcrypt (never stored in plain text). Accounts are stored in a local
SQLite file (`mirror-backend.db`) via Exposed. Sessions are JWTs valid for 30 days.

## Run it locally

Requires a JDK (any JDK 17+; this repo doesn't pin a Gradle toolchain, so it just uses
whatever `java` is on your PATH / `JAVA_HOME`).

```
./gradlew run
```

The server listens on `0.0.0.0:8080` - reachable from other devices on the same Wi-Fi
network (like a phone running the Mirror app), not just `localhost`. Find this machine's
LAN IP (Windows: `ipconfig`, look for the Wi-Fi adapter's IPv4 address) and point the
Android app's `NetworkConfig.BASE_URL` at `http://<that-ip>:8080/`.

A `jwt-secret.txt` file is generated on first run and reused after that, so restarting the
server doesn't log everyone out. It's git-ignored - don't commit it.

**Local-only limitation:** this only works while your PC is on and the phone is on the same
Wi-Fi network. Deploy it (below) for access from anywhere.

## Deploy (e.g. to Render)

This repo includes a `Dockerfile`, which Render (and most PaaS hosts) can build and run
directly:

1. Push this folder to its own GitHub repo.
2. On Render: **New → Web Service**, connect the repo, environment = **Docker**.
3. Set an environment variable `JWT_SECRET` to some long random string (Render's
   filesystem is ephemeral, so without this every redeploy would invalidate all sessions -
   see `Security.kt`).
4. Deploy. Render assigns a public URL like `https://mirror-backend-xyz.onrender.com` and
   sets `$PORT` itself - the app already reads both.
5. Give that URL to whoever's building the Android app's `NetworkConfig.BASE_URL`.

**Known limitation on a free Render plan:** the SQLite database file lives on the
container's local disk, which is wiped on every redeploy/restart unless you add a paid
persistent disk. Fine for early testing; for real production use, swap `Database.kt` to a
managed Postgres instance (Render offers one) - the Exposed table definitions barely change.
