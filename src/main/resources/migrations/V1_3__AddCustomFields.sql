CREATE TABLE IF NOT EXISTS entities (
    key VARCHAR NOT NULL
);

INSERT INTO entities(key) VALUES ('system');
INSERT INTO entities(key) VALUES ('toy');

CREATE TABLE IF NOT EXISTS custom_fields (
    id SERIAL PRIMARY KEY,
    name VARCHAR NOT NULL,
    custom_field_type VARCHAR NOT NULL -- This will be an enum enforced by the Repository
);

CREATE TABLE IF NOT EXISTS entity_to_custom_fields (
    entity_name VARCHAR NOT NULL,
    custom_fields_id INTEGER REFERENCES custom_fields (id)
);

CREATE TABLE IF NOT EXISTS custom_field_values (
    custom_fields_id INTEGER REFERENCES  custom_fields (id),
    entity_id INTEGER NOT NULL, -- This will be a foreign key enforced by the Respository
    entity_name VARCHAR NOT NULL,
    combo_id VARCHAR NOT NULL, -- This will be formatted as (entity_name - entity_id) combining the two will make the join easier
    value VARCHAR
);

-- Undo
-- DROP TABLE custom_fields;
-- DROP TABLE Toys;
-- DELETE FROM flyway_schema_history WHERE version = '1.3';