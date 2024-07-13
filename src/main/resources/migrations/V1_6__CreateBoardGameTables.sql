CREATE TABLE IF NOT EXISTS board_games (
    id SERIAL PRIMARY KEY,
    title VARCHAR NOT NULL,
    created_at timestamp with time zone NOT NULL default now(),
    updated_at timestamp with time zone NOT NULL default now(),
    deleted_at timestamp with time zone NULL
);

CREATE TABLE IF NOT EXISTS board_game_boxes (
    id SERIAL PRIMARY KEY,
    title VARCHAR NOT NULL,
    is_expansion BOOLEAN DEFAULT false,
    is_stand_alone BOOLEAN DEFAULT false,
    base_set_id INTEGER REFERENCES board_game_boxes (id) NULL,
    board_game_id INTEGER REFERENCES board_games (id) NOT NULL,
    created_at timestamp with time zone NOT NULL default now(),
    updated_at timestamp with time zone NOT NULL default now(),
    deleted_at timestamp with time zone NULL
);

-- Undo
-- DROP TABLE board_games;
-- DROP TABLE board_game_boxes;
-- DELETE FROM flyway_schema_history WHERE version = '1.6';
