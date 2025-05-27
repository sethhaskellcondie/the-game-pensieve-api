package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyRequestDto;

/**
 * This test suite is a little different it simulates the likely situation of updating and importing a single file
 * again and again trying to get their data imported so the tests are not independent instead they build on top of one another. Each assuming that
 * some data was imported from the previous test. Because of this, there is a single test-container dedicated to this test suite to be spun up
 * each time the test run to ensure repeated accurate results.
 */
@SpringBootTest
@ActiveProfiles("import-tests")
public class BackupImportGatewayTests {

    @Autowired
    private BackupImportGateway gateway;

    //custom fields that are new should pass in a 0 as the id
    private final CustomField initialCustomField = new CustomField(0, "Initial Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
    private final ToyRequestDto initialToy = new ToyRequestDto("Initial Toy", "Initial Set", new ArrayList<>());

    private final CustomField toyCustomField = new CustomField(11, "Count", CustomField.TYPE_NUMBER, Keychain.TOY_KEY);
    private final ToyRequestDto secondToy = new ToyRequestDto("Second Toy", "Second Set", List.of(new CustomFieldValue(11, "Count", CustomField.TYPE_NUMBER, "4")));

    private final SystemRequestDto initialSystem = new SystemRequestDto("Initial System", 3, false, List.of(new CustomFieldValue(0, "Initial Custom Field", CustomField.TYPE_TEXT, "Initial Value")));
    private final CustomField systemCustomField = new CustomField(99, "Owned", CustomField.TYPE_BOOLEAN, Keychain.SYSTEM_KEY);
    private final SystemRequestDto secondSystem = new SystemRequestDto("Second System", 4, false, List.of(new CustomFieldValue(0, "Owned", CustomField.TYPE_BOOLEAN, "false")));

    //Future Update: update the functionality of this to work with no id assumed
    //This is assuming that there is a system in the database with an ID of 1, this should be the initial system but any system will work.
    private final VideoGameRequestDto initialVideoGame = new VideoGameRequestDto("Initial Video Game", 1, new ArrayList<>());
    private final CustomField videoGameCustomField = new CustomField(0, "Hall Of Fame", CustomField.TYPE_TEXT, Keychain.VIDEO_GAME_KEY);
    private final VideoGameRequestDto secondVideoGame = new VideoGameRequestDto("Second Video Game", 1, List.of(new CustomFieldValue(0, "Hall Of Fame", CustomField.TYPE_TEXT, "1st Place")));

    /**
     * This test suite is different each test depends on the previous tests so there is only a single test, but it tests everything.
     */
    @Test
    void testImportBackupData_AllOfIt() {
        //Test 1: initial happy path test import a custom field and toy
        String testNumber = "1";
        final BackupDataDto expectedBackupData1 = new BackupDataDto(
                List.of(initialCustomField),
                List.of(initialToy),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        final ImportResultsDto backupResult1 = gateway.importBackupData(expectedBackupData1);

        assertAll(
                "Error on test " + testNumber + ": Importing initial custom field and toy data.",
                () -> assertEquals(1, backupResult1.createdCustomFields()),
                () -> assertEquals(0, backupResult1.existingCustomFields()),
                () -> assertEquals(1, backupResult1.createdToys()),
                () -> assertEquals(0, backupResult1.existingToys()),
                () -> assertEquals(0, backupResult1.createdSystems()),
                () -> assertEquals(0, backupResult1.existingSystems()),
                () -> assertEquals(0, backupResult1.existingVideoGames()),
                () -> assertEquals(0, backupResult1.createdVideoGames()),
                () -> assertEquals(0, backupResult1.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(expectedBackupData1, gateway.getBackupData(), testNumber);


        //Test 2: given when a custom field is imported with the same name but different type, then an error will be returned, and other entries will be skipped
        testNumber = "2";
        final BackupDataDto expectedBackupData2 = new BackupDataDto(
                //The name and key match the initialCustomField but the type is a mismatch causing an error
                List.of(new CustomField(0, "Initial Custom Field", CustomField.TYPE_NUMBER, Keychain.SYSTEM_KEY)),
                List.of(new ToyRequestDto("Will Be Skipped", "Ignored", new ArrayList<>())),
                List.of(new SystemRequestDto("Will Be Skipped", 3, false, new ArrayList<>())),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        final ImportResultsDto backupResult2 = gateway.importBackupData(expectedBackupData2);

        assertAll(
                "Error on test " + testNumber + ": Unexpected result for importing custom fields with mismatched types.",
                () -> assertEquals(0, backupResult2.createdCustomFields()),
                () -> assertEquals(0, backupResult2.existingCustomFields()),
                () -> assertEquals(0, backupResult2.createdToys()),
                () -> assertEquals(0, backupResult2.existingToys()),
                () -> assertEquals(0, backupResult2.createdSystems()),
                () -> assertEquals(0, backupResult2.existingSystems()),
                () -> assertEquals(0, backupResult2.existingVideoGames()),
                () -> assertEquals(0, backupResult2.createdVideoGames()),
                () -> assertEquals(2, backupResult2.exceptionBackupImport().getExceptions().size())
        );
        //since the import failed the expected data didn't change
        validateBackupData(expectedBackupData1, gateway.getBackupData(), testNumber);


        //Test 3: given valid custom fields and invalid custom fields, return some successes and some errors
        testNumber = "3";
        final BackupDataDto backupData3 = new BackupDataDto(
                //The toyCustomField will work but the invalidCustomField will throw an error preventing the rest of the import from completing.
                List.of(initialCustomField, toyCustomField, new CustomField(42, "Valid Name", "Invalid Type", "Invalid Key")),
                List.of(new ToyRequestDto("Will Be Skipped", "Ignored", new ArrayList<>())),
                List.of(new SystemRequestDto("Will Be Skipped", 3, false, new ArrayList<>())),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        final BackupDataDto expectedBackupData3 = new BackupDataDto(
                List.of(initialCustomField, toyCustomField),
                List.of(initialToy),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        final ImportResultsDto backupResult3 = gateway.importBackupData(backupData3);

        assertAll(
                "Error on test " + testNumber + ": Unexpected results for successfully importing some custom fields while others return errors.",
                () -> assertEquals(1, backupResult3.createdCustomFields()),
                () -> assertEquals(1, backupResult3.existingCustomFields()),
                () -> assertEquals(0, backupResult3.createdToys()),
                () -> assertEquals(0, backupResult3.existingToys()),
                () -> assertEquals(0, backupResult3.createdSystems()),
                () -> assertEquals(0, backupResult3.existingSystems()),
                () -> assertEquals(0, backupResult3.existingVideoGames()),
                () -> assertEquals(0, backupResult3.createdVideoGames()),
                () -> assertEquals(2, backupResult3.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(expectedBackupData3, gateway.getBackupData(), testNumber);

        //Test 4: If a toy to be imported has a custom field value but is missing a matching custom field in the import that toy will be skipped
        testNumber = "4";
        final ToyRequestDto skippedToy = new ToyRequestDto("Valid Name", "Valid Set", List.of(new CustomFieldValue(0, "Missing Name", CustomField.TYPE_TEXT, "Value")));
        final BackupDataDto backupData4 = new BackupDataDto(
                List.of(initialCustomField, toyCustomField),
                List.of(initialToy, secondToy, skippedToy),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        final BackupDataDto expectedBackupData4 = new BackupDataDto(
                List.of(initialCustomField, toyCustomField),
                List.of(initialToy, secondToy),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        final ImportResultsDto results4 = gateway.importBackupData(backupData4);

        assertAll(
                "Error on test " + testNumber + ": Unexpected results for importing some toys while others return errors.",
                () -> assertEquals(0, results4.createdCustomFields()),
                () -> assertEquals(2, results4.existingCustomFields()),
                () -> assertEquals(1, results4.createdToys()),
                () -> assertEquals(1, results4.existingToys()),
                () -> assertEquals(0, results4.createdSystems()),
                () -> assertEquals(0, results4.existingSystems()),
                () -> assertEquals(0, results4.existingVideoGames()),
                () -> assertEquals(0, results4.createdVideoGames()),
                () -> assertEquals(2, results4.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(expectedBackupData4, gateway.getBackupData(), testNumber);


        //Test 5: if a system to be imported has a custom field value but is missing a matching custom field in the import that system will be skipped
        testNumber = "5";
        final SystemRequestDto skippedSystem = new SystemRequestDto("Valid Name", 4, false, List.of(new CustomFieldValue(0, "Missing Name", CustomField.TYPE_TEXT, "Value")));
        final BackupDataDto backupData5 = new BackupDataDto(
                List.of(initialCustomField, toyCustomField),
                List.of(initialToy, secondToy),
                List.of(initialSystem, skippedSystem),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        final BackupDataDto expectedBackupData5 = new BackupDataDto(
                List.of(initialCustomField, toyCustomField),
                List.of(initialToy, secondToy),
                List.of(initialSystem),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        final ImportResultsDto results5 = gateway.importBackupData(backupData5);

        assertAll(
                "Error in test 5: Unexpected results for importing some systems successfully and others with missing custom fields.",
                () -> assertEquals(0, results5.createdCustomFields()),
                () -> assertEquals(2, results5.existingCustomFields()),
                () -> assertEquals(0, results5.createdToys()),
                () -> assertEquals(2, results5.existingToys()),
                () -> assertEquals(1, results5.createdSystems()),
                () -> assertEquals(0, results5.existingSystems()),
                () -> assertEquals(0, results5.existingVideoGames()),
                () -> assertEquals(0, results5.createdVideoGames()),
                () -> assertEquals(2, results5.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(expectedBackupData5, gateway.getBackupData(), testNumber);

        //Test 6: Given an existing system and a new system return success
        testNumber = "6";
        final BackupDataDto backupData6 = new BackupDataDto(
                List.of(initialCustomField, toyCustomField, systemCustomField),
                List.of(initialToy, secondToy),
                List.of(initialSystem, secondSystem),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        final ImportResultsDto results6 = gateway.importBackupData(backupData6);

        assertAll(
                "Error on test " + testNumber + ": Unexpected results for importing some systems while others systems already exist.",
                () -> assertEquals(1, results6.createdCustomFields()), //systemCustomField is new
                () -> assertEquals(2, results6.existingCustomFields()),
                () -> assertEquals(0, results6.createdToys()),
                () -> assertEquals(2, results6.existingToys()),
                () -> assertEquals(1, results6.createdSystems()), //secondSystem is new
                () -> assertEquals(1, results6.existingSystems()), //initialSystem is existing
                () -> assertEquals(0, results6.existingVideoGames()),
                () -> assertEquals(0, results6.createdVideoGames()),
                () -> assertEquals(0, results6.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(backupData6, gateway.getBackupData(), testNumber);

        //TODO update this to take video game boxes instead of video games
        //test 7: given some videos games with some custom fields missing return success and errors
//        testNumber = "7";
//        final VideoGameRequestDto skippedVideoGame = new VideoGameRequestDto("Valid Title", 1, List.of(new CustomFieldValue(0, "Missing Name", CustomField.TYPE_TEXT, "Value")));
//        final BackupDataDto backupData7 = new BackupDataDto(
//                List.of(initialCustomField, toyCustomField, systemCustomField),
//                List.of(initialToy, secondToy),
//                List.of(initialSystem, secondSystem),
//                List.of(initialVideoGame, skippedVideoGame),
//                List.of(),
//                List.of(),
//                List.of()
//        );
//        final BackupDataDto expectedBackupData7 = new BackupDataDto(
//                List.of(initialCustomField, toyCustomField, systemCustomField),
//                List.of(initialToy, secondToy),
//                List.of(initialSystem, secondSystem),
//                List.of(initialVideoGame),
//                List.of(),
//                List.of(),
//                List.of()
//        );
//
//        final ImportResultsDto results7 = gateway.importBackupData(backupData7);
//
//        assertAll(
//                "Unexpected results for importing some video games successfully and others with missing custom fields.",
//                () -> assertEquals(0, results7.createdCustomFields()),
//                () -> assertEquals(3, results7.existingCustomFields()),
//                () -> assertEquals(0, results7.createdToys()),
//                () -> assertEquals(2, results7.existingToys()),
//                () -> assertEquals(0, results7.createdSystems()),
//                () -> assertEquals(2, results7.existingSystems()),
//                () -> assertEquals(0, results7.existingVideoGames()),
//                () -> assertEquals(1, results7.createdVideoGames()), //Initial video game is new
//                () -> assertEquals(2, results7.exceptionBackupImport().getExceptions().size())
//        );
//        validateBackupData(expectedBackupData7, gateway.getBackupData(), testNumber);

        //test 8: given importing video games some existing and some new return success
//        testNumber = "8";
//        final BackupDataDto backupData8 = new BackupDataDto(
//                List.of(initialCustomField, toyCustomField, systemCustomField, videoGameCustomField),
//                List.of(initialToy, secondToy),
//                List.of(initialSystem, secondSystem),
//                List.of(initialVideoGame, secondVideoGame),
//                List.of(),
//                List.of(),
//                List.of()
//        );
//
//        final ImportResultsDto results8 = gateway.importBackupData(backupData8);
//
//        assertAll(
//                "Error on test " + testNumber + "Unexpected results for importing some video games while others systems already exist.",
//                () -> assertEquals(1, results8.createdCustomFields()), //videoGameCustomField is new
//                () -> assertEquals(3, results8.existingCustomFields()),
//                () -> assertEquals(0, results8.createdToys()),
//                () -> assertEquals(2, results8.existingToys()),
//                () -> assertEquals(0, results8.createdSystems()),
//                () -> assertEquals(2, results8.existingSystems()),
//                () -> assertEquals(1, results8.existingVideoGames()), //initial video game is existing
//                () -> assertEquals(1, results8.createdVideoGames()), //second video game is new
//                () -> assertEquals(0, results8.exceptionBackupImport().getExceptions().size())
//        );
//        validateBackupData(backupData8, gateway.getBackupData(), testNumber);

        //Test Last: Make sure that if the backupData is taken from the system and loaded back into the system that no new entries are found, and all are accounted for
        testNumber = "last";
        final BackupDataDto backupDataFromSystem = gateway.getBackupData();
        //---- This will need to be updated if any of the previous tests are updated to add more data ----
        final BackupDataDto lastExpectedBackupData = new BackupDataDto(
                List.of(initialCustomField, toyCustomField, systemCustomField),
                List.of(initialToy, secondToy),
                List.of(initialSystem, secondSystem),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        validateBackupData(lastExpectedBackupData, backupDataFromSystem, testNumber);

        final ImportResultsDto backupDataResult = gateway.importBackupData(backupDataFromSystem);

        assertAll(
                "Error on the last test: Unexpected errors when checking to see that the import process is idempotent.",
                () -> assertEquals(0, backupDataResult.createdCustomFields()),
                () -> assertEquals(lastExpectedBackupData.customFields().size(), backupDataResult.existingCustomFields()),
                () -> assertEquals(0, backupDataResult.createdToys()),
                () -> assertEquals(lastExpectedBackupData.toys().size(), backupDataResult.existingToys()),
                () -> assertEquals(0, backupDataResult.createdSystems()),
                () -> assertEquals(lastExpectedBackupData.systems().size(), backupDataResult.existingSystems()),
                () -> assertEquals(0, backupDataResult.createdVideoGames()),
                () -> assertEquals(lastExpectedBackupData.videoGames().size(), backupDataResult.existingVideoGames()),
                () -> assertEquals(0, backupDataResult.exceptionBackupImport().getExceptions().size())
        );
        validateBackupData(lastExpectedBackupData, gateway.getBackupData(), testNumber);
    }

    // ====================================== Private Validation Methods ======================================

    private void validateBackupData(BackupDataDto expectedBackupData, BackupDataDto actualBackupData, String testNumber) {
        validateCustomFieldBackupData(expectedBackupData, actualBackupData, testNumber);
        validateToyBackupData(expectedBackupData, actualBackupData, testNumber);
        validateSystemBackupData(expectedBackupData, actualBackupData, testNumber);
        validateVideoGameBackupData(expectedBackupData, actualBackupData, testNumber);
    }

    private void validateCustomFieldBackupData(BackupDataDto expectedData, BackupDataDto actualData, String testNumber) {
        final List<CustomField> expectedCustomFields = expectedData.customFields();
        final List<CustomField> actualCustomFields = actualData.customFields();
        if (null == expectedCustomFields || null == actualCustomFields) {
            assertAll(
                    "Error on test " + testNumber + ": If the expected custom fields are null then the actual should be as well.",
                    () -> assertNull(expectedCustomFields),
                    () -> assertNull(actualCustomFields)
            );
            return;
        }
        assertEquals(expectedCustomFields.size(), actualCustomFields.size(), "Error on test " + testNumber + ": Unexpected number of custom field results returned in BackupDataDto.");
        for (int i = 0; i < expectedCustomFields.size(); i++) {
            final CustomField expectedCustomField = expectedCustomFields.get(i);
            final CustomField actualCustomField = actualCustomFields.get(i);
            assertAll(
                    "Error on test " + testNumber + ": Mismatched custom field data returned in BackupDataDto.",
                    () -> assertEquals(expectedCustomField.name(), actualCustomField.name()),
                    () -> assertEquals(expectedCustomField.type(), actualCustomField.type()),
                    () -> assertEquals(expectedCustomField.entityKey(), actualCustomField.entityKey())
            );
        }
    }

    private void validateToyBackupData(BackupDataDto expectedData, BackupDataDto actualData, String testNumber) {
        final List<ToyRequestDto> expectedToys = expectedData.toys();
        final List<ToyRequestDto> actualToys = actualData.toys();
        if (null == expectedToys || null == actualToys) {
            assertAll(
                    "Error on test " + testNumber + ": If the expected toys are null then the actual should be as well.",
                    () -> assertNull(expectedToys),
                    () -> assertNull(actualToys)
            );
            return;
        }
        assertEquals(expectedToys.size(), actualToys.size(), "Unexpected number of toy results returned in BackupDataDto");
        for (int i = 0; i < expectedToys.size(); i++) {
            final ToyRequestDto expectedToy = expectedToys.get(i);
            final ToyRequestDto actualToy = actualToys.get(i);
            assertAll(
                    "Error on test " + testNumber + ": Mismatched toy data returned in BackupDataDto.",
                    () -> assertEquals(expectedToy.name(), actualToy.name()),
                    () -> assertEquals(expectedToy.set(), actualToy.set())
            );
            validateCustomFieldValues(expectedToy.customFieldValues(), actualToy.customFieldValues(), Keychain.TOY_KEY, actualToy.name(), testNumber);
        }
    }

    private void validateSystemBackupData(BackupDataDto expectedData, BackupDataDto actualData, String testNumber) {
        final List<SystemRequestDto> expectedSystems = expectedData.systems();
        final List<SystemRequestDto> actualSystems = actualData.systems();
        if (null == expectedSystems || null == actualSystems) {
            assertAll(
                    "Error in test " + testNumber + ": If the expected systems are null then the actual should be as well.",
                    () -> assertNull(expectedSystems),
                    () -> assertNull(actualSystems)
            );
            return;
        }
        assertEquals(expectedSystems.size(), actualSystems.size(), "Error in test " + testNumber + ": Unexpected number of system results returned in BackupDataDto");
        for (int i = 0; i < expectedSystems.size(); i++) {
            final SystemRequestDto expectedSystem = expectedSystems.get(i);
            final SystemRequestDto actualSystem = actualSystems.get(i);
            assertAll(
                    "Error on test " + testNumber + ": Mismatched system data returned in BackupDataDto.",
                    () -> assertEquals(expectedSystem.name(), actualSystem.name()),
                    () -> assertEquals(expectedSystem.generation(), actualSystem.generation()),
                    () -> assertEquals(expectedSystem.handheld(), actualSystem.handheld())
            );
            validateCustomFieldValues(expectedSystem.customFieldValues(), actualSystem.customFieldValues(), Keychain.SYSTEM_KEY, actualSystem.name(), testNumber);
        }
    }

    private void validateVideoGameBackupData(BackupDataDto expectedData, BackupDataDto actualData, String testNumber) {
        final List<VideoGameRequestDto> expectedVideoGames = expectedData.videoGames();
        final List<VideoGameRequestDto> actualVideoGames = actualData.videoGames();
        if (null == expectedVideoGames || null == actualVideoGames) {
            assertAll(
                    "Error on test " + testNumber + ": If the expected video games are null then the actual should be as well.",
                    () -> assertNull(expectedVideoGames),
                    () -> assertNull(actualVideoGames)
            );
            return;
        }
        assertEquals(expectedVideoGames.size(), actualVideoGames.size(), "Unexpected number of system results returned in BackupDataDto");
        for (int i = 0; i < expectedVideoGames.size(); i++) {
            final VideoGameRequestDto expectedVideoGame = expectedVideoGames.get(i);
            final VideoGameRequestDto actualVideoGame = actualVideoGames.get(i);
            assertAll(
                    "Error on test " + testNumber + ": Mismatched video game data returned in BackupDataDto.",
                    () -> assertEquals(expectedVideoGame.title(), actualVideoGame.title()),
                    () -> assertEquals(expectedVideoGame.systemId(), actualVideoGame.systemId())
            );
            validateCustomFieldValues(expectedVideoGame.customFieldValues(), actualVideoGame.customFieldValues(), Keychain.VIDEO_GAME_KEY, actualVideoGame.title(), testNumber);
        }
    }

    private void validateCustomFieldValues(List<CustomFieldValue> expectedValues, List<CustomFieldValue> actualValues, String entityKey, String name, String testNumber) {
        assertEquals(expectedValues.size(), actualValues.size(), "Unexpected number of custom field values in " + entityKey + " with the name/title '" + name + "'");
        for (int i = 0; i < expectedValues.size(); i++) {
            final CustomFieldValue expectedValue = expectedValues.get(i);
            final CustomFieldValue actualValue = actualValues.get(i);
            assertAll(
                    "Error on test " + testNumber + ": Mismatched custom field value data returned in " + entityKey + " with the name/title '" + name + "'",
                    () -> assertEquals(expectedValue.getCustomFieldName(), actualValue.getCustomFieldName()),
                    () -> assertEquals(expectedValue.getCustomFieldType(), actualValue.getCustomFieldType()),
                    () -> assertEquals(expectedValue.getValue(), actualValue.getValue())
            );
        }
    }
}
