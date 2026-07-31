CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
  id BIGSERIAL PRIMARY KEY,
  owner_id TEXT NOT NULL,
  original_filename TEXT NOT NULL,
  storage_path TEXT NOT NULL,
  content_sha256 CHAR(64) NOT NULL,
  embedding_model TEXT NOT NULL,
  embedding_dimensions SMALLINT NOT NULL DEFAULT 1536,
  status TEXT NOT NULL DEFAULT 'uploaded'
    CHECK (status IN ('uploaded', 'indexing', 'ready', 'failed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  indexed_at TIMESTAMPTZ,
  UNIQUE (id, owner_id),
  UNIQUE (owner_id, content_sha256)
);

CREATE TABLE document_chunks (
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  owner_id TEXT NOT NULL,
  document_id BIGINT NOT NULL,
  chunk_index INTEGER NOT NULL,
  page_number INTEGER,
  section_title TEXT,
  content TEXT NOT NULL,
  keyword_terms TEXT[] NOT NULL DEFAULT '{}',
  embedding VECTOR(1536) NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (owner_id, id),
  FOREIGN KEY (document_id, owner_id)
    REFERENCES documents (id, owner_id) ON DELETE CASCADE,
  UNIQUE (owner_id, document_id, chunk_index)
) PARTITION BY HASH (owner_id);

DO $$
DECLARE
  partition_index INTEGER;
  partition_name TEXT;
BEGIN
  FOR partition_index IN 0..31 LOOP
    partition_name := format('document_chunks_p%s', partition_index);
    EXECUTE format(
      'CREATE TABLE %I PARTITION OF document_chunks FOR VALUES WITH (MODULUS 32, REMAINDER %s)',
      partition_name, partition_index
    );
    EXECUTE format('CREATE INDEX %I ON %I (document_id)', format('%s_document_id_idx', partition_name), partition_name);
    EXECUTE format(
      'CREATE INDEX %I ON %I USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)',
      format('%s_embedding_hnsw_idx', partition_name), partition_name
    );
    EXECUTE format('CREATE INDEX %I ON %I USING gin (keyword_terms)', format('%s_keyword_terms_gin_idx', partition_name), partition_name);
  END LOOP;
END $$;
