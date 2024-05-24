package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class CustomFieldValueRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Logger logger = LoggerFactory.getLogger(CustomFieldRepository.class);

    public CustomFieldValueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CustomFieldValue> upsertValues(List<CustomFieldValue> values, int entityId, String entityKey) {
        List<CustomFieldValue> savedValues = new ArrayList<>();
        for (CustomFieldValue value: values) {
            savedValues.add(upsertValue(value, entityId, entityKey));
        }
        return savedValues;
    }

    public CustomFieldValue upsertValue(CustomFieldValue value, int entityId, String entityKey) {
        CustomFieldValueDao valueDao = value.convertToDao(entityId, entityKey);
        if (value.getCustomFieldId() > 0) {
            valueDao = updateValue(valueDao);
        } else {
            valueDao = insertValue(valueDao);
        }
        return valueDao.convertToValue(value.getCustomFieldName(), value.getCustomFieldType());
    }

    private CustomFieldValueDao insertValue(CustomFieldValueDao value) {
        //TODO finish this
        return value;
    }

    private CustomFieldValueDao updateValue(CustomFieldValueDao value) {
        //TODO finish this
        return value;
    }
}
