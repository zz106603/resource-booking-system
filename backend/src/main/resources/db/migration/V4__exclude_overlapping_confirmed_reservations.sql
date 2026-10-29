-- Stop writers while checking and enforcing the new invariant. Do not repair history implicitly.
LOCK TABLE reservations IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM reservations a JOIN reservations b
          ON a.id < b.id AND a.space_id = b.space_id
         AND a.status = 'CONFIRMED' AND b.status = 'CONFIRMED'
         AND a.start_at < b.end_at AND b.start_at < a.end_at
    ) THEN
        RAISE EXCEPTION 'Existing confirmed reservations overlap; resolve the data before applying V4';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

ALTER TABLE reservations ADD CONSTRAINT reservations_confirmed_no_overlap
    EXCLUDE USING gist (
        space_id public.gist_int8_ops WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status = 'CONFIRMED');
