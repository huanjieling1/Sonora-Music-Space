ALTER TABLE music_catalog_track
    ADD COLUMN graph_projected_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER embedding_dimensions,
    ADD COLUMN embedding_content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER graph_projected_hash,
    ADD KEY idx_music_catalog_graph_projection (graph_projected_hash),
    ADD KEY idx_music_catalog_embedding_projection (embedding_content_hash);

