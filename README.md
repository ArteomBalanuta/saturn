## Saturn

**Saturn** is a moderator bot for [Hack.Chat](https://github.com/hack-chat).

It supports moderation commands, user utilities, room replicas, embedded H2 persistence, and Docker-based deployment.

---

## Requirements

- JDK 23+
- Docker (optional, for containerized runs)

Check your Java version:

```bash
java --version
```

---

## Quick Start

```bash
git clone https://github.com/ArteomBalanuta/saturn.git
cd saturn
cp config.example.toml config.toml
make fresh-db
./mvnw package
java -Dlog4j.configurationFile=./log4j2.xml -jar target/saturn.jar
```

---

## Configuration

Main runtime configuration lives in `config.toml`.

Start from the template:

```bash
cp config.example.toml config.toml
```

Example:

```toml
dbPath = "database/database"
wsUrl = "wss://hack.chat/chat-ws"
cmdPrefix = "*"
channel = "programming"
nick = "alphaBot"
trip = "YOUR_BOT_TRIP_HERE"
userTrips = ""
adminTrips = "YOUR_ADMIN_TRIP_HERE"
autoReconnect = true
healthCheckInterval = 5
autorunCommands = "replica lounge, say hello lads!!"

[agent]
enabled = false
# Set SATURN_AGENT_ENDPOINT in the environment before enabling the agent.
apiKeyEnv = "SATURN_AGENT_API_KEY"
timeoutSeconds = 30
maxCompletionTokens = 1024
thinkingEnabled = false
maxConcurrentRequests = 2
maxSteps = 5
maxToolCallsPerTurn = 4
toolTimeoutMillis = 10000
maxToolCalls = 4
memoryTurns = 30
memoryTtlHours = 168
creatorTrip = "595754"
ambientEnabled = false
ambientEveryMessages = 8
quietMinutes = 15
contextMessageLimit = 60
moderationEnabled = true
dynamicSqlEnabled = true
dynamicSqlMaxSqlChars = 4000
dynamicSqlMaxRows = 50
dynamicSqlMaxColumns = 32
dynamicSqlMaxCellChars = 2000
dynamicSqlMaxResultChars = 32000
dynamicSqlTimeoutMillis = 1000
```

Important fields:

- `dbPath`: H2 database stem; H2 stores data at `<dbPath>.mv.db`
- `cmdPrefix`: command prefix used in chat
- `channel`: room Saturn joins on startup
- `trip`: bot trip password
- `userTrips`: comma-separated trips with basic access
- `adminTrips`: comma-separated admin trips
- `autoReconnect`: enables health-check restart behavior
- `autorunCommands`: commands executed after startup
- `agent.endpoint`: TOML fallback for the OpenAI-compatible router base URL; Saturn calls `/v1/chat/completions`
- `SATURN_AGENT_ENDPOINT`: environment-first provider URL; use this instead of committing a deployed endpoint
- `agent.apiKeyEnv`: name of the environment variable containing an optional bearer token
- `SATURN_AGENT_API_KEY`: standard bearer-token environment variable
- `SATURN_AGENT_MODEL`: optional environment-first model override
- `agent.maxCompletionTokens`: provider-side output-token limit for each completion
- `agent.thinkingEnabled`: enables model thinking mode; disabled by default for predictable latency
- `agent.maxConcurrentRequests`: maximum accepted active and queued agent requests per engine
- `agent.maxSteps`: maximum model/tool loop iterations for one request
- `agent.maxToolCallsPerTurn`: total tool-call budget for one request; `agent.maxToolCalls` remains a compatible legacy alias
- `agent.toolTimeoutMillis`: deadline for one tool invocation unless that tool declares a shorter or longer override
- `agent.memoryTurns` and `agent.memoryTtlHours`: bounded embedded database conversation memory
- `agent.creatorTrip`: trusted creator identity; keep it authorized in `adminTrips` or the roles table
- `agent.ambientEnabled`: enables periodic participation in unaddressed public chat; defaults to `false`
- `agent.ambientEveryMessages`: when ambient mode is enabled, routes every Nth eligible message
- `agent.quietMinutes`: per-user, per-room ambient suppression after a polite quiet request
- `agent.contextMessageLimit`: recent public room messages automatically supplied to all public agent turns
- `agent.moderationEnabled`: enables deterministic spam and raid responses independently of ambient chat
- `agent.dynamicSqlEnabled`: enables admin-only schema inspection and generated read-only SQL
- `agent.dynamicSqlMax*`: bounds SQL length, rows, columns, cells, and serialized results
- `agent.dynamicSqlTimeoutMillis`: interrupts generated queries after the configured deadline

Environment variables take precedence over TOML for the provider, router limits, memory bounds, and
dynamic-SQL bounds that expose `SATURN_AGENT_*` overrides. Their names use the `SATURN_AGENT_` prefix
and upper snake case; for example `agent.maxSteps` becomes `SATURN_AGENT_MAX_STEPS`. Copy
[`.env.example`](.env.example) to the ignored `.env` file only when you want environment-specific
agent overrides. Participation and moderation thresholds are configured in `[agent]` TOML. The
endpoint selects the model by default, so `agent.model` may remain empty. Set `SATURN_AGENT_API_KEY`
only when the endpoint requires authentication. Keep the agent disabled until an explicit endpoint is
configured; use HTTPS or a trusted private network for all prompts and tool results.

For production, choose one of these supported paths:

- Keep the current real agent values in ignored `config.toml`; run `make run` or Compose without a
  `.env` file and Saturn uses those values unchanged.
- Create ignored `.env` from `.env.example`, replace its placeholders with the real endpoint and
  credentials, and set `SATURN_AGENT_ENABLED=true`; those environment values override TOML.

### Vaelen Agent

Maintainers should start with [`docs/AGENTIC_BEHAVIOR.md`](docs/AGENTIC_BEHAVIOR.md) for the
implementation-grounded behavior contract and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the
current package map and lifecycle. The section below summarizes operator-visible behavior and
deployment constraints.

```text
*l how many users are in the room right now?
@alphaBot can you check what sun discussed recently?
```

Vaelen answers `*l` and exact `@<bot-nick>` mentions immediately. Ambient participation is disabled
by default, so unaddressed public messages receive no agent response. If explicitly enabled, every
eighth eligible public message is evaluated by default. A polite request such as `please be silent`
produces no acknowledgement and suppresses ambient replies to that user in that room for 15 minutes.

The agent can inspect live users in any Saturn-managed room, retrieve bounded public message history
for a named user across all rooms or within one named room (up to 500 messages), and run named
read-only database queries.
By default, public direct, mention, and ambient turns automatically receive the latest 20 public
messages from their room; `agent.contextMessageLimit` can change that bound.
Informational Saturn commands are available to all agent callers. The configured creator, configured
admin trips, persisted admins, and persisted moderators receive moderation commands when their
invocation mode is eligible. Only a direct creator invocation receives permanent-ban and admin
commands. Recursive `l`, raw SQL commands, shutdown, unban-all, and unrelated admin commands are
never exposed.

Public conversation memory is shared by everyone in the same room, so another participant can
continue an earlier exchange. Each engine has one FIFO agent worker, so accepted work across its
rooms is serialized in submission order. Whispers use private per-user memory and are never added to
the public room session. Rooms remain separate, and memory expires according to the configured TTL.

New message audit rows carry an explicit `PUBLIC` or `WHISPER` visibility. Regular agent history
tools only read `PUBLIC` rows. Existing rows from before this migration remain unclassified and are
excluded because Saturn cannot safely infer whether they came from public chat or a whisper.

Configured admin trips and users with a persisted `ADMIN` role also receive a generated-SQL
fallback. The agent must inspect the schema first and may then run one bounded, AST-validated
`SELECT` on a dedicated read-only H2 connection. This admin capability can read every Saturn
application table and column, including cross-room messages, trip/hash identity data, mail, notes,
moderation data, command history, agent memory, whispers, and unclassified legacy message rows.
Database metadata tables, writes, schema changes, and administrative statements remain blocked.
Logs contain only a query fingerprint, duration, row count, and outcome; raw
generated SQL is not logged.

## Agentic Runtime Contract

The optional agent is a bounded orchestration layer, not an unrestricted chatbot or replacement for
Saturn's command, persistence, or moderation systems. An OpenAI-compatible provider proposes text or
structured tool calls; Saturn owns the immutable request context, tool definitions, authorization,
argument/result validation, ordering, timeouts, persistence, and final room output. The provider is
called at the configured endpoint's `/v1/chat/completions` path.

### Invocation modes and capabilities

Agent invocations are created in four modes:

- `DIRECT`: an explicit `*l <prompt>` command or whisper command; a user-visible answer is expected.
- `MENTION`: an exact public `@<bot-nick>` mention; a user-visible answer is expected.
- `AMBIENT`: an eligible sampled public message; no acknowledgement is required and silence is normal.
- `MODERATION`: a semantic moderation candidate; output is intentionally silent and the action path is
  restricted to approved moderation commands.

Contextual tools can read room users, retrieve bounded public message history, inspect the schema,
run bounded read-only SQL for authorized admins, and execute approved Saturn commands. Tool visibility
is calculated for the current caller and mode. Informational tools are broadly available; moderation
requires the configured creator/admin/persisted roles where eligible; permanent bans and admin commands
require a direct creator invocation. The agent cannot bypass `SaturnCommandGateway`, protected-principal
rules, or command authorization, and RPC/MCP/plugin/arbitrary skill execution is not part of this
in-process boundary.

### Safety, memory, evidence, and output

Every proposed call is checked against its contextual descriptor, JSON arguments, capability policy,
prerequisites, duplicate ledger, per-turn budgets, deadlines, and cancellation state before execution.
Failures, denials, invalid results, timeouts, and cancellations remain explicit failures; they are never
reported as successful actions. A request is limited by `agent.maxSteps`, `agent.maxToolCallsPerTurn`
(with legacy `agent.maxToolCalls` compatibility), per-tool/failure limits, prompt/output bounds, and
tool deadlines. Safe independent read-only calls may run in contiguous parallel segments, but barriers,
side effects, prerequisites, and resource conflicts remain ordered; this is not an unbounded loop or a
general dependency DAG.

`memoryKey` is both the session lock and durable-memory namespace. Public turns share ordered memory per
room; whispers use a private per-user namespace and are not added to the public session. H2 memory is
bounded by turns and TTL. Public room context and regular history tools use only classified `PUBLIC`
rows; `WHISPER` rows are private, and pre-migration unclassified rows are excluded from regular history
queries. Tool evidence records what was actually attempted and whether it succeeded, failed, or was
unavailable. Durable history is not mutated by provider-side trimming or projection, and non-silent
conversation persistence occurs only after final response validation.

`ToolResultMode` controls result visibility independently of whether the agent replies: `MODEL_DATA` is
sent to the model and may become reusable evidence; `ROOM_DELIVERY` is delivered to the room without a
normal model-data observation and is not reusable; `ROOM_DELIVERY_AND_MODEL_DATA` uses both immediate
channels but is not reusable cross-turn. `AgentResult.silent` intentionally suppresses conversational
output for ambient/moderation cases.
### Autonomous Moderation

With `agent.moderationEnabled = true`, Saturn watches bounded in-memory windows for message floods,
repeated text, join bursts, same-hash nickname variants, and suspicious nickname clusters. The
default escalation ceiling is warning, captcha, mute, kick, then shadow-ban. Autonomous permanent
bans do not exist; `ban` is available only through a direct creator invocation. Host, replica, creator,
and configured admin identities are excluded from automatic targeting.

Captcha is enabled after a detected raid and remains enabled until an authorized user runs
`*captcha off`. Every moderation count, window, and cooldown is documented in
`config.example.toml` and can be tuned under `[agent]`.

`config.example.toml` is tracked in git. Your local `config.toml` is intentionally ignored.

---

## Local Development

### Create a Fresh Database

```bash
make fresh-db
```

This stops the Docker container and removes `database/database.mv.db`. Saturn recreates its H2
schema automatically on the next startup.

### Build

Linux/macOS:

```bash
./mvnw package
```

Windows:

```powershell
mvnw.cmd package
```

The image build copies `VERSION` into the build context so the shaded jar and runtime `/VERSION`
resource report the same release artifact version. The Dockerfile currently builds with JDK 24 while the
Maven source/target level is Java 23; local development requires JDK 23+ and the container supplies its
own JDK.

Run the focused test class while iterating, then the normal quality gates:

```bash
./mvnw -Dtest=<TestClass> test
./mvnw spotless:check
./mvnw test
./mvnw package
```

`package` produces the shaded `target/saturn.jar`. The Maven project remains `1.0-SNAPSHOT`; the
release artifact version is read from the root [`VERSION`](VERSION) file and is embedded in the jar.
The repository's release process should update `VERSION` deliberately; do not infer a release
version from the Maven snapshot string.

### Run

```bash
java -Dlog4j.configurationFile=./log4j2.xml -jar target/saturn.jar
```

When running outside Docker, keep these files available together:

- `target/saturn.jar`
- `config.toml`
- `config.example.toml` (optional, as a template)
- `log4j2.xml`
- `database/database.mv.db`

---

## Docker

The easiest Docker workflow uses the included `Makefile`.

### First Run

```bash
# Optional environment-override mode:
# cp .env.example .env
make fresh-db
make rebuild
make logs
```

### Common Commands

```bash
make build      # build the image
make run        # recreate and run the container
make rebuild    # clean, build, and run
make stop       # stop the container
make rm         # remove the container
make rmi        # remove the image
make clean      # remove both container and image
make db-check   # stop Saturn and verify the H2 database file
make backup-db  # stop Saturn and copy the H2 database file
make logs       # follow container logs
make shell      # open a shell in the container
make status     # show container status
make help       # list all targets
```

`make run` and `make rebuild` mount:

- `./config.toml` to `/app/config.toml`
- `./database` to `/app/database`

They preserve the ignored `config.toml` as the runtime fallback and, when present, pass the ignored
`.env` file to Docker with `--env-file`. This lets production deployments retain their current TOML
endpoint/model settings or override them through `SATURN_AGENT_*` without putting real values in
tracked files. Compose forwards the same optional variables and also works with no `.env` file,
using `config.toml` fallbacks.

Container removal is graceful: Saturn receives up to 30 seconds to close its WebSocket, replicas,
agent executor, and H2 connections before Docker removes it.

Do not open the bind-mounted database concurrently from another process while Saturn is running.
H2 stores data in a `.mv.db` file; `AUTO_SERVER=TRUE` coordinates local JVMs, but one Saturn
container should own a mounted database. Use `make db-check` or `make backup-db`; both
stop Saturn before touching the database. Run `make start` afterward to resume the existing
container.

### Manual Docker Commands

```bash
docker build -t saturn .

docker run -d \
  --name saturn \
  -v $(pwd)/config.toml:/app/config.toml \
  -v $(pwd)/database:/app/database \
  saturn
```

Add `--env-file .env` before the volume flags only when you intentionally use the environment-
override mode.

### Docker Compose

```bash
docker compose up --build -d
```

If you use the local bind-mounted setup, the container will use your local `config.toml` and `database/`.

---

## Agent Tool SDK

Agent tools expose a contextual `AgentToolDescriptor` contract. Each descriptor documents the
tool's label, category, access level, side effect, result delivery mode, usage guidance, examples,
capabilities, prerequisites, and JSON parameter schema. The registry publishes this contract in
the provider definitions, and the runtime prompt treats it as authoritative over persona prose.

The provider may emit multiple tool calls in one response. Saturn remains sequential-first:
`run_command`, moderation, room delivery, and dependency chains execute in provider order. Only
contiguous, independent tools marked both read-only and idempotent may run concurrently; their
observations still return to the provider in original call order. This reduces provider round trips
for compound read requests without reordering Saturn commands. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full lifecycle and extension guide.

Existing tools remain compatible: tools that do not override `descriptor(context)` receive safe
read-only defaults from `AgentTool`. Add an explicit descriptor for new tools, especially when a
tool changes the room, moderates users, writes persistence, or requires another tool first.

Agent-facing instructions and tool copy are externalized under `src/main/resources/agent/`; Java
code supplies only runtime values and orchestration.

## Project Notes

- H2 is used for embedded persistence. On first startup Saturn migrates a sibling legacy
  `database.db` SQLite file to `database.mv.db` and archives the source as `database.db.bak`.
- Startup adds the message-visibility column and matching agent query indexes to legacy databases.
- The local database is not recreated automatically unless you run `make fresh-db`.
- Saturn creates the embedded H2 schema at startup; the mounted `database/` directory holds the active file.

---

## Have Fun
