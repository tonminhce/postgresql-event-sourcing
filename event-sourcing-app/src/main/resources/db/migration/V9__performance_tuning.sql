-- Performance tuning for append-only ES_EVENT table
ALTER TABLE ES_EVENT SET (
    autovacuum_vacuum_insert_scale_factor = 0.05,
    autovacuum_vacuum_insert_threshold = 10000,
    autovacuum_analyze_scale_factor = 0.02,
    fillfactor = 100
);

