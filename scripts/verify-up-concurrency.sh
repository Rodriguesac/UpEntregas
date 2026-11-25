#!/usr/bin/env bash
# Real competing PostgreSQL sessions; refuses any non-loopback database host.
set -euo pipefail
[[ "${PGHOST:-}" == "localhost" || "${PGHOST:-}" == "127.0.0.1" ]] || { echo 'Local test database required'; exit 1; }
: "${PGPASSWORD:?Disposable database password required}"
psql -X -v ON_ERROR_STOP=1 -d postgres -c 'CREATE DATABASE up_concurrency_test' >/dev/null
export PGDATABASE=up_concurrency_test
psql -X -v ON_ERROR_STOP=1 -f supabase/up-independent-schema.sql >/dev/null
psql -X -v ON_ERROR_STOP=1 -f supabase/up-driver-operations.sql >/dev/null
output=$(mktemp -d)
trap 'rm -rf "$output"' EXIT
for scenario in same_job same_courier; do
 psql -X -v ON_ERROR_STOP=1 -v scenario="$scenario" <<'SQL' >/dev/null
INSERT INTO up_couriers(firebase_uid,display_name,state,online)
VALUES (:'scenario'||'-1','Isolated courier 1','APPROVED',true),
       (:'scenario'||'-2','Isolated courier 2','APPROVED',true);
INSERT INTO up_jobs(merchant_order_id,merchant_order_code,source_event_id,source_payload_sha256,status)
SELECT gen_random_uuid(), :'scenario'||'-'||i,gen_random_uuid(),repeat('a',64),'OFFERED'
FROM generate_series(1,2) i;
INSERT INTO up_dispatch_offers(job_id,courier_id,expires_at)
SELECT j.id,c.id,now()+interval '5 minutes' FROM up_jobs j JOIN up_couriers c
ON (:'scenario'='same_job' AND j.merchant_order_code='same_job-1' AND c.firebase_uid LIKE 'same_job-%')
OR (:'scenario'='same_courier' AND j.merchant_order_code LIKE 'same_courier-%' AND c.firebase_uid='same_courier-1');
SQL
 race() {
  psql -X -v ON_ERROR_STOP=1 -v scenario="$scenario" -v racer="$1" <<'SQL'
SET statement_timeout='8s';
SET ROLE service_role;
BEGIN;
SELECT pg_sleep(0.2);
SELECT * FROM up_driver_accept(
 (SELECT j.id FROM up_jobs j WHERE merchant_order_code=:'scenario'||'-'||CASE WHEN :'scenario'='same_job' THEN '1' ELSE :'racer' END),
 (SELECT c.id FROM up_couriers c WHERE firebase_uid=:'scenario'||'-'||CASE WHEN :'scenario'='same_courier' THEN '1' ELSE :'racer' END),
 (SELECT o.id FROM up_dispatch_offers o JOIN up_jobs j ON j.id=o.job_id JOIN up_couriers c ON c.id=o.courier_id
  WHERE j.merchant_order_code=:'scenario'||'-'||CASE WHEN :'scenario'='same_job' THEN '1' ELSE :'racer' END
    AND c.firebase_uid=:'scenario'||'-'||CASE WHEN :'scenario'='same_courier' THEN '1' ELSE :'racer' END)
);
SELECT pg_sleep(0.2);
COMMIT;
SQL
 }
 race 1 >"$output/one" 2>&1 & first=$!
 race 2 >"$output/two" 2>&1 & second=$!
 successes=0
 if wait "$first"; then successes=$((successes+1)); fi
 if wait "$second"; then successes=$((successes+1)); fi
 [[ "$successes" == 1 ]] || { cat "$output/one" "$output/two"; exit 1; }
 # A deadlock/timeout is NOT an acceptable loser. It must be a business conflict.
 if [[ "$scenario" == same_job ]]; then expected=job_unavailable; else expected=courier_busy; fi
 grep -q "$expected" "$output/one" "$output/two"
 count=$(psql -X -Atqc "select count(*) from up_jobs where merchant_order_code like '$scenario-%' and status='ACCEPTED'")
 [[ "$count" == 1 ]]
 events=$(psql -X -Atqc "select count(*) from up_job_events e join up_jobs j on j.id=e.job_id where j.merchant_order_code like '$scenario-%' and e.status='ACCEPTED'")
 [[ "$events" == 1 ]]
 echo "PASS simultaneous accept: $scenario (one winner, one business conflict, one event)"
done
psql -X -v ON_ERROR_STOP=1 <<'SQL'
UPDATE up_couriers SET online=false;
UPDATE up_dispatch_offers SET expires_at=now()-interval '1 hour';
DO $$
DECLARE chosen record; replay boolean;
BEGIN
 SELECT * INTO chosen FROM up_dispatch_offers WHERE status='ACCEPTED' LIMIT 1;
 SELECT already_applied INTO replay FROM up_driver_accept(chosen.job_id,chosen.courier_id,chosen.id);
 IF replay IS DISTINCT FROM true THEN RAISE EXCEPTION 'replay_not_idempotent'; END IF;
END $$;
SQL
echo 'PASS accepted retry after expiry and offline; no duplicate event'
