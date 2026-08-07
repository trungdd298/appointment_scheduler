# Deployment / Containerization

## Full stack via Docker Compose

```bash
docker compose --profile full up -d --build   # Postgres + seeded backend + frontend
docker compose --profile full down            # stop everything
```

`backend` and `frontend` are defined in `docker-compose.yml` but tagged
`profiles: ["full"]`, so they're excluded from a plain `docker compose up
-d` — that command still starts *only* Postgres, matching every existing
CLAUDE.md/README instruction that assumes the backend/frontend run
directly on the host. The `full` profile is purely additive.

## How the containers are wired

- **`backend`** (root `Dockerfile`, multi-stage: `eclipse-temurin:17-jdk`
  builds the `bootJar`, `eclipse-temurin:17-jre` runs it) connects to
  Postgres over the **internal Docker network** —
  `DB_URL=jdbc:postgresql://postgres:5432/appointment_scheduler` (the
  Compose service name, not `localhost`, and the container's internal port
  5432, not the host-published 5433). `SPRING_PROFILES_ACTIVE=dev` is
  baked into the compose environment so it seeds automatically — no manual
  env var needed for this path, unlike the direct-`bootRun` path.
- **`frontend`** (`frontend/Dockerfile`, multi-stage: `node:20-alpine`
  builds the Vite bundle, `nginx:alpine` serves the static output per
  `frontend/nginx.conf`) bakes `VITE_API_BASE_URL` in **at build time**
  (`ARG`/`ENV`, defaulting to `http://localhost:8080`) — Vite env vars are
  compiled into the JS bundle, not readable at container runtime. This
  must be a **browser-reachable** address, not the Docker service name
  `backend`: the built JS runs in the user's browser and calls this URL
  directly, so it needs the host's *published* backend port.
- CORS: the frontend container publishes port 80 as host port 5173
  (`"5173:80"`), so the browser's origin is still `http://localhost:5173`
  — matching `WebConfig`'s existing CORS allowlist unchanged.

## Verification

Both the plain `docker compose up -d` (Postgres-only) and the
`--profile full` full-stack path were run and checked directly:
`docker compose ps` confirmed the default path starts only Postgres;
`curl localhost:8080/dealerships` and a full browser walkthrough (via the
Browser tool at `http://localhost:5173`) confirmed the containerized
backend/frontend behave identically to the host-run versions — same seed
data, same availability results, same CORS behavior.
