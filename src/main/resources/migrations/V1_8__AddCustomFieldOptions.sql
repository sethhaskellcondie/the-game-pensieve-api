CREATE TABLE custom_field_options
(
    id              SERIAL PRIMARY KEY,
    custom_field_id INTEGER NOT NULL REFERENCES custom_fields (id),
    name            VARCHAR NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT false,
    deleted         BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT unique_option_per_field UNIQUE (custom_field_id, name)
);
