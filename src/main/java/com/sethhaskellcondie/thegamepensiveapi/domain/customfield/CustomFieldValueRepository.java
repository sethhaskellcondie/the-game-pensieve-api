package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
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
        CustomField insertedCustomField;
        try {
            insertedCustomField = customFieldRepository.insertCustomField(new CustomField(value.getCustomFieldId(), value.getCustomFieldName(), value.getCustomFieldType(), entityKey));
            value.setCustomFieldId(insertedCustomField.id());
        } catch (ExceptionFailedDbValidation exception) {
            //TODO update this to return a custom runtime exception CustomFieldValueException
        }
        CustomFieldValueDao valueDao = value.convertToDao(entityId, entityKey);
        final String sql = """
                			INSERT INTO custom_field_values(custom_field_id, entity_id, entity_key, value_text, value_number) VALUES (?, ?, ?, ?, ?);
                """;
        jdbcTemplate.update(sql, valueDao.customFieldId(), valueDao.entityId(), valueDao.entityKey(), valueDao.valueText(), valueDao.valueNumber());
        return getByCustomFieldIdAndEntityId(valueDao.customFieldId(), valueDao.entityId()).convertToValue(value.getCustomFieldName(), value.getCustomFieldType());
    }

    private CustomFieldValue updateValue(CustomFieldValue value, int entityId, String entityKey) {
        //TODO finish this
        //take the id from the value and get it from the database (even if it is deleted)
        //if a value is retrieved then run an update query in the database
        //else call insertValue with this value
        return value;
    }

    private CustomFieldValueDao getByCustomFieldIdAndEntityId(int customFieldId, int entityId) {
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
            //TODO update this to return a custom runtime exception CustomFieldValueException
            throw new RuntimeException("Custom Field Value not found in database RIGHT AFTER INSERT with custom_field_id: " + customFieldId +
                    "AND entity_id: " + entityId + ".");
        }
        return valueDao;
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
