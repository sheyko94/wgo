ALTER TABLE observations ADD COLUMN requested_event_id UUID REFERENCES events(id);
