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
 * When exporting a backup ALL the data in the database will be exported into a massive JSON object and output to a backup.json file.
 * ID's of object will be output as well, these ID's will be used to determine relationships of data when imported.
 * <p>
 * The import function is idempotent, it is assumed that in most cases imports will be done on an empty database.
 * But a common case will be importing a file with errors, then adjusting the import file based on errors returned during the
 * import process then attempt an import of this file again. (thus the idempotent requirement)
 * <p>
 * The ID's on the import data are only used to reference relationships with other entities in the import data, THESE WILL NOT BE THE IDS INSERTED INTO THE DATABASE
 * Because of this each entity has a different way of checking if that entity is already in the database (usually the name)
 * Details on each entity can be found below.
 */
@SpringBootTest
@ActiveProfiles("import-tests")
public class BackupImportGatewayTests {

    @Autowired
    private BackupImportGateway gateway;

    @Test
    void validImportData_CustomFieldToyAndSystemData_ReturnSuccess() {
        //custom fields that are new should pass in a 0 as the id
        final CustomField initialCustomField = new CustomField(0, "Initial Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final ToyRequestDto initialToy = new ToyRequestDto("Initial Toy", "Initial Set", new ArrayList<>());

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
