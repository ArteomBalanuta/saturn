#!/bin/bash
set -euo pipefail

PROJECT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DATABASE_DIR="$PROJECT_DIR/database"
DATABASE_FILE="$DATABASE_DIR/database.db"

echo "Creating SQLite database: $DATABASE_FILE"
mkdir -p "$DATABASE_DIR"
sqlite3 "$DATABASE_FILE" < "$PROJECT_DIR/schema.sql"
