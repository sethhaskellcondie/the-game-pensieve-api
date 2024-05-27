package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@JdbcTest
@ActiveProfiles("test-container")
public class CustomFieldValueRepositoryTests {

    @Autowired
    protected JdbcTemplate jdbcTemplate;
    protected CustomFieldValueRepository repository;

    @BeforeEach
    public void setUp() {
        repository = new CustomFieldValueRepository(jdbcTemplate);
    }

    @Test
    public void upsertValue_NewAndExistingValues_CustomFieldValueReturned() {
        int customFieldId = 0;
        String customFieldName = "Release Date";
        String customFieldValue = "1991";
        CustomFieldValue newValue = new CustomFieldValue(customFieldId, customFieldName, CustomField.TYPE_NUMBER, customFieldValue, false);
        CustomFieldValue returnedValue = repository.upsertValue(newValue, 1, "system");
        assertAll(
                "The CustomFieldValue that was return didn't match the expected value.",
                () -> assertNotEquals(customFieldId, returnedValue.getCustomFieldId()),
                () -> assertEquals(customFieldName, returnedValue.getCustomFieldName()),
                () -> assertEquals(CustomField.TYPE_NUMBER, returnedValue.getCustomFieldType()),
                () -> assertEquals(customFieldValue, returnedValue.getValue()),
                () -> assertFalse(returnedValue.isDeleted())
        );

    }
}
