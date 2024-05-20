package com.sethhaskellcondie.thegamepensiveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensiveapi.domain.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test-container")
@AutoConfigureMockMvc
public class CustomFieldTests {

    @Autowired
    private MockMvc mockMvc;
    private TestFactory factory;
    //since there is not a getById endpoint we will call getById through the repository
    private CustomFieldRepository repository;
    private String baseUrl = "v1/custom_fields";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    @Test
    void postCustomField_HappyPath_CustomFieldReturned() {

    }

    @Test
    void postCustomField_InvalidType_ReturnError() {

    }

    @Test
    void postCustomField_InvalidEntityKey_ReturnError() {

    }

    @Test
    void getCustomFields_HappyPath_ReturnAll() {
        //Seed two then return at least two
    }

    @Test
    void getCustomFieldsValuesCount_ZeroResults_ReturnZero() {
        //TODO finish this after values have been implemented
    }

    @Test
    void getCustomFieldsValuesCount_TwoResults_ReturnTwo() {
        //TODO finish this after values have been implemented
    }

    @Test
    void patchCustomFieldName_HappyPath_CustomFieldReturned() {

    }

    @Test
    void patchCustomFieldName_InvalidId_ReturnError() {

    }

    @Test
    void deleteCustomField_HappyPath_CustomFieldDeleted() {

    }

    @Test
    void deleteCustomField_InvalidId_ReturnError() {

    }
}
