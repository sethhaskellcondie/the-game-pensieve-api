ALTER TABLE custom_field_values ADD COLUMN IF NOT EXISTS value_option_id INTEGER REFERENCES custom_field_options (id);

-- Undo
-- ALTER TABLE custom_field_values DROP COLUMN IF EXISTS value_option_id;
-- DELETE FROM flyway_schema_history WHERE version = '1.10';
