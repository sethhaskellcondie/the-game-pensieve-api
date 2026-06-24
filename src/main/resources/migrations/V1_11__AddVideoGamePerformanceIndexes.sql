CREATE INDEX IF NOT EXISTS video_game_to_video_game_box_video_game_id ON video_game_to_video_game_box (video_game_id);
CREATE INDEX IF NOT EXISTS video_game_to_video_game_box_video_game_box_id ON video_game_to_video_game_box (video_game_box_id);
CREATE INDEX IF NOT EXISTS video_games_system_id ON video_games (system_id);
CREATE INDEX IF NOT EXISTS video_game_boxes_system_id ON video_game_boxes (system_id);

-- Undo
-- DROP INDEX video_game_to_video_game_box_video_game_id;
-- DROP INDEX video_game_to_video_game_box_video_game_box_id;
-- DROP INDEX video_games_system_id;
-- DROP INDEX video_game_boxes_system_id;
-- DELETE FROM flyway_schema_history WHERE version = '1.11';
