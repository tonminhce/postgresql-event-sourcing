-- Deduplicate snapshots if any older versions exist, keeping only the highest VERSION
DELETE FROM ES_AGGREGATE_SNAPSHOT s1
USING ES_AGGREGATE_SNAPSHOT s2
WHERE s1.AGGREGATE_ID = s2.AGGREGATE_ID
  AND s1.VERSION < s2.VERSION;

-- Replace composite primary key (AGGREGATE_ID, VERSION) with primary key on (AGGREGATE_ID)
ALTER TABLE ES_AGGREGATE_SNAPSHOT
  DROP CONSTRAINT IF EXISTS es_aggregate_snapshot_pkey;

ALTER TABLE ES_AGGREGATE_SNAPSHOT
  ADD CONSTRAINT es_aggregate_snapshot_pkey PRIMARY KEY (AGGREGATE_ID);
