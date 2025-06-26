package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
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
    void validImportData_CustomFieldToyAndCustomFieldData_ReturnSuccess() {
        final BackupDataDto backupDataDto = gateway.getBackupData();

        final CustomField initialCustomField = new CustomField(0, "Initial Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final ToyRequestDto initialToy = new ToyRequestDto("Initial Toy", "Initial Set", new ArrayList<>());

        final BackupDataDto expectedBackupData = new BackupDataDto(
                List.of(initialCustomField),
                List.of(initialToy),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        final ImportResultsDto backupResult1 = gateway.importBackupData(expectedBackupData);

        assertAll(
                "Error on importing valid toy and custom field data.",
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
        validateBackupData(expectedBackupData, gateway.getBackupData());
    }

    //TODO Test invalid custom field ID's (ID's that are not a positive integer, and duplicate ID's)

    //TODO Test that custom fields that were already imported (checked by the name, and key) are not imported again and are instead returned as existingCustomFields

    //TODO The ID's on Toys are meaningless, validate that toys with a unique name and set combination are created in the database

    //TODO validate that toys with an existing name and set combination are NOT imported OR updated, they are returned as existing toy counts

    //TODO validate that a toy with an invalid custom field ID (not found in the imports, OR not found in the database) return errors

    // ====================================== Private Validation Methods ======================================

    private void validateBackupData(BackupDataDto expectedBackupData, BackupDataDto actualBackupData) {
        validateCustomFieldBackupData(expectedBackupData, actualBackupData);
        validateToyBackupData(expectedBackupData, actualBackupData);
        validateSystemBackupData(expectedBackupData, actualBackupData);
        validateVideoGameBackupData(expectedBackupData, actualBackupData);
    }

    private void validateCustomFieldBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
        final List<CustomField> expectedCustomFields = expectedData.customFields();
        final List<CustomField> actualCustomFields = actualData.customFields();
        if (null == expectedCustomFields || null == actualCustomFields) {
            assertAll(
                    "If the expected custom fields are null then the actual should be as well.",
                    () -> assertNull(expectedCustomFields),
                    () -> assertNull(actualCustomFields)
            );
            return;
        }
        assertEquals(expectedCustomFields.size(), actualCustomFields.size(), "Unexpected number of custom field results returned in BackupDataDto.");
        for (int i = 0; i < expectedCustomFields.size(); i++) {
            final CustomField expectedCustomField = expectedCustomFields.get(i);
            final CustomField actualCustomField = actualCustomFields.get(i);
            assertAll(
                    "Mismatched custom field data returned in BackupDataDto.",
                    () -> assertEquals(expectedCustomField.name(), actualCustomField.name()),
                    () -> assertEquals(expectedCustomField.type(), actualCustomField.type()),
                    () -> assertEquals(expectedCustomField.entityKey(), actualCustomField.entityKey())
            );
        }
    }

    private void validateToyBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
        final List<ToyResponseDto> expectedToys = expectedData.toys();
        final List<ToyResponseDto> actualToys = actualData.toys();
        if (null == expectedToys || null == actualToys) {
            assertAll(
                    "If the expected toys are null then the actual should be as well.",
                    () -> assertNull(expectedToys),
                    () -> assertNull(actualToys)
            );
            return;
        }
        assertEquals(expectedToys.size(), actualToys.size(), "Unexpected number of toy results returned in BackupDataDto");
        for (int i = 0; i < expectedToys.size(); i++) {
            final ToyResponseDto expectedToy = expectedToys.get(i);
            final ToyResponseDto actualToy = actualToys.get(i);
            assertAll(
                    "Mismatched toy data returned in BackupDataDto.",
                    () -> assertEquals(expectedToy.name(), actualToy.name()),
                    () -> assertEquals(expectedToy.set(), actualToy.set())
            );
            validateCustomFieldValues(expectedToy.customFieldValues(), actualToy.customFieldValues(), Keychain.TOY_KEY, actualToy.name());
        }
    }

    private void validateSystemBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
        final List<SystemResponseDto> expectedSystems = expectedData.systems();
        final List<SystemResponseDto> actualSystems = actualData.systems();
        if (null == expectedSystems || null == actualSystems) {
            assertAll(
                    "If the expected systems are null then the actual should be as well.",
                    () -> assertNull(expectedSystems),
                    () -> assertNull(actualSystems)
            );
            return;
        }
        assertEquals(expectedSystems.size(), actualSystems.size(), "Unexpected number of system results returned in BackupDataDto");
        for (int i = 0; i < expectedSystems.size(); i++) {
            final SystemResponseDto expectedSystem = expectedSystems.get(i);
            final SystemResponseDto actualSystem = actualSystems.get(i);
            assertAll(
                    "Mismatched system data returned in BackupDataDto.",
                    () -> assertEquals(expectedSystem.name(), actualSystem.name()),
                    () -> assertEquals(expectedSystem.generation(), actualSystem.generation()),
                    () -> assertEquals(expectedSystem.handheld(), actualSystem.handheld())
            );
            validateCustomFieldValues(expectedSystem.customFieldValues(), actualSystem.customFieldValues(), Keychain.SYSTEM_KEY, actualSystem.name());
        }
    }

    private void validateVideoGameBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
        final List<VideoGameResponseDto> expectedVideoGames = expectedData.videoGames();
        final List<VideoGameResponseDto> actualVideoGames = actualData.videoGames();
        if (null == expectedVideoGames || null == actualVideoGames) {
            assertAll(
                    "If the expected video games are null then the actual should be as well.",
                    () -> assertNull(expectedVideoGames),
                    () -> assertNull(actualVideoGames)
            );
            return;
        }
        assertEquals(expectedVideoGames.size(), actualVideoGames.size(), "Unexpected number of system results returned in BackupDataDto");
        for (int i = 0; i < expectedVideoGames.size(); i++) {
            final VideoGameResponseDto expectedVideoGame = expectedVideoGames.get(i);
            final VideoGameResponseDto actualVideoGame = actualVideoGames.get(i);
            assertAll(
                    "Mismatched video game data returned in BackupDataDto.",
                    () -> assertEquals(expectedVideoGame.title(), actualVideoGame.title()),
                    () -> assertEquals(expectedVideoGame.system().id(), actualVideoGame.system().id())
            );
            validateCustomFieldValues(expectedVideoGame.customFieldValues(), actualVideoGame.customFieldValues(), Keychain.VIDEO_GAME_KEY, actualVideoGame.title());
        }
    }

    private void validateCustomFieldValues(List<CustomFieldValue> expectedValues, List<CustomFieldValue> actualValues, String entityKey, String name) {
        assertEquals(expectedValues.size(), actualValues.size(), "Unexpected number of custom field values in " + entityKey + " with the name/title '" + name + "'");
        for (int i = 0; i < expectedValues.size(); i++) {
            final CustomFieldValue expectedValue = expectedValues.get(i);
            final CustomFieldValue actualValue = actualValues.get(i);
            assertAll(
                    "Mismatched custom field value data returned in " + entityKey + " with the name/title '" + name + "'",
                    () -> assertEquals(expectedValue.getCustomFieldName(), actualValue.getCustomFieldName()),
                    () -> assertEquals(expectedValue.getCustomFieldType(), actualValue.getCustomFieldType()),
                    () -> assertEquals(expectedValue.getValue(), actualValue.getValue())
            );
        }
    }
}
