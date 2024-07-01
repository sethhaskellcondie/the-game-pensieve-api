CREATE TABLE IF NOT EXISTS video_games (
    id SERIAL PRIMARY KEY,
    title VARCHAR NOT NULL,
    system_id INTEGER REFERENCES systems (id),
    created_at timestamp with time zone NOT NULL default now(),
    updated_at timestamp with time zone NOT NULL default now(),
    deleted_at timestamp with time zone NULL
);

CREATE TABLE IF NOT EXISTS video_game_boxes (
    id SERIAL PRIMARY KEY,
    title VARCHAR NOT NULL,
    is_physical BOOLEAN DEFAULT false,
    is_collection BOOLEAN DEFAULT false,
    created_at timestamp with time zone NOT NULL default now(),
    updated_at timestamp with time zone NOT NULL default now(),
    deleted_at timestamp with time zone NULL
);

CREATE TABLE IF NOT EXISTS video_game_to_video_game_box (
    video_game_id INTEGER REFERENCES video_games (id),
    video_game_box_id INTEGER REFERENCES video_game_boxes (id)
);

-- Undo
-- DROP TABLE video_games;
-- DROP TABLE video_game_boxes;
-- DROP TABLE video_game_to_video_game_box;
-- DELETE FROM flyway_schema_history WHERE version = '1.5';
