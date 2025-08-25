package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.SlimBoardGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.SlimVideoGame;
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

    @Test
    void customFieldImport_InvalidCustomFields_NoDataImported() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomField validCustomField = new CustomField(100, "Valid Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final CustomField duplicateValidCustomField = new CustomField(100, "Valid Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final CustomField zeroIdCustomField = new CustomField(0, "Zero ID Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final CustomField negativeIdCustomField = new CustomField(-1, "Negative ID Custom Field", CustomField.TYPE_NUMBER, Keychain.TOY_KEY);
        final int customFieldErrors = 3;
        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        customFieldsList.add(validCustomField); //valid but will be skipped
        customFieldsList.add(duplicateValidCustomField); //a duplicate ID is considered an error, not a duplicate, this will return an error
        customFieldsList.add(zeroIdCustomField); //return error invalid id, id's must be positive
        customFieldsList.add(negativeIdCustomField); //return error invalid id, id's must be positive
        final BackupDataDto importData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                initialBackupData.boardGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        //because no data is imported when there is an error with custom fields the existing values will also be returned as zero
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
                () -> assertEquals(0, importResult.existingBoardGameBoxes()),
                () -> assertEquals(0, importResult.createdBoardGameBoxes()),
                () -> assertEquals(customFieldErrors, importResult.exceptionBackupImport().getExceptions().size()),
                () -> assertEquals(customFieldErrors, importResult.exceptionBackupImport().getCustomFieldExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getToyExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getSystemExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameBoxExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getBoardGameExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getBoardGameBoxExceptions().getExceptions().size())
        );
    }


    @Test
    void customFieldImport_DuplicateCustomFields_DuplicatesCountedAsExisting() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomField validCustomField = new CustomField(100, "Valid Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        final CustomField duplicateValidCustomField = new CustomField(200, "Valid Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        customFieldsList.add(validCustomField); //valid and created as new
        final int createdCustomFields = 1;
        final BackupDataDto expectedResult = new BackupDataDto(
                new ArrayList<>(customFieldsList),
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                initialBackupData.boardGameBoxes()
        );
        customFieldsList.add(duplicateValidCustomField); //duplicate name, no data is created or updated this is based on the name NOT the id
        final int existingCustomFields = 1;
        final BackupDataDto duplicateImportData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                initialBackupData.boardGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(duplicateImportData);

        //Assert
        assertAll(
                "Error on duplicate custom field import.",
                () -> assertEquals(createdCustomFields, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size() + existingCustomFields, importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getExceptions().size())
        );
        final BackupDataDto resultsBackupData = gateway.getBackupData();
        validateBackupData(expectedResult, resultsBackupData);
    }

    @Test
    void toyImport_InvalidData_ToyErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomFieldValue missingCustomFieldValue = new CustomFieldValue(999, "Missing Custom Field", CustomField.TYPE_NUMBER, "123");
        final ToyResponseDto toyWithMissingCustomField = new ToyResponseDto(Keychain.TOY_KEY, 701, "Toy With Missing Custom Field", "Missing Set", null, null, null, List.of(missingCustomFieldValue));
        final CustomFieldValue invalidCustomFieldValue = new CustomFieldValue(-1, "Value Without Custom Field", CustomField.TYPE_BOOLEAN, "true");
        final ToyResponseDto toyWithInvalidCustomField = new ToyResponseDto(Keychain.TOY_KEY, 702, "Toy With Invalid Custom Field", "Invalid Set", null, null, null, List.of(invalidCustomFieldValue));
        List<ToyResponseDto> toysList = new ArrayList<>(initialBackupData.toys());
        final int toyErrors = 2;
        toysList.add(toyWithMissingCustomField); //return error custom field not included on the import
        toysList.add(toyWithInvalidCustomField); //return error invalid custom field ID
        final BackupDataDto importData = new BackupDataDto(
                initialBackupData.customFields(),
                toysList,
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                initialBackupData.boardGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on toy import with custom field validation issues.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(toyErrors, importResult.exceptionBackupImport().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getCustomFieldExceptions().getExceptions().size()),
                () -> assertEquals(toyErrors, importResult.exceptionBackupImport().getToyExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getSystemExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameBoxExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameExceptions().getExceptions().size())
        );
    }

    @Test
    void toyImport_DuplicateData_DuplicatesCountAsExisting() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final ToyResponseDto newToy = new ToyResponseDto(Keychain.TOY_KEY, 400, "Duplicate Test Toy", "Test Set Alpha", null, null, null, new ArrayList<>());
        final ToyResponseDto duplicateToy = new ToyResponseDto(Keychain.TOY_KEY, 500, "Duplicate Test Toy", "Test Set Alpha", null, null, null, new ArrayList<>());
        List<ToyResponseDto> toysList = new ArrayList<>(initialBackupData.toys());
        final int createdToys = 1;
        toysList.add(newToy); //successful import
        final BackupDataDto expectedResult = new BackupDataDto(
                initialBackupData.customFields(),
                new ArrayList<>(toysList),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                initialBackupData.boardGameBoxes()
        );
        final int existingToys = 1;
        toysList.add(duplicateToy); //import skipped returned as existing toy
        final BackupDataDto firstImportData = new BackupDataDto(
                initialBackupData.customFields(),
                toysList,
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                initialBackupData.boardGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(firstImportData);

        //Assert
        assertAll(
                "Error on duplicate toy import.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(createdToys, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size() + existingToys, importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getExceptions().size())
        );
        final BackupDataDto resultsBackupData = gateway.getBackupData();
        validateBackupData(expectedResult, resultsBackupData);
    }

    @Test
    void systemImport_InvalidData_SystemErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomFieldValue missingCustomFieldValue = new CustomFieldValue(999, "Missing Custom Field", CustomField.TYPE_NUMBER, "123");
        final SystemResponseDto systemWithMissingCustomField = new SystemResponseDto(Keychain.SYSTEM_KEY, 801,
                "System Missing Custom Field", 5, false, null, null, null, List.of(missingCustomFieldValue));
        final CustomFieldValue invalidCustomFieldValue = new CustomFieldValue(-1, "Invalid Custom Field", CustomField.TYPE_BOOLEAN, "true");
        final SystemResponseDto systemWithInvalidCustomField = new SystemResponseDto(Keychain.SYSTEM_KEY, 802,
                "System Invalid Custom Field", 6, true, null, null, null, List.of(invalidCustomFieldValue));
        List<SystemResponseDto> systemsList = new ArrayList<>(initialBackupData.systems());
        final int systemErrors = 2;
        systemsList.add(systemWithMissingCustomField); //return error custom field not included on the import
        systemsList.add(systemWithInvalidCustomField); //return error invalid custom field ID
        final BackupDataDto importData = new BackupDataDto(
                initialBackupData.customFields(),
                initialBackupData.toys(),
                systemsList,
                initialBackupData.videoGameBoxes(),
                initialBackupData.boardGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on system import with custom field validation issues.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(systemErrors, importResult.exceptionBackupImport().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getCustomFieldExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getToyExceptions().getExceptions().size()),
                () -> assertEquals(systemErrors, importResult.exceptionBackupImport().getSystemExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameBoxExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameExceptions().getExceptions().size())
        );
    }

    @Test
    void systemImport_DuplicateSystems_DuplicatesCountedAsExisting() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final SystemResponseDto newSystem = new SystemResponseDto(Keychain.SYSTEM_KEY, 900, "Duplicate Test System", 7, false, null, null, null, new ArrayList<>());
        final SystemResponseDto duplicateSystem = new SystemResponseDto(Keychain.SYSTEM_KEY, 901, "Duplicate Test System", 8, true, null, null, null, new ArrayList<>());
        List<SystemResponseDto> systemsList = new ArrayList<>(initialBackupData.systems());
        final int createdSystems = 1;
        systemsList.add(newSystem); //successful import
        final BackupDataDto expectedResult = new BackupDataDto(
                initialBackupData.customFields(),
                initialBackupData.toys(),
                new ArrayList<>(systemsList),
                initialBackupData.videoGameBoxes(),
                initialBackupData.boardGameBoxes()
        );
        final int existingSystems = 1;
        systemsList.add(duplicateSystem); //import skipped returned as existing system because they share the same name, NOT determined by ID.
        final BackupDataDto importData = new BackupDataDto(
                initialBackupData.customFields(),
                initialBackupData.toys(),
                systemsList,
                initialBackupData.videoGameBoxes(),
                initialBackupData.boardGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on duplicate system import.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(createdSystems, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size() + createdSystems, importResult.existingSystems()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
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
        final VideoGameBoxResponseDto videoGameBoxWithMissingInformation = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 810,
                "Box With Missing Information", missingSystem, new ArrayList<>(), true, false, null, null, null, List.of(missingCustomFieldValue));
        List<VideoGameBoxResponseDto> videoGameBoxes = new ArrayList<>(initialBackupData.videoGameBoxes());
        final int expectedVideoGameBoxErrors = 3;
        //one for the missing custom field
        //one for the missing system
        //one for no video games being included in this video game box
        videoGameBoxes.add(videoGameBoxWithMissingInformation);
        final BackupDataDto importData = new BackupDataDto(
                initialBackupData.customFields(),
                initialBackupData.toys(),
                initialBackupData.systems(),
                videoGameBoxes,
                initialBackupData.boardGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on video game box import with custom field validation issues.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(expectedVideoGameBoxErrors, importResult.exceptionBackupImport().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getCustomFieldExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getToyExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getSystemExceptions().getExceptions().size()),
                () -> assertEquals(expectedVideoGameBoxErrors, importResult.exceptionBackupImport().getVideoGameBoxExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameExceptions().getExceptions().size())
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
        final SlimVideoGame invalidGame = new SlimVideoGame(234, "Game With Missing Information", missingSystem, null, null, null,
                List.of(missingCustomFieldValue));
        final VideoGameBoxResponseDto validVideoGameBoxWithInvalidGame = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 810,
                "Valid Box With Invalid Game", validSystem, List.of(invalidGame), true, false, null, null, null, List.of(validCustomFieldValue));
        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        final int expectedCustomFieldsCreated = 1;
        customFieldsList.add(validVideoGameBoxCustomField);
        List<SystemResponseDto> systemsList = new ArrayList<>(initialBackupData.systems());
        final int expectedSystemsCreated = 1;
        systemsList.add(validSystem);
        List<VideoGameBoxResponseDto> videoGameBoxes = new ArrayList<>(initialBackupData.videoGameBoxes().size() + 1);
        final int expectedVideoGameErrors = 2;
        //one for the missing custom field on the video game (not the box)
        //one for the missing system on the video game (not the box)
        final int expectedVideoGameBoxErrors = 1;
        //one for no video valid games being included in this video game box
        videoGameBoxes.add(validVideoGameBoxWithInvalidGame);
        final BackupDataDto importData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                systemsList,
                videoGameBoxes,
                initialBackupData.boardGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on system import with custom field validation issues.",
                () -> assertEquals(expectedCustomFieldsCreated, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(expectedSystemsCreated, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(expectedVideoGameErrors + expectedVideoGameBoxErrors, importResult.exceptionBackupImport().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getCustomFieldExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getToyExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getSystemExceptions().getExceptions().size()),
                () -> assertEquals(expectedVideoGameBoxErrors, importResult.exceptionBackupImport().getVideoGameBoxExceptions().getExceptions().size()),
                () -> assertEquals(expectedVideoGameErrors, importResult.exceptionBackupImport().getVideoGameExceptions().getExceptions().size())
        );
    }

    @Test
    void videoGameBoxImport_ValidAndDuplicateGamesAndBoxes_ValidDataImportedDuplicateReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final int validVideoGameBoxCustomFieldId = 554;
        final String validVideoGameBoxCustomFieldName = "Valid For Box";
        final CustomField validVideoGameBoxCustomField = new CustomField(validVideoGameBoxCustomFieldId, validVideoGameBoxCustomFieldName, CustomField.TYPE_NUMBER, Keychain.VIDEO_GAME_BOX_KEY);
        final int validVideoGameCustomFieldId = 556;
        final String validVideoGameCustomFieldName = "Valid For Game";
        final CustomField validVideoGameCustomField = new CustomField(validVideoGameCustomFieldId, validVideoGameCustomFieldName, CustomField.TYPE_TEXT, Keychain.VIDEO_GAME_KEY);

        final CustomFieldValue validBoxCustomFieldValue1 = new CustomFieldValue(validVideoGameBoxCustomFieldId, validVideoGameBoxCustomFieldName, CustomField.TYPE_NUMBER, "123");
        final CustomFieldValue validBoxCustomFieldValue2 = new CustomFieldValue(validVideoGameBoxCustomFieldId, validVideoGameBoxCustomFieldName, CustomField.TYPE_NUMBER, "1234");
        final CustomFieldValue validGameCustomFieldValue1 = new CustomFieldValue(validVideoGameCustomFieldId,
                validVideoGameCustomFieldName, CustomField.TYPE_TEXT, "ABC");
        final CustomFieldValue validGameCustomFieldValue2 = new CustomFieldValue(validVideoGameCustomFieldId,
                validVideoGameCustomFieldName, CustomField.TYPE_TEXT, "ABCD");
        final SystemResponseDto validSystem = new SystemResponseDto(Keychain.SYSTEM_KEY, 78,  "Valid System", 7, false, null, null, null, new ArrayList<>());
        final SlimVideoGame validGame1 = new SlimVideoGame(111, "Valid Game 1", validSystem, null, null, null,
                List.of(validGameCustomFieldValue1));
        final SlimVideoGame validGame2 = new SlimVideoGame(112, "Valid Game 2", validSystem, null, null, null,
                List.of(validGameCustomFieldValue2));
        final VideoGameBoxResponseDto validVideoGameBox1 = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 810,
                "Valid Single Game Box", validSystem, List.of(validGame1), true, false, null, null, null, List.of(validBoxCustomFieldValue1));
        final VideoGameBoxResponseDto validVideoGameBox2 = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 811,
                "Valid Collection Box", validSystem, List.of(validGame1, validGame2), true, true, null, null, null, List.of(validBoxCustomFieldValue2));
        final VideoGameBoxResponseDto duplicateVideoGameBox = new VideoGameBoxResponseDto(Keychain.VIDEO_GAME_BOX_KEY, 812,
                "Valid Collection Box", validSystem, List.of(validGame1, validGame2), true, true, null, null, null, List.of());

        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        final int createdCustomFields = 2;
        customFieldsList.add(validVideoGameBoxCustomField);
        customFieldsList.add(validVideoGameCustomField);
        List<SystemResponseDto> systemsList = new ArrayList<>(initialBackupData.systems());
        final int createdSystems = 1;
        systemsList.add(validSystem);
        List<VideoGameBoxResponseDto> videoGameBoxes = new ArrayList<>(initialBackupData.videoGameBoxes());
        final int createdVideoGameBoxes = 2;
        videoGameBoxes.add(validVideoGameBox1);
        videoGameBoxes.add(validVideoGameBox2);
        final BackupDataDto expectedResult = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                systemsList,
                new ArrayList<>(videoGameBoxes),
                initialBackupData.boardGameBoxes()
        );
        final int existingVideoGameBoxes = 1; //This will be returned as existing because it shares the same name and system, NOT because of the provided IDs
        videoGameBoxes.add(duplicateVideoGameBox);
        final BackupDataDto importData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                systemsList,
                videoGameBoxes,
                initialBackupData.boardGameBoxes()
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on duplicate video game box import.",
                () -> assertEquals(createdCustomFields, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(createdSystems, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(createdVideoGameBoxes, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size() + existingVideoGameBoxes, importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getExceptions().size())
        );
        final BackupDataDto resultsBackupData = gateway.getBackupData();
        validateBackupData(expectedResult, resultsBackupData);
    }

    @Test
    void boardGameBoxImport_InvalidBoxData_BoardGameErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final CustomFieldValue missingCustomFieldValue = new CustomFieldValue(999, "Missing Custom Field", CustomField.TYPE_NUMBER, "123");
        final SlimBoardGame missingBoardGame = new SlimBoardGame(99, "Missing Board Game", null, null, null, new ArrayList<>());
        final BoardGameBoxResponseDto boardGameBoxWithMissingInformation = new BoardGameBoxResponseDto(Keychain.BOARD_GAME_BOX_KEY, 810,
                "", true, false, null, missingBoardGame, null, null, null, List.of(missingCustomFieldValue));
        List<BoardGameBoxResponseDto> boardGameBoxes = new ArrayList<>(initialBackupData.boardGameBoxes());
        final int expectedBoardGameBoxErrors = 2;
        //one for the missing custom field
        //one for the missing title (empty string)
        boardGameBoxes.add(boardGameBoxWithMissingInformation);
        final BackupDataDto importData = new BackupDataDto(
                initialBackupData.customFields(),
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                boardGameBoxes
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on board game box import with custom field validation issues.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.boardGameBoxes().size(), importResult.existingBoardGameBoxes()),
                () -> assertEquals(0, importResult.createdBoardGameBoxes()),
                () -> assertEquals(expectedBoardGameBoxErrors, importResult.exceptionBackupImport().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getCustomFieldExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getToyExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getSystemExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameBoxExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getBoardGameExceptions().getExceptions().size()),
                () -> assertEquals(expectedBoardGameBoxErrors, importResult.exceptionBackupImport().getBoardGameBoxExceptions().getExceptions().size())
        );
    }

    @Test
    void boardGameBoxImport_InvalidGameData_ErrorsReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final int validCustomFieldBoxCustomFieldId = 987;
        final CustomField validBoardGameBoxCustomField = new CustomField(validCustomFieldBoxCustomFieldId, "Valid", CustomField.TYPE_NUMBER, Keychain.BOARD_GAME_BOX_KEY);
        final CustomFieldValue validCustomFieldValue = new CustomFieldValue(validCustomFieldBoxCustomFieldId, "Valid", CustomField.TYPE_NUMBER, "123");
        final CustomFieldValue missingCustomFieldValue = new CustomFieldValue(999, "Missing Custom Field", CustomField.TYPE_NUMBER, "123");
        final SlimBoardGame invalidBoardGame = new SlimBoardGame(234, "", null, null, null,
                List.of(missingCustomFieldValue));
        final BoardGameBoxResponseDto validBoardGameBoxWithInvalidGame = new BoardGameBoxResponseDto(Keychain.BOARD_GAME_BOX_KEY, 810,
                "Valid Box With Invalid Game", true, false, null, invalidBoardGame, null, null, null, List.of(validCustomFieldValue));
        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        final int expectedCustomFieldsCreated = 1;
        customFieldsList.add(validBoardGameBoxCustomField);
        List<BoardGameBoxResponseDto> boardGameBoxes = new ArrayList<>(initialBackupData.boardGameBoxes().size() + 1);
        final int expectedBoardGameBoxErrors = 2;
        //one for the missing custom field on the board game
        //one for the missing title on the board game
        boardGameBoxes.add(validBoardGameBoxWithInvalidGame);
        final BackupDataDto importData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                boardGameBoxes
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on board game box import with invalid board game data.",
                () -> assertEquals(expectedCustomFieldsCreated, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.boardGameBoxes().size(), importResult.existingBoardGameBoxes()),
                () -> assertEquals(0, importResult.createdBoardGameBoxes()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getCustomFieldExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getToyExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getSystemExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameBoxExceptions().getExceptions().size()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getVideoGameExceptions().getExceptions().size()),
                () -> assertEquals(expectedBoardGameBoxErrors, importResult.exceptionBackupImport().getBoardGameExceptions().getExceptions().size())
        );
    }

    @Test
    void boardGameImport_ValidAndDuplicateBoardGamesAndBoxes_ValidDataImportedDuplicateReturned() {
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Arrange
        final int validBoardGameBoxCustomFieldId = 554;
        final String validBoardGameBoxCustomFieldName = "Valid For Box";
        final CustomField validBoardGameBoxCustomField = new CustomField(validBoardGameBoxCustomFieldId, validBoardGameBoxCustomFieldName, CustomField.TYPE_NUMBER, Keychain.BOARD_GAME_BOX_KEY);
        final int validBoardGameCustomFieldId = 556;
        final String validBoardGameCustomFieldName = "Valid For Game";
        final CustomField validBoardGameCustomField = new CustomField(validBoardGameCustomFieldId, validBoardGameCustomFieldName, CustomField.TYPE_TEXT, Keychain.BOARD_GAME_KEY);

        final CustomFieldValue validBoxCustomFieldValue1 = new CustomFieldValue(validBoardGameBoxCustomFieldId, validBoardGameBoxCustomFieldName, CustomField.TYPE_NUMBER, "123");
        final CustomFieldValue validBoxCustomFieldValue2 = new CustomFieldValue(validBoardGameBoxCustomFieldId, validBoardGameBoxCustomFieldName, CustomField.TYPE_NUMBER, "1234");
        final CustomFieldValue validGameCustomFieldValue1 = new CustomFieldValue(validBoardGameCustomFieldId,
                validBoardGameCustomFieldName, CustomField.TYPE_TEXT, "ABC");
        final CustomFieldValue validGameCustomFieldValue2 = new CustomFieldValue(validBoardGameCustomFieldId,
                validBoardGameCustomFieldName, CustomField.TYPE_TEXT, "ABCD");
        final CustomFieldValue validGameCustomFieldValue3 = new CustomFieldValue(validBoardGameCustomFieldId,
                validBoardGameCustomFieldName, CustomField.TYPE_TEXT, "ABCD");
        final SlimBoardGame validBoardGame1 = new SlimBoardGame(111, "Valid Game 1", null, null, null,
                List.of(validGameCustomFieldValue1));
        final SlimBoardGame validBoardGame2 = new SlimBoardGame(112, "Valid Game 2", null, null, null,
                List.of(validGameCustomFieldValue2));
        final SlimBoardGame duplicateBoardGame = new SlimBoardGame(112, "Valid Game 2", null, null, null,
                List.of(validGameCustomFieldValue3));
        final BoardGameBoxResponseDto validBoardGameBox1 = new BoardGameBoxResponseDto(Keychain.BOARD_GAME_BOX_KEY, 810,
                "Valid Single Game Box", true, false, null, validBoardGame1, null, null, null, List.of(validBoxCustomFieldValue1));
        final BoardGameBoxResponseDto validBoardGameBox2 = new BoardGameBoxResponseDto(Keychain.BOARD_GAME_BOX_KEY, 811,
                "Valid Collection Box", false, true, null, validBoardGame2, null, null, null, List.of(validBoxCustomFieldValue2));
        final BoardGameBoxResponseDto duplicateBoardGameBox = new BoardGameBoxResponseDto(Keychain.BOARD_GAME_BOX_KEY, 812,
                "Valid Collection Box", false, true, null, duplicateBoardGame, null, null, null, List.of());

        List<CustomField> customFieldsList = new ArrayList<>(initialBackupData.customFields());
        final int createdCustomFields = 2;
        customFieldsList.add(validBoardGameBoxCustomField);
        customFieldsList.add(validBoardGameCustomField);
        List<BoardGameBoxResponseDto> boardGameBoxes = new ArrayList<>(initialBackupData.boardGameBoxes());
        final int createdBoardGameBoxes = 2;
        boardGameBoxes.add(validBoardGameBox1);
        boardGameBoxes.add(validBoardGameBox2);
        final BackupDataDto expectedResult = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                new ArrayList<>(boardGameBoxes)
        );
        final int existingBoardGameBoxes = 1; //This will be returned as existing because it shares the same title and board game, NOT because of the provided IDs
        boardGameBoxes.add(duplicateBoardGameBox);
        final BackupDataDto importData = new BackupDataDto(
                customFieldsList,
                initialBackupData.toys(),
                initialBackupData.systems(),
                initialBackupData.videoGameBoxes(),
                boardGameBoxes
        );

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(importData);

        //Assert
        assertAll(
                "Error on duplicate board game box import.",
                () -> assertEquals(createdCustomFields, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(createdBoardGameBoxes, importResult.createdBoardGameBoxes()),
                () -> assertEquals(initialBackupData.boardGameBoxes().size() + existingBoardGameBoxes, importResult.existingBoardGameBoxes())
        );
        final BackupDataDto resultsBackupData = gateway.getBackupData();
        validateBackupData(expectedResult, resultsBackupData);
    }

    @Test
    void exportData_ImportSameData_NoNewDataImported() {

        //Arrange
        final BackupDataDto initialBackupData = gateway.getBackupData();

        //Act
        final ImportResultsDto importResult = gateway.importBackupData(initialBackupData);

        //Assert
        assertAll(
                "Error on idempotent test.",
                () -> assertEquals(0, importResult.createdCustomFields()),
                () -> assertEquals(initialBackupData.customFields().size(), importResult.existingCustomFields()),
                () -> assertEquals(0, importResult.createdToys()),
                () -> assertEquals(initialBackupData.toys().size(), importResult.existingToys()),
                () -> assertEquals(0, importResult.createdSystems()),
                () -> assertEquals(initialBackupData.systems().size(), importResult.existingSystems()),
                () -> assertEquals(0, importResult.createdVideoGamesBoxes()),
                () -> assertEquals(initialBackupData.videoGameBoxes().size(), importResult.existingVideoGamesBoxes()),
                () -> assertEquals(0, importResult.createdBoardGameBoxes()),
                () -> assertEquals(initialBackupData.boardGameBoxes().size(), importResult.existingBoardGameBoxes()),
                () -> assertEquals(0, importResult.exceptionBackupImport().getExceptions().size())
        );
        final BackupDataDto resultsBackupData = gateway.getBackupData();
        validateBackupData(initialBackupData, resultsBackupData);
    }

    // ====================================== Private Validation Methods ======================================

    private void validateBackupData(BackupDataDto expectedBackupData, BackupDataDto actualBackupData) {
        validateCustomFieldBackupData(expectedBackupData, actualBackupData);
        validateToyBackupData(expectedBackupData, actualBackupData);
        validateSystemBackupData(expectedBackupData, actualBackupData);
        validateVideoGameBackupData(expectedBackupData, actualBackupData);
        validateBoardGameBackupData(expectedBackupData, actualBackupData);
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
                    "Mismatched video game box data returned in BackupDataDto.",
                    () -> assertEquals(expectedVideoGame.title(), actualVideoGame.title()),
                    () -> assertEquals(expectedVideoGame.isPhysical(), actualVideoGame.isPhysical()),
                    () -> assertEquals(expectedVideoGame.isCollection(), actualVideoGame.isCollection()),
                    () -> assertEquals(expectedVideoGame.system().name(), actualVideoGame.system().name()),
                    () -> assertEquals(expectedVideoGame.system().generation(), actualVideoGame.system().generation()),
                    () -> assertEquals(expectedVideoGame.system().handheld(), actualVideoGame.system().handheld()),
                    () -> assertEquals(expectedVideoGame.videoGames().size(), actualVideoGame.videoGames().size())
            );
            validateVideoGames(expectedVideoGame.videoGames(), actualVideoGame.videoGames(), expectedVideoGame.title());
            validateCustomFieldValues(expectedVideoGame.customFieldValues(), actualVideoGame.customFieldValues(), Keychain.VIDEO_GAME_BOX_KEY, actualVideoGame.title());
        }
    }

    private void validateVideoGames(List<SlimVideoGame> expectedVideoGames, List<SlimVideoGame> actualVideoGames, String expectedTitle) {
        assertEquals(expectedVideoGames.size(), actualVideoGames.size(), "Unexpected number of video games in video game box");
        for (int i = 0; i < expectedVideoGames.size(); i++) {
            final SlimVideoGame expectedGame = expectedVideoGames.get(i);
            final SlimVideoGame actualGame = actualVideoGames.get(i);
            assertAll(
                    "Mismatched video game data in video game box with title '" + expectedTitle + "'.",
                    () -> assertEquals(expectedGame.title(), actualGame.title()),
                    () -> assertEquals(expectedGame.system().name(), actualGame.system().name()),
                    () -> assertEquals(expectedGame.system().generation(), actualGame.system().generation()),
                    () -> assertEquals(expectedGame.system().handheld(), actualGame.system().handheld())
            );
            validateCustomFieldValues(expectedGame.customFieldValues(), actualGame.customFieldValues(), Keychain.VIDEO_GAME_KEY, actualGame.title());
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

    private void validateBoardGameBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
        final List<BoardGameBoxResponseDto> expectedBoardGameBoxes = expectedData.boardGameBoxes();
        final List<BoardGameBoxResponseDto> actualBoardGameBoxes = actualData.boardGameBoxes();
        if (null == expectedBoardGameBoxes || null == actualBoardGameBoxes) {
            assertAll(
                    "If the expected board game boxes are null then the actual should be as well.",
                    () -> assertNull(expectedBoardGameBoxes),
                    () -> assertNull(actualBoardGameBoxes)
            );
            return;
        }
        assertEquals(expectedBoardGameBoxes.size(), actualBoardGameBoxes.size(), "Unexpected number of board game box results returned in BackupDataDto");
        for (int i = 0; i < expectedBoardGameBoxes.size(); i++) {
            final BoardGameBoxResponseDto expectedBoardGameBox = expectedBoardGameBoxes.get(i);
            final BoardGameBoxResponseDto actualBoardGameBox = actualBoardGameBoxes.get(i);
            assertAll(
                    "Mismatched board game box data returned in BackupDataDto.",
                    () -> assertEquals(expectedBoardGameBox.title(), actualBoardGameBox.title()),
                    () -> assertEquals(expectedBoardGameBox.isExpansion(), actualBoardGameBox.isExpansion()),
                    () -> assertEquals(expectedBoardGameBox.isStandAlone(), actualBoardGameBox.isStandAlone()),
                    () -> assertEquals(expectedBoardGameBox.baseSetId(), actualBoardGameBox.baseSetId()),
                    () -> assertEquals(expectedBoardGameBox.boardGame().title(), actualBoardGameBox.boardGame().title())
            );
            validateCustomFieldValues(expectedBoardGameBox.customFieldValues(), actualBoardGameBox.customFieldValues(), Keychain.BOARD_GAME_BOX_KEY, actualBoardGameBox.title());
        }
    }
}
