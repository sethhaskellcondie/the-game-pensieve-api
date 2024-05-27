package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ErrorLogs;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionCustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInternalCatastrophe;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class CustomFieldValueRepository {

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldRepository customFieldRepository;
    private final Logger logger = LoggerFactory.getLogger(CustomFieldRepository.class);
    private final RowMapper<CustomFieldValueDao> rowMapper = (resultSet, rowNumber) ->
            new CustomFieldValueDao(
                    resultSet.getInt("custom_field_id"),
                    resultSet.getInt("entity_id"),
                    resultSet.getString("entity_key"),
                    resultSet.getString("value_text"),
                    resultSet.getInt("value_number"),
                    resultSet.getBoolean("deleted")
            );

    public CustomFieldValueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldRepository = new CustomFieldRepository(jdbcTemplate);
    }

    public List<CustomFieldValue> upsertValues(List<CustomFieldValue> values, int entityId, String entityKey) {
        List<CustomFieldValue> savedValues = new ArrayList<>();
        for (CustomFieldValue value: values) {
            savedValues.add(upsertValue(value, entityId, entityKey));
        }
        return savedValues;
    }

    public CustomFieldValue upsertValue(CustomFieldValue value, int entityId, String entityKey) {
        if (value.getCustomFieldId() > 0) {
            value = updateValue(value, entityId, entityKey);
        } else {
            value = insertValue(value, entityId, entityKey);
        }
        return value;
    }

    private CustomFieldValue insertValue(CustomFieldValue value, int entityId, String entityKey) {
        CustomField upsertedCustomField = UpsertCustomField(value, entityKey);
        value.setCustomFieldId(upsertedCustomField.id());
        CustomFieldValueDao valueDao = convertToDao(value, entityId, entityKey);
        final String sql = """
                			INSERT INTO custom_field_values(custom_field_id, entity_id, entity_key, value_text, value_number) VALUES (?, ?, ?, ?, ?);
                """;
        jdbcTemplate.update(sql, valueDao.customFieldId(), valueDao.entityId(), valueDao.entityKey(), valueDao.valueText(), valueDao.valueNumber());

        return getByCustomFieldValueByCustomFieldIdAndEntityId(valueDao.customFieldId(), valueDao.entityId()).convertToValue(value.getCustomFieldName(), value.getCustomFieldType());
    }

    private CustomFieldValue updateValue(CustomFieldValue value, int entityId, String entityKey) {
        CustomField upsertedCustomField = UpsertCustomField(value, entityKey);
        value.setCustomFieldId(upsertedCustomField.id());
        CustomFieldValueDao valueDao = convertToDao(value, entityId, entityKey);
        final String sql = """
                			UPDATE custom_field_values SET value_text = ?, value_number = ?;
                """;
        jdbcTemplate.update(sql, valueDao.valueText(), valueDao.valueNumber());

        //TODO clean up the upsert
        //take the id from the value and get it from the database (even if it is deleted)
        //if a value is retrieved then run an update query in the database
        //else call insertValue with this value
        return value;
    }

    private CustomField UpsertCustomField(CustomFieldValue value, String entityKey) {
        CustomField customField;
        if (value.getCustomFieldId() <= 0) {
            try {
                customField = customFieldRepository.insertCustomField(new CustomField(value.getCustomFieldId(), value.getCustomFieldName(), value.getCustomFieldType(), entityKey));
            } catch (ExceptionFailedDbValidation exception) {
                throw new ExceptionCustomFieldValue("Cannot create new custom field needed to insert a new value: " + exception.getMessage());
            }
        } else {
            try {
                customField = customFieldRepository.getById(value.getCustomFieldId());
            } catch (ExceptionResourceNotFound e) {
                throw new ExceptionCustomFieldValue("Invalid CustomFieldId: " + value.getCustomFieldId() + " on provided value with entityKey: " + entityKey);
            }
            if (!Objects.equals(customField.type(), value.getCustomFieldType())) {
                throw new ExceptionCustomFieldValue("Custom field retrieved from the database with provided id: " + value.getCustomFieldId() + " has type: " + customField.type() +
                        " provided type on custom field value did not match. Value custom field type: " + value.getCustomFieldType() + ".");
            }
            if (!Objects.equals(customField.name(), value.getCustomFieldName())) {
                try {
                    customField = customFieldRepository.updateName(customField.id(), value.getCustomFieldName());
                } catch (ExceptionResourceNotFound exception) {
                    //We should never hit this code because we just did a get by id.
                    logger.error(ErrorLogs.InsertThenRetrieveError("Custom Field", customField.id()));
                    throw new ExceptionInternalCatastrophe("Custom Field", customField.id());
                }
            }
        }
        return customField;
    }

    private CustomFieldValueDao getByCustomFieldValueByCustomFieldIdAndEntityId(int customFieldId, int entityId) {
        final String sql = "SELECT * FROM custom_field_values WHERE custom_field_id = ? AND entity_id = ? ;";
        CustomFieldValueDao valueDao = null;
        try {
            valueDao = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{customFieldId, entityId},
                    new int[]{Types.BIGINT, Types.BIGINT},
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionCustomFieldValue("Custom Field Value not found in database RIGHT AFTER INSERT with custom_field_id: " + customFieldId +
                    "AND entity_id: " + entityId + ".");
        }
        return valueDao;
    }

    private CustomFieldValueDao convertToDao(CustomFieldValue customFieldValue, int entityId, String entityKey) {
        switch (customFieldValue.getCustomFieldType()) {
            case CustomField.TYPE_TEXT -> {
                return new CustomFieldValueDao(customFieldValue.getCustomFieldId(), entityId, entityKey, customFieldValue.getValue(), null, customFieldValue.isDeleted());
            }
            case CustomField.TYPE_NUMBER -> {
                try {
                    return new CustomFieldValueDao(customFieldValue.getCustomFieldId(), entityId, entityKey, null, Integer.parseInt(customFieldValue.getValue()), customFieldValue.isDeleted());
                } catch (NumberFormatException exception) {
                    throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: if the Custom Field Type is number the value must be a valid Integer.")));
                }
            }
            case CustomField.TYPE_BOOLEAN -> {
                if (Objects.equals(customFieldValue.getValue(), "true") || Objects.equals(customFieldValue.getValue(), "false")) {
                    return new CustomFieldValueDao(customFieldValue.getCustomFieldId(), entityId, entityKey, customFieldValue.getValue(), null, customFieldValue.isDeleted());
                }
                throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: if the Custom Field Type is boolean the value must be exactly 'true' or 'false'.")));
            }
            default -> {
                throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: unknown Custom Field Type provided: " + customFieldValue.getCustomFieldType() +
                        ". Valid types include [" + String.join(", ", CustomField.getAllCustomFieldTypes()) + "]")));
            }
        }
    }
}

record CustomFieldValueDao(int customFieldId, int entityId, String entityKey, String valueText, Integer valueNumber, boolean deleted) {

    CustomFieldValue convertToValue(String customFieldName, String customFieldType) {
        if (Objects.equals(customFieldType, CustomField.TYPE_TEXT) || Objects.equals(customFieldType, CustomField.TYPE_BOOLEAN)) {
            return new CustomFieldValue(this.customFieldId, customFieldName, customFieldType, this.valueText, this.deleted);
        }
        return new CustomFieldValue(this.customFieldId, customFieldName, customFieldType, this.valueNumber.toString(), this.deleted);
    }
}
