-- Performance tuning for append-only ES_EVENT table
ALTER TABLE ES_EVENT SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_analyze_scale_factor = 0.02,
    fillfactor = 100
);
