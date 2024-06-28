package com.sethhaskellcondie.thegamepensiveapi.domain.backupimport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * This test suite is a little different it's a set of integration tests that simulate the likely situation of updating and importing a single file
 * again and again trying to get their data imported so the tests are not independent instead they build on top of one another. Each assuming that
 * some data was imported from the previous test. Because of this, there is a single test-container dedicated to this test suite to be spun up
 * each time the test run to ensure repeated accurate results.
 */
@SpringBootTest
@ActiveProfiles("import-tests")
public class BackupImportGatewayTests {

    @Autowired
    private BackupImportGateway gateway;

    //TODO finish fleshing out these tests
    @Test
    void testImportCustomFields_MismatchedType_ReturnErrors() {
        gateway.getBackupData();
        //The system will check for existing custom fields in the system by Name and EntityKey
        //if a system is found in the system then the type on the found system MUST match the type of the field to be imported
        //otherwise it will be skipped
        //only errors will be returned (still return ok)
    }

    @Test
    void testImportCustomFields_ValidFieldsAndInvalidTypeAndKey_ReturnSuccessAndErrors() {
        //The system will check for existing custom fields in the system by Name and EntityKey
        //if a custom field is found it will not be created again
        //if the custom field is invalid when created and error is thrown and that custom field will be skipped
        //valid new custom fields will be created
    }

    @Test
    void testImportCustomFields_NewAndExistingFields_ReturnSuccess() {
        //The system will check for existing systems
        //If a custom fields is found then it will not be created again, but the count will be returned
    }

    @Test
    void testImportBackupData_ErrorInCustomFieldsImport_ImportStopped() {
        //This will have a valid toy import and an invalid custom field import
        //the import function will return early without attempting to import the toy
    }

    @Test
    void testImportToys_MissingCustomField_ReturnSuccessAndErrors() {
        //if a toy to be imported has a custom field value but is missing a matching custom field in the import that toy will be skipped
        //valid toys will still be imported
    }

    @Test
    void testImportToys_ExistingToys_NoNewToysCreated() {
        //the system will check to see if a toy exists with the given name and set, if that toy is found then the custom fields values will be updated
        //but the toy will not be created again
    }

    @Test
    void testImportSystems_MissingCustomField_ReturnSuccessAndErrors() {
        //if a system to be imported has a custom field value but is missing a matching custom field in the import that system will be skipped
        //valid new system will still be created
    }

    @Test
    void testImportSystems_SomeExistingSomeNew_ReturnSuccessAndErrors() {
        //the system will check to see if an existing system entity is already in the database with the given name, if that system is found then it will be updated
        //new systems will be created in the database
    }
}
