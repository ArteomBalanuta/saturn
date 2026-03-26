IMAGE_NAME ?= saturn
CONTAINER_NAME ?= saturn
APP_DIR ?= /app
CONFIG_FILE ?= $(CURDIR)/config.toml
DATABASE_DIR ?= $(CURDIR)/database
DATABASE_FILE ?= $(DATABASE_DIR)/database.db
MIGRATIONS_DIR ?= $(DATABASE_DIR)/migrations

.PHONY: help build run start stop restart rm rmi clean rebuild logs shell ps status fresh-db

help:
	@printf "%s\n" \
		"make build    - Build the Docker image" \
		"make run      - Recreate and run the container in detached mode" \
		"make start    - Start the existing container" \
		"make stop     - Stop the container if it exists" \
		"make restart  - Stop and run the container again" \
		"make rm       - Remove the container if it exists" \
		"make rmi      - Remove the Docker image if it exists" \
		"make clean    - Remove both container and image" \
		"make rebuild  - Clean, build, and run" \
		"make fresh-db - Recreate the local SQLite database from schema.sql" \
		"make logs     - Tail container logs" \
		"make shell    - Open a shell inside the running container" \
		"make ps       - Show matching containers" \
		"make status   - Show container status"

build:
	docker build -t $(IMAGE_NAME) .

run: rm
	docker run -d \
		--name $(CONTAINER_NAME) \
		-v $(CONFIG_FILE):$(APP_DIR)/config.toml \
		-v $(DATABASE_DIR):$(APP_DIR)/database \
		$(IMAGE_NAME)

start:
	docker start $(CONTAINER_NAME)

stop:
	-docker stop $(CONTAINER_NAME)

restart: stop run

rm:
	-docker rm -f $(CONTAINER_NAME)

rmi:
	-docker rmi $(IMAGE_NAME)

clean: rm rmi

rebuild: clean build run

fresh-db:
	mkdir -p $(DATABASE_DIR)
	rm -f $(DATABASE_FILE)
	sqlite3 $(DATABASE_FILE) < schema.sql
	@if [ -d "$(MIGRATIONS_DIR)" ]; then \
		for migration in $$(find "$(MIGRATIONS_DIR)" -type f -name '*.sql' | sort); do \
			echo "Applying $$migration"; \
			sqlite3 $(DATABASE_FILE) < "$$migration"; \
		done; \
	fi

logs:
	docker logs -f $(CONTAINER_NAME)

shell:
	docker exec -it $(CONTAINER_NAME) sh

ps:
	docker ps -a --filter "name=$(CONTAINER_NAME)"

status:
	docker inspect -f '{{.Name}} {{.State.Status}}' $(CONTAINER_NAME)
