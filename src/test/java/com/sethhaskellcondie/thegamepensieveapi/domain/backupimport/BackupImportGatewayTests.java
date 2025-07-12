package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.SlimVideoGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;

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

    //TODO go back and test these function with data already in the system.
    //TODO refactor these tests to test for specific exceptions. (refactor the results to pass back systemExceptions, videoGameExceptions, etc.)

    @Test
    void customFieldImport_InvalidCustomFields_NoDataImported() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomField validCustomField = new CustomField(100, "Valid Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final CustomField duplicateValidCustomField = new CustomField(100, "Valid Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final CustomField zeroIdCustomField = new CustomField(0, "Zero ID Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final CustomField negativeIdCustomField = new CustomField(-1, "Negative ID Custom Field", CustomField.TYPE_NUMBER, Keychain.TOY_KEY);
        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        customFieldsList.add(validCustomField); //valid but will be skipped
        customFieldsList.add(duplicateValidCustomField); //will return an error for having the same id as validCustomField
        customFieldsList.add(zeroIdCustomField); //return error invalid id
        customFieldsList.add(negativeIdCustomField); //return error invalid id
        final BackupDataDto importData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Unexpected error when importing invalid custom fields.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(0, importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(0, importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(0, importResult.existingSystems()),
                () -> assertEquals(0, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(4, importResult.exceptionBackupImport().getExceptions().size())
        );
    }


    @Test
    void customFieldImport_ValidAndDuplicateCustomFields_ValidDataImportedErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomField validCustomField = new CustomField(100, "Valid Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final CustomField duplicateValidCustomField = new CustomField(200, "Valid Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        customFieldsList.add(validCustomField); //import successful
        final BackupDataDto expectedResult = new BackupDataDto(
                new ArrayList<>(customFieldsList),
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes()
        );
        customFieldsList.add(duplicateValidCustomField); //return as existing, no data is created or updated
        final BackupDataDto duplicateImportData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(duplicateImportData);

        //Assert
        assertAll(
                "Error on duplicate custom field import.",
                () -> assertEquals(1, importResult.createdCustomFields()),
                () -> assertEquals(1, importResult.existingCustomFields()), // Both existing custom fields
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(0, importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(0, importResult.existingSystems()),
                () -> assertEquals(0, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getExceptions().size())
        );
        final BackupDataDto resultsBackupData = gateway.getBackupData();
        validateBackupData(expectedResult, resultsBackupData);
    }

    @Test
    void toyImport_InvalidData_ErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomFieldValue missingCustomFieldValue = new CustomFieldValue(999, "Missing Custom Field", CustomField.TYPE_NUMBER, "123");
        final ToyResponseDto toyWithMissingCustomField = new ToyResponseDto(Keychain.TOY_KEY, 701, "Toy Missing Custom Field", "Missing Set", null, null, null, List.of(missingCustomFieldValue));
        final CustomFieldValue invalidCustomFieldValue = new CustomFieldValue(-1, "Invalid Custom Field", CustomField.TYPE_BOOLEAN, "true");
        final ToyResponseDto toyWithInvalidCustomField = new ToyResponseDto(Keychain.TOY_KEY, 702, "Toy Invalid Custom Field", "Invalid Set", null, null, null, List.of(invalidCustomFieldValue));
        List<ToyResponseDto> toysList = new ArrayList<>(initialBackupData.toys());
        toysList.add(toyWithMissingCustomField); //return error custom field not included on the import
        toysList.add(toyWithInvalidCustomField); //return error invalid custom field ID
        final BackupDataDto importData = new BackupDataDto(
                initialBackupData.customFields(),
                toysList,
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on toy import with custom field validation issues.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(0, importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(0, importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(0, importResult.existingSystems()),
                () -> assertEquals(0, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(2, importResult.exceptionBackupImport().getExceptions().size())
        );
    }

    @Test
    void toyImport_ValidAndDuplicateData_ErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final ToyResponseDto newToy = new ToyResponseDto(Keychain.TOY_KEY, 400, "Duplicate Test Toy", "Test Set Alpha", null, null, null, new ArrayList<>());
        final ToyResponseDto duplicateToy = new ToyResponseDto(Keychain.TOY_KEY, 500, "Duplicate Test Toy", "Test Set Alpha", null, null, null, new ArrayList<>());
        List<ToyResponseDto> toysList = new ArrayList<>(initialBackupData.toys());
        toysList.add(newToy); //successful import
        final BackupDataDto expectedResult = new BackupDataDto(
                initialBackupData.customFields(),
                new ArrayList<>(toysList),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes()
        );
        toysList.add(duplicateToy); //import skipped returned as existing toy
        final BackupDataDto firstImportData = new BackupDataDto(
                initialBackupData.customFields(),
                toysList,
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(firstImportData);

        //Assert
        assertAll(
                "Error on duplicate toy import.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(0, importResult.existingCustomFields()),
                () -> assertEquals(1, importResult.createdToys()),
                () -> assertEquals(1, importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(0, importResult.existingSystems()),
                () -> assertEquals(0, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getExceptions().size())
        );
        final BackupDataDto resultsBackupData = gateway.getBackupData();
        validateBackupData(expectedResult, resultsBackupData);
    }

    @Test
    void systemImport_InvalidData_ErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomFieldValue missingCustomFieldValue = new CustomFieldValue(999, "Missing Custom Field", CustomField.TYPE_NUMBER, "123");
        final SystemResponseDto systemWithMissingCustomField = new SystemResponseDto(Keychain.SYSTEM_KEY, 801, "System Missing Custom Field", 5, false, null, null, null, List.of(missingCustomFieldValue));
        final CustomFieldValue invalidCustomFieldValue = new CustomFieldValue(-1, "Invalid Custom Field", CustomField.TYPE_BOOLEAN, "true");
        final SystemResponseDto systemWithInvalidCustomField = new SystemResponseDto(Keychain.SYSTEM_KEY, 802, "System Invalid Custom Field", 6, true, null, null, null, List.of(invalidCustomFieldValue));
        List<SystemResponseDto> systemsList = new ArrayList<>(initialBackupData.systems());
        systemsList.add(systemWithMissingCustomField); //return error custom field not included on the import
        systemsList.add(systemWithInvalidCustomField); //return error invalid custom field ID
        final BackupDataDto importData = new BackupDataDto(
                initialBackupData.customFields(),
                initialBackupData.toys(),
                systemsList,
                initialBackupData.videoGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on system import with custom field validation issues.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(0, importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(0, importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(0, importResult.existingSystems()),
                () -> assertEquals(0, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(2, importResult.exceptionBackupImport().getExceptions().size())
        );
    }

    @Test
    void systemImport_ValidAndDuplicateData_ValidDataImportedDuplicateReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final SystemResponseDto newSystem = new SystemResponseDto(Keychain.SYSTEM_KEY, 900, "Duplicate Test System", 7, false, null, null, null, new ArrayList<>());
        final SystemResponseDto duplicateSystem = new SystemResponseDto(Keychain.SYSTEM_KEY, 901, "Duplicate Test System", 8, true, null, null, null, new ArrayList<>());
        List<SystemResponseDto> systemsList = new ArrayList<>(initialBackupData.systems());
        systemsList.add(newSystem); //successful import
        final BackupDataDto expectedResult = new BackupDataDto(
                initialBackupData.customFields(),
                initialBackupData.toys(),
                new ArrayList<>(systemsList),
                initialBackupData.videoGameBoxes()
        );
        systemsList.add(duplicateSystem); //import skipped returned as existing system (same name)
        final BackupDataDto importData = new BackupDataDto(
                initialBackupData.customFields(),
                initialBackupData.toys(),
                systemsList,
                initialBackupData.videoGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on duplicate system import.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(0, importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(0, importResult.existingToys()),
                () -> assertEquals(1, importResult.createdSystems()),
                () -> assertEquals(1, importResult.existingSystems()),
                () -> assertEquals(0, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getExceptions().size())
        );
        final BackupDataDto resultsBackupData = gateway.getBackupData();
        validateBackupData(expectedResult, resultsBackupData);
    }

    @Test
    void videoGameBoxImport_InvalidBoxData_ErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomFieldValue missingCustomFieldValue = new CustomFieldValue(999, "Missing Custom Field", CustomField.TYPE_NUMBER, "123");
        final SystemResponseDto missingSystem = new SystemResponseDto(Keychain.SYSTEM_KEY, 99,  "Missing Test System", 7, false, null, null, null, new ArrayList<>());
        final VideoGameBoxResponseDto videoGameBoxWithMissingInformation = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 810, "Box With Missing Information", missingSystem, new ArrayList<>(), true, false, null, null, null, List.of(missingCustomFieldValue));
        List<VideoGameBoxResponseDto> videoGameBoxes = new ArrayList<>(initialBackupData.videoGameBoxes().size() + 1);
        videoGameBoxes.add(videoGameBoxWithMissingInformation);
        final BackupDataDto importData = new BackupDataDto(
                initialBackupData.customFields(),
                initialBackupData.toys(),
                initialBackupData.systems(),
                //Will return four total errors
                //one error stating that there were errors
                //one for the missing custom field
                //one for the missing system
                //one for no video games being included in this video game box
                videoGameBoxes
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on video game box import with custom field validation issues.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(0, importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(0, importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(0, importResult.existingSystems()),
                () -> assertEquals(0, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(4, importResult.exceptionBackupImport().getExceptions().size())
        );
    }

    @Test
    void videoGameBoxImport_InvalidVideoGameData_ErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final int validCustomFieldBoxCustomFieldId = 987;
        final CustomField validVideoGameBoxCustomField = new CustomField(validCustomFieldBoxCustomFieldId, "Valid", CustomField.TYPE_NUMBER, Keychain.VIDEO_GAME_BOX_KEY);
        final CustomFieldValue validCustomFieldValue = new CustomFieldValue(validCustomFieldBoxCustomFieldId, "Valid", CustomField.TYPE_NUMBER, "123");
        final CustomFieldValue missingCustomFieldValue = new CustomFieldValue(999, "Missing Custom Field", CustomField.TYPE_NUMBER, "123");
        final SystemResponseDto validSystem = new SystemResponseDto(Keychain.SYSTEM_KEY, 98,  "Valid", 7, false, null, null, null, new ArrayList<>());
        final SystemResponseDto missingSystem = new SystemResponseDto(Keychain.SYSTEM_KEY, 99,  "Missing Test System", 7, false, null, null, null, new ArrayList<>());
        final SlimVideoGame invalidGame = new SlimVideoGame(234, "Game With Missing Information", missingSystem, null, null, null, List.of(missingCustomFieldValue));
        final VideoGameBoxResponseDto validVideoGameBox = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 810, "Valid Box With Invalid Game", validSystem, List.of(invalidGame), true, false, null, null, null, List.of(validCustomFieldValue));
        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        customFieldsList.add(validVideoGameBoxCustomField);
        List<SystemResponseDto> systemsList = new ArrayList<>(initialBackupData.systems());
        systemsList.add(validSystem);
        List<VideoGameBoxResponseDto> videoGameBoxes = new ArrayList<>(initialBackupData.videoGameBoxes().size() + 1);
        videoGameBoxes.add(validVideoGameBox);
        final BackupDataDto importData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                systemsList,
                //Will return four total errors
                //one error stating that there were errors
                //one for the missing custom field on the video game (not the box)
                //one for the missing system on the video game (not the box)
                //one for no video valid games being included in this video game box
                videoGameBoxes
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on system import with custom field validation issues.",
                () -> assertEquals(1, importResult.createdCustomFields()),
                () -> assertEquals(0, importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(0, importResult.existingToys()),
                () -> assertEquals(1, importResult.createdSystems()),
                () -> assertEquals(0, importResult.existingSystems()),
                () -> assertEquals(0, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(4, importResult.exceptionBackupImport().getExceptions().size())
        );
    }



    @Test
    void videoGameGoxImport_ValidAndDuplicateGamesAndBoxes_ValidDataImportedDuplicateReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final int validVideoGameBoxCustomFieldId = 554;
        final int validVideoGameCustomFieldId = 556;
        final CustomField validVideoGameBoxCustomField = new CustomField(validVideoGameBoxCustomFieldId, "Valid For Box", CustomField.TYPE_NUMBER, Keychain.VIDEO_GAME_BOX_KEY);
        final CustomField validVideoGameCustomField = new CustomField(validVideoGameCustomFieldId, "Valid For Game", CustomField.TYPE_TEXT, Keychain.VIDEO_GAME_KEY);
        final CustomFieldValue validBoxCustomFieldValue1 = new CustomFieldValue(validVideoGameBoxCustomFieldId, "Valid For Box", CustomField.TYPE_NUMBER, "123");
        final CustomFieldValue validBoxCustomFieldValue2 = new CustomFieldValue(validVideoGameBoxCustomFieldId, "Valid For Box", CustomField.TYPE_NUMBER, "1234");
        final CustomFieldValue validGameCustomFieldValue = new CustomFieldValue(validVideoGameCustomFieldId, "Valid For Game", CustomField.TYPE_TEXT, "ABC");
        final SystemResponseDto validSystem = new SystemResponseDto(Keychain.SYSTEM_KEY, 78,  "Valid System", 7, false, null, null, null, new ArrayList<>());
        final SlimVideoGame validGame1 = new SlimVideoGame(111, "Valid Game 1", validSystem, null, null, null, List.of(validGameCustomFieldValue));
        final SlimVideoGame validGame2 = new SlimVideoGame(112, "Valid Game 2", validSystem, null, null, null, List.of(validGameCustomFieldValue));
        final VideoGameBoxResponseDto validVideoGameBox1 = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 810, "Valid Single Game Box", validSystem, List.of(validGame1), true, false, null, null, null, List.of(validBoxCustomFieldValue1));
        final VideoGameBoxResponseDto validVideoGameBox2 = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 811, "Valid Collection Box", validSystem, List.of(validGame1, validGame2), true, true, null, null, null, List.of(validBoxCustomFieldValue2));
        final VideoGameBoxResponseDto duplicateVideoGameBox = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 812, "Valid Collection Box", validSystem, List.of(validGame1, validGame2), true, true, null, null, null, List.of());
        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        customFieldsList.add(validVideoGameBoxCustomField);
        customFieldsList.add(validVideoGameCustomField);
        List<SystemResponseDto> systemsList = new ArrayList<>(initialBackupData.systems());
        systemsList.add(validSystem);
        List<VideoGameBoxResponseDto> videoGameBoxes = new ArrayList<>(initialBackupData.videoGameBoxes().size() + 2);
        videoGameBoxes.add(validVideoGameBox1);
        videoGameBoxes.add(validVideoGameBox2);
        final BackupDataDto expectedResult = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                systemsList,
                new ArrayList<>(videoGameBoxes)
        );
        videoGameBoxes.add(duplicateVideoGameBox);
        final BackupDataDto importData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                systemsList,
                videoGameBoxes
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on duplicate video game box import.",
                () -> assertEquals(2, importResult.createdCustomFields()),
                () -> assertEquals(0, importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(0, importResult.existingToys()),
                () -> assertEquals(1, importResult.createdSystems()),
                () -> assertEquals(0, importResult.existingSystems()),
                () -> assertEquals(2, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(1, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getExceptions().size())
        );
        final BackupDataDto resultsBackupData = gateway.getBackupData();
        validateBackupData(expectedResult, resultsBackupData);
    }

    //TODO include tests for board game imports

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
        final List<VideoGameBoxResponseDto> expectedVideoGames = expectedData.videoGameBoxes();
        final List<VideoGameBoxResponseDto> actualVideoGames = actualData.videoGameBoxes();
        if (null == expectedVideoGames || null == actualVideoGames) {
            assertAll(
                    "If the expected video games are null then the actual should be as well.",
                    () -> assertNull(expectedVideoGames),
                    () -> assertNull(actualVideoGames)
            );
            return;
        }
        assertEquals(expectedVideoGames.size(), actualVideoGames.size(), "Unexpected number of video game box results returned in BackupDataDto");
        for (int i = 0; i < expectedVideoGames.size(); i++) {
            final VideoGameBoxResponseDto expectedVideoGame = expectedVideoGames.get(i);
            final VideoGameBoxResponseDto actualVideoGame = actualVideoGames.get(i);
            assertAll(
                    "Mismatched video game data returned in BackupDataDto.",
                    () -> assertEquals(expectedVideoGame.title(), actualVideoGame.title())
//                    () -> assertEquals(expectedVideoGame.system().id(), actualVideoGame.system().id()) //this line is comparing the original id with the inserted id they will not match...
                    //TODO assert the rest
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
