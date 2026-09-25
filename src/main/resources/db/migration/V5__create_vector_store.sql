-- Spring AI PGVectorStore creates this table automatically, but Flyway manages
-- the extension and index post-ingestion. The vector dimension is environment-specific;
-- Spring AI creates the table on first use. The HNSW index should be created after
-- initial bulk ingestion in production.

-- Post-ingestion index (run manually or via a separate migration after first load):
-- CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);
