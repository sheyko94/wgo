CREATE TABLE events (
	id UUID PRIMARY KEY,
	title TEXT NOT NULL CHECK (length(btrim(title)) > 0),
	latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
	longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
	started_at TIMESTAMP WITH TIME ZONE NOT NULL,
	last_observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT events_time_order CHECK (last_observed_at >= started_at),
	CONSTRAINT events_update_order CHECK (updated_at >= created_at)
);

CREATE TABLE observations (
	id UUID PRIMARY KEY,
	text TEXT NOT NULL CHECK (length(btrim(text)) > 0),
	latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
	longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
	observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
	event_id UUID REFERENCES events(id),
	processing_status TEXT NOT NULL DEFAULT 'PENDING',
	created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	processed_at TIMESTAMP WITH TIME ZONE,
	CONSTRAINT observations_status CHECK (processing_status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
	CONSTRAINT observations_completion CHECK (
		(processing_status = 'PROCESSED' AND event_id IS NOT NULL AND processed_at IS NOT NULL)
		OR (processing_status <> 'PROCESSED' AND processed_at IS NULL)
	),
	CONSTRAINT observations_processed_order CHECK (processed_at >= created_at)
);
