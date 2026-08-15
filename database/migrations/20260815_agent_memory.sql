CREATE TABLE IF NOT EXISTS "agent_memory" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT,
    "identity_key" TEXT NOT NULL,
    "role" TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
    "content" TEXT NOT NULL,
    "created_on" INTEGER NOT NULL,
    "expires_on" INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_memory_identity_created
  ON agent_memory (identity_key, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_agent_memory_expires ON agent_memory (expires_on);
