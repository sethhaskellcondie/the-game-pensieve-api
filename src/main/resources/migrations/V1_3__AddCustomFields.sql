CREATE TABLE IF NOT EXISTS custom_fields (
    id SERIAL PRIMARY KEY,
    name VARCHAR NOT NULL,
    custom_field_type VARCHAR NOT NULL, -- This will be an enum enforced by the Repository
    enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS entity_to_custom_fields (
    entity_key VARCHAR NOT NULL,
    custom_fields_id INTEGER REFERENCES custom_fields (id),
    enabled BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS custom_field_values (
    custom_fields_id INTEGER REFERENCES  custom_fields (id),
    entity_id INTEGER NOT NULL, -- This will be a foreign key enforced by the Repository
    entity_key VARCHAR NOT NULL,
    value VARCHAR
);

-- Undo
-- DROP TABLE custom_fields;
-- DROP TABLE entity_to_custom_fields;
-- DROP TABLE custom_field_values;
-- DELETE FROM flyway_schema_history WHERE version = '1.3';