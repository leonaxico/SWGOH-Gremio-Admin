# SWGOH Gremio Admin

Territory Battle planner for a SWGOH guild. It pulls every member's roster
from SWGoH Comlink, resolves the **real** TB mission requirements from the
game data, and plans which squads each player should send to which mission
in a phase.

Java/Spring rewrite of an earlier Python/Streamlit prototype, split into
three containers.

## Architecture

```
comlink (official image)  <-- backend (Spring Boot REST API)  <-- frontend (Spring MVC + Thymeleaf)
```

| Module | Stack | Role |
|---|---|---|
| `backend` | Spring Boot, `RestClient` | Comlink integration, game-data catalog, optimizer, REST API |
| `frontend` | Spring MVC + Thymeleaf | Single-page UI, calls backend over REST |

Frontend and backend are deliberately decoupled: the frontend has its own
copies of the response DTOs (`frontend/.../dto/`) rather than sharing a
module with the backend. Either side can be redeployed or rewritten without
touching the other.

## Where the data comes from

All Comlink calls are `POST` with a `{"payload": {...}}` body.

### Guild and rosters (on every **Fetch**)

| Order | Call | Used for |
|---|---|---|
| 1 | `/player {allyCode}` | `guildId`, `guildName`, the seed player's roster |
| 2 | `/guild {guildId}` | `guild.member[].playerId` (member entries carry no GP/roster) |
| 3 | `/player {playerId}` × members | each member's roster |

How a player is mapped (`PlayerMapper`):

| Field | Source |
|---|---|
| Player GP | `profileStat[]` entry `STAT_GALACTIC_POWER_ACQUIRED_NAME` (string → number) |
| Unit id | `rosterUnit[].definitionId` before the `:` (`VADER:SEVEN_STAR` → `VADER`) |
| Stars | `currentRarity` (same as the `definitionId` suffix) |
| Gear | `currentTier` |
| Ship | `relic` is `null` |
| Relic | `relic.currentTier - 2`, only for G13 characters (raw 1 = locked, 2 = R0, 9 = R7) |
| Name, categories | `units` game data (below) |

Comlink's player payload has **no per-unit power** (`unitStat` is always
`null`), so units are ranked by a strength score built from gear, relic
and stars (`PlayerMapper#strength`). It's a ranking proxy, not in-game GP.

### TB requirements (game data, loaded once at backend startup)

`GameDataService` calls `/metadata` for the current versions, then
`/data` with `items` = `category` (bit 0) + `territoryBattleDefinition`
(bit 29) + `units` (bit 37) + `campaign` (bit 38), then `/localization`
for readable names. Load takes ~15 s and is cached in memory until restart.

Every mission is resolved like this:

```
territoryBattleDefinition[tb].strikeZoneDefinition[]   (combat / fleet missions)
territoryBattleDefinition[tb].covertZoneDefinition[]   (special missions)
   └─ campaignElementIdentifier (campaign / map / node / difficulty / mission)
        └─ campaign[...].campaignNodeMission[].entryCategoryAllowed   ← the requirement
units[].categoryId                                                  ← what each unit counts as
category[] + localization                                           ← names / requirement text
```

`entryCategoryAllowed` fields used:

| Field | Meaning |
|---|---|
| `categoryId` | unit must have at least one (e.g. `profession_jedi`, `alignment_light`) |
| `excludeCategoryId` | unit must have none |
| `mandatoryRosterUnit` | units that must all be in the squad (e.g. `MACEWINDU`, `KITFISTO`) |
| `commanderCategoryId` | fleet: capital ship must be from one of these |
| `minimumRequiredUnitQuantity` | squad size |
| `minimumUnitRarity` | star floor |
| `minimumRelicTier` | raw relic floor, same scale as rosters (7 = R5); implies G13 |

The phase comes from the zone id (`tb3_mixed_phase01_conflict01_strike01`
→ `phase_1`) and the territory name from the linked conflict zone. All 5 TBs
currently in the game are supported: Rise of the Empire, both Geonosis TBs
and both Hoth TBs.

## How the optimizer plans a phase

In a TB every player can attempt every mission once, but a unit used in one
mission is spent for the rest of the phase. `OptimizerService`:

1. **Dependency:** for each mission, counts the players who could field a
   valid squad with their whole roster (*Eligible*). Fewer eligible players
   means the guild depends more on each of them.
2. **Planning:** for each player, walks missions scarcest-first and builds
   each squad from the units still available. It prefers units that fit the
   fewest of the player's remaining missions, then the strongest, so rare
   units aren't spent on missions anything could fill. The result is the
   *Planned* count and each player's squads.

Missions nobody can attempt show 0 eligible (highlighted red in the UI).

## Run it

Needs Docker. On macOS without Docker Desktop, Homebrew + Colima works:

```bash
brew install colima docker docker-compose
colima start --cpu 4 --memory 6
docker compose up --build -d
```

- Frontend UI: `http://localhost:8080`
- Backend API: `http://localhost:8081`
- Comlink: `http://localhost:3000`

The first build downloads Maven dependencies inside the build containers and
takes a few minutes. Comlink needs `APP_NAME` (already set in
`docker-compose.yml`).

### Use the UI

1. Enter an ally code (defaults to `191483497`) and click **Fetch**. This
   takes ~15 s for a 50-member guild.
2. Pick a TB and phase, then click **Run optimization**.
3. Each mission shows its requirement and eligible/planned counts. Expand
   **Planned squads** to see who sends what.

### Names in another language

Unit, territory and requirement names come from Comlink's localization
bundle. Set `COMLINK_LANGUAGE` on the backend (`ENG_US` default; `SPA_XM`,
`POR_BR`, `FRE_FR`, `GER_DE`, ...).

### Without Docker for the apps (local dev loop)

Needs JDK 17+.

```bash
docker run -e APP_NAME=swgoh-gremio-admin -p 3000:3000 ghcr.io/swgoh-utils/swgoh-comlink:latest
mvn -pl backend -am spring-boot:run
mvn -pl frontend -am spring-boot:run
```

Both modules default to `localhost` URLs, so no environment variables are
needed outside Docker.

## REST API

| Method | Path | |
|---|---|---|
| `GET` | `/api/tbs` | TBs with their phases (from game data) |
| `POST` | `/api/guild/fetch?allyCode=…` | load the guild and cache it in memory |
| `GET` | `/api/guild/current` | the cached guild |
| `POST` | `/api/optimize` `{"tbId":"t05D","phase":"phase_1"}` | plan a phase for the cached guild |

## Debugging Comlink responses

With Comlink running:

```bash
bash debug/comlink_dump.sh 191483497   # player → guild → member player, saved to debug/comlink/
bash debug/comlink_data_bits.sh        # maps each /data "items" bit to its collection
```

`debug/comlink/` is git-ignored. Game-data payloads are large (the
`campaign` and `units` collections are 50–90 MB each), so mind disk space.

## Known gaps / next steps

- **No persistence.** The guild and game data live in memory. Every
  **Fetch** re-pulls the whole guild (one `/player` call per member), and
  game data reloads on every backend restart. Add Postgres if this runs
  often against large guilds.
- **No real unit GP.** Comlink's raw rosters don't include it. Getting real
  numbers needs a stat calculator such as `swgoh-stats` next to Comlink.
- **Platoons and deployment aren't planned.** Only combat, special and fleet
  missions are.
- **Simplified squad rules.** Squads are filled to the minimum size.
  Bonus zones are always included even if not unlocked yet. `legendLimit` /
  `bigUnitLimit` aren't enforced. `matchType` is treated as "any category",
  which is what every live mission uses (`matchType: 2`).
- **Minimal frontend error handling.** A backend error surfaces as Spring's
  default error page.
