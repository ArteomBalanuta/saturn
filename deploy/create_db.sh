#!/bin/bash
set -euo pipefail

PROJECT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DATABASE_DIR="$PROJECT_DIR/database"
DATABASE_STEM="$DATABASE_DIR/database"

echo "Preparing H2 database directory for: $DATABASE_STEM.mv.db"
mkdir -p "$DATABASE_DIR"
echo "Saturn creates the H2 schema at application startup."
