-- Management → Travel: trips, map places (planned/visited), optional photos per place.

CREATE TABLE management_travel_trips (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id),
    title           VARCHAR(500)  NOT NULL,
    summary         TEXT          NOT NULL DEFAULT '',
    start_date      DATE          NOT NULL,
    end_date        DATE,
    status          VARCHAR(32)   NOT NULL DEFAULT 'PLANNING',
    color_hex       VARCHAR(7),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_management_travel_trips_status CHECK (status IN ('PLANNING', 'ACTIVE', 'COMPLETED'))
);

CREATE INDEX idx_management_travel_trips_owner_start ON management_travel_trips (owner_user_id, start_date DESC);

CREATE TABLE management_travel_places (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT        NOT NULL REFERENCES management_travel_trips (id) ON DELETE CASCADE,
    name            VARCHAR(500)  NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    address         TEXT,
    place_status    VARCHAR(32)   NOT NULL DEFAULT 'PLANNED',
    visit_date      DATE,
    notes           TEXT          NOT NULL DEFAULT '',
    sort_order      INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_management_travel_places_status CHECK (place_status IN ('PLANNED', 'VISITED'))
);

CREATE INDEX idx_management_travel_places_trip ON management_travel_places (trip_id);
CREATE INDEX idx_management_travel_places_visit ON management_travel_places (visit_date);

CREATE TABLE management_travel_place_photos (
    id                 BIGSERIAL PRIMARY KEY,
    place_id           BIGINT        NOT NULL REFERENCES management_travel_places (id) ON DELETE CASCADE,
    storage_key        TEXT          NOT NULL,
    original_filename  TEXT          NOT NULL,
    content_type       TEXT,
    size_bytes         BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_management_travel_photos_place ON management_travel_place_photos (place_id);
