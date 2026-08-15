IMAGE_NAME ?= saturn
CONTAINER_NAME ?= saturn
DOCKER ?= docker
APP_DIR ?= /app
CONFIG_FILE ?= $(shell if [ -f "$(CURDIR)/config.toml" ]; then printf "%s" "$(CURDIR)/config.toml"; else printf "%s" "$(CURDIR)/config.example.toml"; fi)
DATABASE_DIR ?= $(CURDIR)/database
DATABASE_STEM ?= $(DATABASE_DIR)/database
DATABASE_FILE ?= $(DATABASE_STEM).mv.db
MIGRATIONS_DIR ?= $(DATABASE_DIR)/migrations
AGENT_API_KEY_ENV ?= SATURN_AGENT_API_KEY
ENV_FILE ?= $(CURDIR)/.env
STOP_TIMEOUT ?= 30
DATABASE_BACKUP_DIR ?= $(DATABASE_DIR)/backups

.PHONY: help build run start stop restart rm rmi clean rebuild logs shell ps status fresh-db db-check backup-db

help:
	@printf "%s\n" \
		"make build    - Build the Docker image" \
		"make run      - Recreate and run the container in detached mode" \
		"               Uses config.toml if it is a file, otherwise config.example.toml" \
		"               Loads .env when present; environment values override TOML agent settings" \
		"make start    - Start the existing container" \
		"make stop     - Stop the container if it exists" \
		"make restart  - Stop and run the container again" \
		"make rm       - Remove the container if it exists" \
		"make rmi      - Remove the Docker image if it exists" \
		"make clean    - Remove both container and image" \
		"make rebuild  - Clean, build, and run" \
		"make fresh-db - Recreate the local H2 database on next Saturn startup" \
		"make db-check - Stop Saturn and verify the H2 file exists" \
		"make backup-db - Stop Saturn and create a consistent H2 database backup" \
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
	@set -eu; \
	set --; \
	if [ -f "$(ENV_FILE)" ]; then \
		set -- --env-file "$(ENV_FILE)"; \
	else \
		set -- --env "$(AGENT_API_KEY_ENV)"; \
	fi; \
	$(DOCKER) run -d \
		--name $(CONTAINER_NAME) \
		"$$@" \
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
	rm -f $(DATABASE_FILE) $(DATABASE_FILE).trace.db
	@echo "H2 database will be created from src/main/resources/schema-h2.sql on next startup."

db-check: stop
	@test -f "$(DATABASE_FILE)" || { echo "Database not found: $(DATABASE_FILE)"; exit 1; }
	@test -s "$(DATABASE_FILE)"
	@echo "H2 database file exists: $(DATABASE_FILE)"

backup-db: stop
	@test -f "$(DATABASE_FILE)" || { echo "Database not found: $(DATABASE_FILE)"; exit 1; }
	@set -eu; \
		mkdir -p "$(DATABASE_BACKUP_DIR)"; \
		backup="$(DATABASE_BACKUP_DIR)/database-$$(date +%Y%m%d-%H%M%S).mv.db"; \
		cp "$(DATABASE_FILE)" "$$backup"; \
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
