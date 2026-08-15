IMAGE_NAME ?= saturn
CONTAINER_NAME ?= saturn
DOCKER ?= docker
APP_DIR ?= /app
CONFIG_FILE ?= $(shell if [ -f "$(CURDIR)/config.toml" ]; then printf "%s" "$(CURDIR)/config.toml"; else printf "%s" "$(CURDIR)/config.example.toml"; fi)
DATABASE_DIR ?= $(CURDIR)/database
DATABASE_FILE ?= $(DATABASE_DIR)/database.db
MIGRATIONS_DIR ?= $(DATABASE_DIR)/migrations
AGENT_API_KEY_ENV ?= SATURN_AGENT_API_KEY
STOP_TIMEOUT ?= 30
DATABASE_BACKUP_DIR ?= $(DATABASE_DIR)/backups

.PHONY: help build run start stop restart rm rmi clean rebuild logs shell ps status fresh-db db-check backup-db

help:
	@printf "%s\n" \
		"make build    - Build the Docker image" \
		"make run      - Recreate and run the container in detached mode" \
		"               Uses config.toml if it is a file, otherwise config.example.toml" \
		"make start    - Start the existing container" \
		"make stop     - Stop the container if it exists" \
		"make restart  - Stop and run the container again" \
		"make rm       - Remove the container if it exists" \
		"make rmi      - Remove the Docker image if it exists" \
		"make clean    - Remove both container and image" \
		"make rebuild  - Clean, build, and run" \
		"make fresh-db - Recreate the local SQLite database from schema.sql" \
		"make db-check - Stop Saturn and run SQLite integrity checks" \
		"make backup-db - Stop Saturn and create a consistent database backup" \
		"make logs     - Tail container logs" \
		"make shell    - Open a shell inside the running container" \
		"make ps       - Show matching containers" \
		"make status   - Show container status"

build:
	$(DOCKER) build -t $(IMAGE_NAME) .

run: rm
	@if [ -e "$(CURDIR)/config.toml" ] && [ ! -f "$(CURDIR)/config.toml" ]; then \
		echo "config.toml exists but is not a file: $(CURDIR)/config.toml"; \
		echo "Using $(CONFIG_FILE) instead. Remove or rename that directory if it is accidental."; \
	fi
	$(DOCKER) run -d \
		--name $(CONTAINER_NAME) \
		--env $(AGENT_API_KEY_ENV) \
		-v "$(CONFIG_FILE):$(APP_DIR)/config.toml" \
		-v "$(DATABASE_DIR):$(APP_DIR)/database" \
		$(IMAGE_NAME)

start:
	$(DOCKER) start $(CONTAINER_NAME)

stop:
	@if ! command -v "$(DOCKER)" >/dev/null 2>&1; then \
		exit 0; \
	elif $(DOCKER) container inspect "$(CONTAINER_NAME)" >/dev/null 2>&1; then \
		$(DOCKER) stop --timeout $(STOP_TIMEOUT) "$(CONTAINER_NAME)"; \
	elif $(DOCKER) info >/dev/null 2>&1; then \
		:; \
	else \
		echo "Cannot verify whether container $(CONTAINER_NAME) is stopped." >&2; \
		exit 1; \
	fi

restart: run

rm: stop
	@if ! command -v "$(DOCKER)" >/dev/null 2>&1; then \
		exit 0; \
	elif $(DOCKER) container inspect "$(CONTAINER_NAME)" >/dev/null 2>&1; then \
		$(DOCKER) rm "$(CONTAINER_NAME)"; \
	elif $(DOCKER) info >/dev/null 2>&1; then \
		:; \
	else \
		echo "Cannot verify whether container $(CONTAINER_NAME) was removed." >&2; \
		exit 1; \
	fi

rmi:
	@if ! command -v "$(DOCKER)" >/dev/null 2>&1; then \
		exit 0; \
	elif $(DOCKER) image inspect "$(IMAGE_NAME)" >/dev/null 2>&1; then \
		$(DOCKER) rmi "$(IMAGE_NAME)"; \
	elif $(DOCKER) info >/dev/null 2>&1; then \
		:; \
	else \
		echo "Cannot verify whether image $(IMAGE_NAME) was removed." >&2; \
		exit 1; \
	fi

clean: rm rmi

rebuild: clean build run

fresh-db: stop
	mkdir -p $(DATABASE_DIR)
	rm -f $(DATABASE_FILE) $(DATABASE_FILE)-wal $(DATABASE_FILE)-shm $(DATABASE_FILE)-journal
	sqlite3 $(DATABASE_FILE) < schema.sql
	@if [ -d "$(MIGRATIONS_DIR)" ]; then \
		set -eu; \
		for migration in $$(find "$(MIGRATIONS_DIR)" -type f -name '*.sql' | sort); do \
			echo "Applying $$migration"; \
			sqlite3 $(DATABASE_FILE) < "$$migration"; \
		done; \
	fi

db-check: stop
	@test -f "$(DATABASE_FILE)" || { echo "Database not found: $(DATABASE_FILE)"; exit 1; }
	@set -eu; \
		result=$$(sqlite3 "$(DATABASE_FILE)" "PRAGMA integrity_check; PRAGMA foreign_key_check;"); \
		printf "%s\n" "$$result"; \
		test "$$result" = "ok"

backup-db: stop
	@test -f "$(DATABASE_FILE)" || { echo "Database not found: $(DATABASE_FILE)"; exit 1; }
	@set -eu; \
		mkdir -p "$(DATABASE_BACKUP_DIR)"; \
		backup="$(DATABASE_BACKUP_DIR)/database-$$(date +%Y%m%d-%H%M%S).db"; \
		sqlite3 "$(DATABASE_FILE)" ".backup '$$backup'"; \
		test -f "$$backup"; \
		echo "Database backup created: $$backup"

logs:
	$(DOCKER) logs -f $(CONTAINER_NAME)

shell:
	$(DOCKER) exec -it $(CONTAINER_NAME) sh

ps:
	$(DOCKER) ps -a --filter "name=$(CONTAINER_NAME)"

status:
	$(DOCKER) inspect -f '{{.Name}} {{.State.Status}}' $(CONTAINER_NAME)
