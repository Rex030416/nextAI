CREATE TABLE chat_sessions (
  id UUID PRIMARY KEY,
  owner_id TEXT NOT NULL,
  document_id BIGINT NOT NULL,
  summary TEXT NOT NULL DEFAULT '',
  summarized_message_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (id, owner_id),
  FOREIGN KEY (document_id, owner_id)
    REFERENCES documents (id, owner_id) ON DELETE CASCADE
);

CREATE INDEX chat_sessions_owner_document_idx ON chat_sessions (owner_id, document_id, updated_at DESC);

CREATE TABLE chat_messages (
  id BIGSERIAL PRIMARY KEY,
  session_id UUID NOT NULL,
  owner_id TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (session_id, owner_id)
    REFERENCES chat_sessions (id, owner_id) ON DELETE CASCADE
);

CREATE INDEX chat_messages_session_idx ON chat_messages (session_id, id);

CREATE TABLE user_memories (
  id BIGSERIAL PRIMARY KEY,
  owner_id TEXT NOT NULL,
  content TEXT NOT NULL,
  embedding VECTOR(1536) NOT NULL,
  source_session_id UUID REFERENCES chat_sessions (id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_accessed_at TIMESTAMPTZ
);

CREATE INDEX user_memories_owner_idx ON user_memories (owner_id, created_at DESC);
CREATE INDEX user_memories_embedding_hnsw_idx
  ON user_memories USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
