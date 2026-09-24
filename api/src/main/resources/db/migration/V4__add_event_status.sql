ALTER TABLE events ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE events ADD CONSTRAINT events_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'));
-- The lifecycle worker reconciles existing events with the configured activity window.
UPDATE events SET status = 'INACTIVE' WHERE last_observed_at < CURRENT_TIMESTAMP - INTERVAL '24 hours';
UPDATE events SET projection_pending = TRUE;
