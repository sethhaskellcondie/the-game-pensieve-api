ALTER TABLE custom_fields ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE custom_field_options ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0;

-- Undo
-- ALTER TABLE custom_fields DROP COLUMN IF EXISTS display_order;
-- ALTER TABLE custom_field_options DROP COLUMN IF EXISTS display_order;
-- DELETE FROM flyway_schema_history WHERE version = '1.9';
