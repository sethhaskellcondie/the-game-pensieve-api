package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.ErrorLogs;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionCustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalCatastrophe;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
public class CustomFieldValueRepository {

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldRepository customFieldRepository;
    private final CustomFieldOptionRepository customFieldOptionRepository;
    private final Logger logger = LoggerFactory.getLogger(CustomFieldRepository.class);
    private final RowMapper<CustomFieldValueDao> customFieldValueDaoRowMapper = (resultSet, rowNumber) ->
            new CustomFieldValueDao(
                    resultSet.getInt("custom_field_id"),
                    resultSet.getInt("entity_id"),
                    resultSet.getString("entity_key"),
                    resultSet.getString("value_text"),
                    resultSet.getInt("value_number"),
                    (Integer) resultSet.getObject("value_option_id")
            );
    private final RowMapper<CustomFieldValueJoinCustomFieldDao> customFieldValueJoinCustomFieldDaoRowMapper = (resultSet, rowNumber) ->
            new CustomFieldValueJoinCustomFieldDao(
                    resultSet.getInt("custom_field_id"),
                    resultSet.getInt("entity_id"),
                    resultSet.getString("entity_key"),
                    resultSet.getString("value_text"),
                    resultSet.getInt("value_number"),
                    (Integer) resultSet.getObject("value_option_id"),
                    resultSet.getBoolean("deleted"),
                    resultSet.getString("name"),
                    resultSet.getString("type"),
                    resultSet.getString("option_name")
            );

    //This repository should only be accessed through EntityRepositories
    public CustomFieldValueRepository(JdbcTemplate jdbcTemplate, CustomFieldRepository customFieldRepository, CustomFieldOptionRepository customFieldOptionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldRepository = customFieldRepository;
        this.customFieldOptionRepository = customFieldOptionRepository;
    }

    public List<CustomFieldValue> getCustomFieldValuesByEntityIdAndEntityKey(int entityId, String entityKey) {
        final String sql = """
                    SELECT custom_field_values.custom_field_id, custom_field_values.entity_id, custom_field_values.entity_key,
                           custom_field_values.value_text, custom_field_values.value_number, custom_field_values.value_option_id,
                           custom_fields.deleted, custom_fields.name, custom_fields.type,
                           custom_field_options.name AS option_name
                        FROM custom_field_values
                        JOIN custom_fields ON custom_field_values.custom_field_id = custom_fields.id
                        LEFT JOIN custom_field_options ON custom_field_values.value_option_id = custom_field_options.id
                        WHERE custom_field_values.entity_id = ? AND custom_field_values.entity_key = ? AND custom_fields.deleted = false;
                """;
        List<CustomFieldValueJoinCustomFieldDao> customFieldValueJoinCustomFieldDaos = jdbcTemplate.query(sql, customFieldValueJoinCustomFieldDaoRowMapper, entityId, entityKey);
        return customFieldValueJoinCustomFieldDaos.stream().map(CustomFieldValueJoinCustomFieldDao::convertToValue).toList();
    }

    public Map<Integer, List<CustomFieldValue>> getCustomFieldValuesByEntityIdsAndEntityKey(List<Integer> entityIds, String entityKey) {
        String placeholders = entityIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        final String sql = "SELECT custom_field_values.custom_field_id, custom_field_values.entity_id, custom_field_values.entity_key,"
                + " custom_field_values.value_text, custom_field_values.value_number, custom_field_values.value_option_id,"
                + " custom_fields.deleted, custom_fields.name, custom_fields.type, custom_field_options.name AS option_name"
                + " FROM custom_field_values"
                + " JOIN custom_fields ON custom_field_values.custom_field_id = custom_fields.id"
                + " LEFT JOIN custom_field_options ON custom_field_values.value_option_id = custom_field_options.id"
                + " WHERE custom_field_values.entity_id IN (" + placeholders + ")"
                + " AND custom_field_values.entity_key = ? AND custom_fields.deleted = false;";
        Object[] params = new Object[entityIds.size() + 1];
        for (int i = 0; i < entityIds.size(); i++) {
            params[i] = entityIds.get(i);
        }
        params[entityIds.size()] = entityKey;
        List<CustomFieldValueJoinCustomFieldDao> daos = jdbcTemplate.query(sql, customFieldValueJoinCustomFieldDaoRowMapper, params);
        Map<Integer, List<CustomFieldValue>> result = new HashMap<>();
        for (CustomFieldValueJoinCustomFieldDao dao : daos) {
            result.computeIfAbsent(dao.entityId(), k -> new ArrayList<>()).add(dao.convertToValue());
        }
        return result;
    }

    public List<CustomFieldValue> upsertValues(List<CustomFieldValue> values, int entityId, String entityKey) {
        List<CustomFieldValue> savedValues = new ArrayList<>();
        for (CustomFieldValue value: values) {
            savedValues.add(updateValue(value, entityId, entityKey));
        }
        return savedValues;
    }

    private CustomFieldValue updateValue(CustomFieldValue value, int entityId, String entityKey) {
        CustomField relatedCustomField = insertOrUpdateNameOfCustomField(value, entityKey);
        value.setCustomFieldId(relatedCustomField.id());
        CustomFieldValueDao valueDao = convertToDao(value, entityId, entityKey);
        if (null == getDaoByCustomFieldIdAndEntityId(value.getCustomFieldId(), entityId, false)) {
            return convertDaoToValue(insertDao(valueDao), relatedCustomField.name(), relatedCustomField.type());
        }

        final String sql = """
                            UPDATE custom_field_values SET value_text = ?, value_number = ?, value_option_id = ? WHERE custom_field_id = ? AND entity_id = ?;
                """;
        jdbcTemplate.update(sql, valueDao.valueText(), valueDao.valueNumber(), valueDao.valueOptionId(), valueDao.customFieldId(), valueDao.entityId());
        return convertDaoToValue(valueDao, relatedCustomField.name(), relatedCustomField.type());
    }

    //Enum values store only the option id, so resolve the option name here for the response; other types delegate to the DAO.
    private CustomFieldValue convertDaoToValue(CustomFieldValueDao valueDao, String customFieldName, String customFieldType) {
        if (CustomField.isEnumType(customFieldType)) {
            final String optionName = customFieldOptionRepository.getOptionById(valueDao.valueOptionId()).name();
            return new CustomFieldValue(valueDao.customFieldId(), customFieldName, customFieldType, optionName, valueDao.valueOptionId());
        }
        return valueDao.convertToValue(customFieldName, customFieldType);
    }

    private CustomFieldValueDao insertDao(CustomFieldValueDao valueDao) {
        final String sql = """
                            INSERT INTO custom_field_values(custom_field_id, entity_id, entity_key, value_text, value_number, value_option_id) VALUES (?, ?, ?, ?, ?, ?);
                """;
        jdbcTemplate.update(sql, valueDao.customFieldId(), valueDao.entityId(), valueDao.entityKey(), valueDao.valueText(), valueDao.valueNumber(), valueDao.valueOptionId());

        return getDaoByCustomFieldIdAndEntityId(valueDao.customFieldId(), valueDao.entityId(), true);
    }

    //it's not an upsert because we are not checking if it exists first, instead we are inserting first then failing if retrieving the existing entry fails
    private CustomField insertOrUpdateNameOfCustomField(CustomFieldValue value, String entityKey) {
        if (value.getCustomFieldId() <= 0) {
            try {
                return customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions(value.getCustomFieldName(), value.getCustomFieldType(), entityKey));
            } catch (ExceptionFailedDbValidation exception) {
                throw new ExceptionCustomFieldValue("Cannot create new custom field needed to insert a new value: " + exception.getMessage(), exception);
            }
        }

        CustomField customField;
        try {
            customField = customFieldRepository.getById(value.getCustomFieldId());
        } catch (ExceptionResourceNotFound e) {
            throw new ExceptionCustomFieldValue("Invalid CustomFieldId: " + value.getCustomFieldId() + " on provided CustomFieldValue with entityKey: " + entityKey, e);
        }
        if (!Objects.equals(customField.type(), value.getCustomFieldType())) {
            throw new ExceptionCustomFieldValue("Custom field retrieved from the database with provided id: " + value.getCustomFieldId() + " has type: " + customField.type()
                    + " provided type on custom field value did not match. Value custom field type: " + value.getCustomFieldType() + ".");
        }
        if (!Objects.equals(customField.name(), value.getCustomFieldName())) {
            try {
                customField = customFieldRepository.update(customField.id(), value.getCustomFieldName(), customField.order());
            } catch (ExceptionResourceNotFound exception) {
                //We should never hit this code because we just did a get by id.
                logger.error(ErrorLogs.insertThenRetrieveError("Custom Field", customField.id()));
                throw new ExceptionInternalCatastrophe("Custom Field", customField.id(), exception);
            }
        }
        return customField;
    }

    private CustomFieldValueDao getDaoByCustomFieldIdAndEntityId(int customFieldId, int entityId, boolean justInserted) {
        final String sql = "SELECT * FROM custom_field_values WHERE custom_field_id = ? AND entity_id = ? ;";
        CustomFieldValueDao valueDao = null;
        try {
            valueDao = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{customFieldId, entityId},
                    new int[]{Types.BIGINT, Types.BIGINT},
                    customFieldValueDaoRowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            if (justInserted) {
                throw new ExceptionCustomFieldValue("Custom Field Value not found in database RIGHT AFTER INSERT with custom_field_id: " + customFieldId
                        + "AND entity_id: " + entityId + ".", exception);
            }
        }
        return valueDao;
    }

    private CustomFieldValueDao convertToDao(CustomFieldValue customFieldValue, int entityId, String entityKey) {
        switch (customFieldValue.getCustomFieldType()) {
            case CustomField.TYPE_TEXT -> {
                return new CustomFieldValueDao(customFieldValue.getCustomFieldId(), entityId, entityKey, customFieldValue.getValue(), null, null);
            }
            case CustomField.TYPE_NUMBER -> {
                try {
                    return new CustomFieldValueDao(customFieldValue.getCustomFieldId(), entityId, entityKey, null, Integer.parseInt(customFieldValue.getValue()), null);
                } catch (NumberFormatException exception) {
                    throw new ExceptionMalformedEntity("Malformed Custom Field Value: if the Custom Field Type is number the value must be a valid Integer.", exception);
                }
            }
            case CustomField.TYPE_BOOLEAN -> {
                if (Objects.equals(customFieldValue.getValue(), "true") || Objects.equals(customFieldValue.getValue(), "false")) {
                    return new CustomFieldValueDao(customFieldValue.getCustomFieldId(), entityId, entityKey, customFieldValue.getValue(), null, null);
                }
                throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: if the Custom Field Type is boolean the value must be exactly 'true' or 'false'.")));
            }
            case CustomField.TYPE_DROPDOWN, CustomField.TYPE_RADIO_BUTTON, CustomField.TYPE_PROGRESS_BAR -> {
                if (!customFieldOptionRepository.isValidOptionId(customFieldValue.getCustomFieldId(), customFieldValue.getValueOptionId())) {
                    throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: valueOptionId '" + customFieldValue.getValueOptionId()
                            + "' is not a valid option for custom field id " + customFieldValue.getCustomFieldId() + ".")));
                }
                return new CustomFieldValueDao(customFieldValue.getCustomFieldId(), entityId, entityKey, null, null, customFieldValue.getValueOptionId());
            }
            default -> {
                //This is just a sanity check the type will have been matched against the found CustomField earlier in the process
                throw new ExceptionMalformedEntity(List.of(new Exception("Malformed Custom Field Value: unknown Custom Field Type provided: " + customFieldValue.getCustomFieldType()
                        + ". Valid types include [" + String.join(", ", CustomField.getAllCustomFieldTypes()) + "]")));
            }
        }
    }
}

record CustomFieldValueDao(int customFieldId, int entityId, String entityKey, String valueText, Integer valueNumber, Integer valueOptionId) {

    //Enum types are resolved in the repository (this DAO has no option name), they must not be routed here.
    CustomFieldValue convertToValue(String customFieldName, String customFieldType) {
        if (Objects.equals(customFieldType, CustomField.TYPE_TEXT)
                || Objects.equals(customFieldType, CustomField.TYPE_BOOLEAN)) {
            return new CustomFieldValue(this.customFieldId, customFieldName, customFieldType, this.valueText);
        }
        return new CustomFieldValue(this.customFieldId, customFieldName, customFieldType, this.valueNumber.toString());
    }
}

record CustomFieldValueJoinCustomFieldDao(int customFieldId, int entityId, String entityKey, String valueText, Integer valueNumber, Integer valueOptionId,
                                          boolean deleted, String customFieldName, String customFieldType, String optionName) {

    CustomFieldValue convertToValue() {
        if (Objects.equals(customFieldType, CustomField.TYPE_TEXT)
                || Objects.equals(customFieldType, CustomField.TYPE_BOOLEAN)) {
            return new CustomFieldValue(customFieldId, customFieldName, customFieldType, valueText);
        }
        if (CustomField.getEnumCustomFieldTypes().contains(customFieldType)) {
            return new CustomFieldValue(customFieldId, customFieldName, customFieldType, optionName, valueOptionId);
        }
        return new CustomFieldValue(this.customFieldId, customFieldName, customFieldType, valueNumber.toString());
    }
}
