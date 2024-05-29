CREATE INDEX IF NOT EXISTS customfieldvalue_customfieldid_entityid ON custom_field_values (custom_field_id, entity_id);

-- Undo
-- DROP INDEX customfieldvalue_customfieldid_entityid;
-- DELETE FROM flyway_schema_history WHERE version = '1.4';