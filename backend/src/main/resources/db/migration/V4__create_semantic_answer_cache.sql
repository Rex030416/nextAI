CREATE TABLE semantic_answer_cache (
  id BIGSERIAL PRIMARY KEY,
  owner_id TEXT NOT NULL,
  document_id BIGINT NOT NULL,
  question TEXT NOT NULL,
  question_embedding VECTOR(1536) NOT NULL,
  answer TEXT NOT NULL,
  sources JSONB NOT NULL,
  hit_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL,
  last_hit_at TIMESTAMPTZ,
  FOREIGN KEY (document_id, owner_id)
    REFERENCES documents (id, owner_id) ON DELETE CASCADE
);

CREATE INDEX semantic_answer_cache_scope_idx
  ON semantic_answer_cache (owner_id, document_id, expires_at DESC);
CREATE INDEX semantic_answer_cache_embedding_hnsw_idx
  ON semantic_answer_cache USING hnsw (question_embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
