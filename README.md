# SWGOH Gremio Admin

Territory Battle deployment optimizer for a SWGOH guild: pulls guild member
rosters via SWGoH Comlink, scores each player's dependency on required
units, and greedily assigns players to TB combat mission slots.

Java/Spring rewrite of an earlier Python/Streamlit prototype — same
scoring and greedy-assignment logic, now split into three containers.

## Architecture

```
comlink (official image)  <-- backend (Spring Boot REST API)  <-- frontend (Spring MVC + Thymeleaf)
```

| Module | Stack | Role |
|---|---|---|
| `backend` | Spring Boot, `RestClient` | Comlink integration, scoring, greedy optimizer, REST API |
| `frontend` | Spring MVC + Thymeleaf | Single-page test UI, calls backend over REST |

Frontend and backend are deliberately decoupled: the frontend has its own
copies of the response DTOs (`frontend/.../dto/`) rather than sharing a
module with backend's. That costs a little duplication but means either
side can be redeployed/rewritten (e.g. swapping Thymeleaf for Vaadin later)
without touching the other.

## Known gaps / next steps

- **No caching layer yet.** Every "Fetch guild roster" click re-pulls the
  whole guild (one `/player` call per member). Fine for testing; add
  Postgres before running this against a large guild repeatedly.
- **Verify the data shape before trusting scores.** Comlink's field name
  for a unit's computed power has drifted across versions. `PlayerMapper`
  tries a few likely paths and falls back to a gear/relic-based proxy so
  it never silently breaks — but confirm the real field name against your
  own instance (see "Verify the data shape" below) before trusting the
  numbers for real TB planning.
- **TB requirements are hand-config, not auto-pulled.** `TbRegistry` ships
  one example TB with made-up thresholds. Comlink doesn't expose a clean
  "this mission needs exactly these characters" list, so fill in your
  guild's actual current requirements there.
- **Single-pass, single-phase optimizer.** A player is assumed to deploy
  to one mission per phase. Extend `OptimizerService` if your TB allows
  redeploying.
- **Minimal error handling** on the frontend — a backend error currently
  surfaces as Spring's default error page, not a friendly message.
- If Thymeleaf's `th:text="${guild.name}"`-style record property access
  doesn't resolve cleanly in your Spring Boot version, the fix is
  mechanical: turn the frontend DTO records into plain classes with
  getters.

## 1. Run Comlink + the stack

```bash
docker compose up --build
```

- Comlink: `http://localhost:3000`
- Backend API: `http://localhost:8081`
- Frontend UI: `http://localhost:8080`

First build pulls Maven dependencies inside the build containers, so it
needs network access and will take a few minutes.

## 2. Verify the data shape (do this once, before trusting results)

With Comlink up (`docker compose up comlink` or the full stack), hit it
directly to see what your instance actually returns for a unit:

```bash
curl -X POST http://localhost:3000/player \
  -H "Content-Type: application/json" \
  -d '{"payload":{"allyCode":"191483497"}}' | jq '.rosterUnit[0]'
```

Check where the power value actually lives and adjust
`backend/src/main/java/com/swgoh/admin/backend/loader/PlayerMapper.java`
(`extractPower`) if it's not one of the paths already tried.

## 3. Fill in your TB's real requirements

Edit `TbRegistry` in
`backend/src/main/java/com/swgoh/admin/backend/config/TbRegistry.java` —
replace the example `TBDefinition` with your TB's actual phases and
mission requirements (tags, GP thresholds, or specific named units for
special missions).

## 4. Use the UI

1. Open `http://localhost:8080`
2. Enter an ally code (defaults to `191483497`) → **Fetch**
3. Pick a TB and type a phase (e.g. `phase_1`) → **Run optimization**
4. Review assignments and any unfilled slots

## Running without Docker (local dev loop)

```bash
# terminal 1
docker run -p 3000:3000 ghcr.io/swgoh-utils/swgoh-comlink:latest

# terminal 2
mvn -pl backend -am spring-boot:run

# terminal 3
mvn -pl frontend -am spring-boot:run
```

Both modules default to `localhost` URLs (`comlink.url` and `backend.url`
in each `application.yml`), so no environment variables are needed outside
Docker.

## Pushing this to your repo

I don't have access to your local machine or network from here, so I
can't run these commands for you — but the files above are everything
`git add` needs. Copy this project into
`/Users/leonaxico/development/SWGOH`, then from that directory:

```bash
git init
git add .
git commit -m "Java/Spring rewrite: Comlink backend + Thymeleaf frontend"
git branch -M main
git remote add origin git@github.com:leonaxico/SWGOH-Gremio-Admin.git
git push -u origin main
```

One correction to the sequence you had: use `git add .` (not just
`README.md`) so the actual project — `pom.xml`, both modules,
`docker-compose.yml` — gets committed, not just this file.
