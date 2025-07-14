package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.ErrorLogs;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalCatastrophe;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CustomFieldRepository {
    private final JdbcTemplate jdbcTemplate;
    private final Logger logger = LoggerFactory.getLogger(CustomFieldRepository.class);
    private final RowMapper<CustomField> rowMapper = (resultSet, rowNumber) ->
            new CustomField(
                    resultSet.getInt("id"),
                    resultSet.getString("name"),
                    resultSet.getString("type"),
                    resultSet.getString("entity_key")
            );

    public CustomFieldRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomField insertCustomField(CustomFieldRequestDto customField) {
        customFieldDbValidation(customField);
        final String sql = """
                			INSERT INTO custom_fields(name, type, entity_key) VALUES (?, ?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, customField.name());
                    ps.setString(2, customField.type());
                    ps.setString(3, customField.entityKey());
                    return ps;
                },
                keyHolder
        );
        final Integer generatedId = (Integer) keyHolder.getKeys().get("id");

        try {
            return getById(generatedId);
        } catch (ExceptionResourceNotFound | NullPointerException e) {
            logger.error(ErrorLogs.InsertThenRetrieveError("custom_fields", generatedId));
            throw new ExceptionInternalCatastrophe("custom_fields", generatedId, e);
        }
    }

    public CustomField getById(int id) {
        final String sql = "SELECT * FROM custom_fields WHERE id = ? AND deleted = false";
        CustomField customField;
        try {
            customField = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{id}, //args to bind to the sql ?
                    new int[]{Types.BIGINT}, //the types of the objects to bind to the sql
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionResourceNotFound("custom_fields", id, exception);
        }
        return customField;
    }

    public CustomField getDeletedById(int id) {
        final String sql = "SELECT * FROM custom_fields WHERE id = ? AND deleted = true";
        CustomField customField;
        try {
            customField = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{id}, //args to bind to the sql ?
                    new int[]{Types.BIGINT}, //the types of the objects to bind to the sql
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionResourceNotFound("custom_fields (include deleted)", id, exception);
        }
        return customField;
    }

    public CustomField getByKeyAndName(String entityKey, String customFieldName) {
        final String sql = "SELECT * FROM custom_fields WHERE entity_key = ? AND name = ? AND deleted = false";
        CustomField customField;
        try {
            customField = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{entityKey, customFieldName}, //args to bind to the sql ?
                    new int[]{Types.VARCHAR, Types.VARCHAR}, //the types of the objects to bind to the sql
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionResourceNotFound("Custom Field (deleted = false) not found with given entity key and name. entity_key: " + entityKey
                    + " name: " + customFieldName + ".", exception);
        }
        return customField;
    }

    public List<CustomField> getAllCustomFields() {
        final String sql = "SELECT * FROM custom_fields";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<CustomField> getAllByKey(String entityKey) {
        final String sql = "SELECT * FROM custom_fields WHERE entity_key = ? ;";
        return jdbcTemplate.query(sql, rowMapper, entityKey);
    }

    public Map<String, String> getCustomFieldsAsFilterFields(String entityKey) {
        List<CustomField> customFields = getAllByKey(entityKey);

        Map<String, String> customFieldNameToType = new LinkedHashMap<>();
        for (CustomField customField : customFields) {
            customFieldNameToType.put(customField.name(), customField.type());
        }
        return customFieldNameToType;
    }

    public CustomField updateName(int id, String newName) {
        final String sql = """
                			UPDATE custom_fields SET name = ? WHERE id = ?;
                """;
        jdbcTemplate.update(sql, newName, id);
        return getById(id);
    }

    public void deleteById(int id) {
        final String sql = """
                			UPDATE custom_fields SET deleted = true WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", "custom_fields", id);
        }
    }

    private void customFieldDbValidation(CustomFieldRequestDto customField) {
        ExceptionFailedDbValidation exception = new ExceptionFailedDbValidation();
        if (!CustomField.getAllCustomFieldTypes().contains(customField.type())) {
            exception.addException("Custom Field Type: '" + customField.type() + "' is not a valid type. " +
                    "Valid types include [" + String.join(", ", CustomField.getAllCustomFieldTypes()) + "]");
        }
        if (!Keychain.getAllKeys().contains(customField.entityKey())) {
            exception.addException("Custom Field Entity Key: '" + customField.entityKey() + "' is not a valid entity key. " +
                    "Valid keys include [" + String.join(", ", Keychain.getAllKeys()) + "]");
        }
        if (!exception.getExceptions().isEmpty()) {
            throw exception;
        }
        try {
            getByKeyAndName(customField.entityKey(), customField.name());
        } catch (ExceptionResourceNotFound ignored) {
            return;
        }
        throw new ExceptionFailedDbValidation("Custom Field with provided Entity Key and Name already found in the database. entity_key: "
                + customField.entityKey() + " name: " + customField.name() + ".");
    }
}
