package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.Toy;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.*;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxService;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionBackupImport;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BackupImportService {
    private final CustomFieldRepository customFieldRepository;
    private final SystemService systemService;
    private final ToyService toyService;
    private final VideoGameBoxService videoGameBoxService;

    protected BackupImportService(CustomFieldRepository customFieldRepository, SystemService systemService, ToyService toyService,
                                  VideoGameBoxService videoGameBoxService) {
        this.customFieldRepository = customFieldRepository;
        this.systemService = systemService;
        this.toyService = toyService;
        this.videoGameBoxService = videoGameBoxService;
    }

    protected BackupDataDto getBackupData() {
        List<CustomField> customFields = customFieldRepository.getAllCustomFields();
        List<ToyResponseDto> toys = toyService.getWithFilters(new ArrayList<>()).stream().map(Toy::convertToResponseDto).toList();
        List<SystemResponseDto> systems = systemService.getWithFilters(new ArrayList<>()).stream().map(System::convertToResponseDto).toList();
        List<VideoGameBoxResponseDto> videoGameBoxes = videoGameBoxService.getWithFilters(new ArrayList<>()).stream().map(VideoGameBox::convertToResponseDto).toList();

        return new BackupDataDto(customFields, toys, systems, videoGameBoxes);
    }

    protected ImportResultsDto importBackupData(BackupDataDto backupDataDto) {
        final Map<Integer, Integer> customFieldIds;
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();

        final ImportEntityResults customFieldResults = importCustomFields(backupDataDto.customFields(), exceptionBackupImport);
        //There is no point trying to import the rest of the data if the custom fields are broken, if any error are detected then return early importing no data.
        if (!exceptionBackupImport.getExceptions().isEmpty()) {
            exceptionBackupImport.setHeader("There were errors importing Custom Fields. No additional data imported.");
            return new ImportResultsDto(customFieldResults.existingCount(), customFieldResults.createdCount(), exceptionBackupImport);
        }
        customFieldIds = customFieldResults.entityIds();

        ImportEntityResults toyResults = importToys(backupDataDto.toys(), customFieldIds, exceptionBackupImport);

        ImportEntityResults systemResults = importSystems(backupDataDto.systems(), customFieldIds, exceptionBackupImport);

        ImportEntityResults videoGameBoxResults = importVideoGameBoxes(backupDataDto.videoGameBoxes(), customFieldIds, systemResults.entityIds(), exceptionBackupImport);

        if (!exceptionBackupImport.getExceptions().isEmpty()) {
            exceptionBackupImport.setHeader("There were errors importing Entity data, all valid data was imported, data with errors was skipped.");
        }

        return new ImportResultsDto(
                customFieldResults.existingCount(), customFieldResults.createdCount(),
                toyResults.existingCount(), toyResults.createdCount(),
                systemResults.existingCount(), systemResults.createdCount(),
                videoGameBoxResults.existingCount(), videoGameBoxResults.createdCount(),
                exceptionBackupImport
        );
    }

    private ImportEntityResults importCustomFields(final List<CustomField> customFields, ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;

        validateCustomFieldIds(customFields, exceptionBackupImport);
        if (!exceptionBackupImport.getExceptions().isEmpty()) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
        }

        final Map<Integer, Integer> customFieldIds = new HashMap<>(customFields.size());
        for (CustomField customField : customFields) {
            CustomField savedCustomField;
            try {
                savedCustomField = customFieldRepository.getByKeyAndName(customField.entityKey(), customField.name());
                if (!Objects.equals(savedCustomField.type(), customField.type())) {
                    exceptionBackupImport.addCustomFieldException(new Exception("Error Importing Custom Field Data: Provided custom field with name: '"
                            + customField.name() + "' and key: '" + customField.entityKey() + "' had a type mismatch with the existing custom field in the database. Provided type: '"
                            + customField.type() + "' existing (correct) type: '" + savedCustomField.type() + "'"));
                } else {
                    existingCount++;
                }
            } catch (ExceptionResourceNotFound ignored) {
                savedCustomField = null;
            }
            if (null == savedCustomField) {
                try {
                    savedCustomField = customFieldRepository.insertCustomField(new CustomFieldRequestDto(customField.name(), customField.type(), customField.entityKey()));
                    createdCount++;
                } catch (Exception exception) {
                    exceptionBackupImport.addCustomFieldException(new Exception("Error Importing Custom Field Data: Provided custom field with name: '"
                            + customField.name() + "' Message: " + exception.getMessage()));
                }
            }
            if (null != savedCustomField) {
                //The first ID comes from the import and is used to determine relationships with other objects to be imported, and the second is the new ID after being written to the database.
                customFieldIds.put(customField.id(), savedCustomField.id());
            }
        }
        return new ImportEntityResults(customFieldIds, existingCount, createdCount);
    }

    private void validateCustomFieldIds(final List<CustomField> customFields, ExceptionBackupImport exceptionBackupImport) {
        if (null == customFields || customFields.isEmpty()) {
            return;
        }

        List<Integer> seenIds = new ArrayList<>();
        for (CustomField customField : customFields) {
            int customFieldId = customField.id();

            if (customFieldId <= 0) {
                exceptionBackupImport.addCustomFieldException("Error Importing Custom Field Data: Custom field with name '" + customField.name()
                        + "' and entity key '" + customField.entityKey() + "' has an invalid ID. ID must be a positive integer, but was: " + customFieldId);
            }

            if (seenIds.contains(customFieldId)) {
                exceptionBackupImport.addCustomFieldException("Error Importing Custom Field Data: Duplicate custom field ID found: " + customFieldId
                        + ". Each custom field must have a unique ID in the import data.");
            }

            seenIds.add(customFieldId);
        }
    }

    private ImportEntityResults importToys(List<ToyResponseDto> toysToBeImported, Map<Integer, Integer> customFieldIds, ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;
        if (null == toysToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
        }
        Map<Integer, Integer> toyIds = new HashMap<>(toysToBeImported.size());

        List<ToyResponseDto> validatedToys = new ArrayList<>(toysToBeImported.size());
        for (ToyResponseDto validatingToy : toysToBeImported) {
            boolean skipped = false;
            for (CustomFieldValue value: validatingToy.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addToyException(new Exception("Error Importing Toy Data: Custom field relationships not found but expected for toy with name: '"
                        + validatingToy.name() + "' and set '" + validatingToy.set() + "' with custom field value named '" + value.getCustomFieldName()
                        + "' and custom field ID provided on the toy as '" + value.getCustomFieldId() + "'."));
                } else {
                    value.setCustomFieldId(customFieldId);
                }
            }
            if (!skipped) {
                validatedToys.add(validatingToy);
            }
        }

        for (ToyResponseDto importToy: validatedToys) {
            try {
                int toyId = toyService.getIdByNameAndSet(importToy.name(), importToy.set());
                if (toyId > 0) {
                    existingCount++;
                    toyIds.put(importToy.id(), toyId);
                } else {
                    Toy createdToy = toyService.createNew(
                            new ToyRequestDto(
                                    importToy.name(),
                                    importToy.set(),
                                    importToy.customFieldValues()
                            )
                    );
                    createdCount++;
                    toyIds.put(importToy.id(), createdToy.getId());
                }
            } catch (Exception exception) {
                exceptionBackupImport.addToyException(new Exception("Error importing toy data with name: '" + importToy.name()
                        + "' and set '" + importToy.set() + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(toyIds, existingCount, createdCount);
    }

    private ImportEntityResults importSystems(List<SystemResponseDto> systemsToBeImported, Map<Integer, Integer> customFieldIds, ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;
        if (null == systemsToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
        }
        Map<Integer, Integer> systemIds = new HashMap<>(systemsToBeImported.size());

        List<SystemResponseDto> validatedSystems = new ArrayList<>(systemsToBeImported.size());
        for (SystemResponseDto validatingSystem: systemsToBeImported) {
            boolean skipped = false;
            for (CustomFieldValue value: validatingSystem.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addSystemException(new Exception("Error importing system data: Imported Custom Field not found but expected for system named: '"
                        + validatingSystem.name() + "' with custom field value named '" + value.getCustomFieldName()
                        + "' The custom field must be included on the import and not just existing in the database."));
                } else {
                    value.setCustomFieldId(customFieldId);
                }
            }
            if (!skipped) {
                validatedSystems.add(validatingSystem);
            }
        }

        for (SystemResponseDto importSystem: validatedSystems) {
            try {
                int systemId = systemService.getIdByName(importSystem.name());
                if (systemId > 0) {
                    existingCount++;
                    systemIds.put(importSystem.id(), systemId);
                } else {
                    System createdSystem = systemService.createNew(new SystemRequestDto(
                            importSystem.name(),
                            importSystem.generation(),
                            importSystem.handheld(),
                            importSystem.customFieldValues()
                    ));
                    createdCount++;
                    systemIds.put(importSystem.id(), createdSystem.getId());
                }
            } catch (Exception exception) {
                exceptionBackupImport.addSystemException(new Exception("Error importing system data with name: '" + importSystem.name()
                        + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(systemIds, existingCount, createdCount);
    }

    private ImportEntityResults importVideoGameBoxes(List<VideoGameBoxResponseDto> videoGameBoxToBeImported, Map<Integer, Integer> customFieldIds, Map<Integer, Integer> systemIds, ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;
        if (null == videoGameBoxToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
        }
        Map<Integer, Integer> boxIds = new HashMap<>(videoGameBoxToBeImported.size());
        List<VideoGameBoxResponseDto> validatedBoxes = validateVideoGameBoxes(videoGameBoxToBeImported, customFieldIds, systemIds, exceptionBackupImport);
        Map<Integer, Integer> gameIds = new HashMap<>(videoGameBoxToBeImported.size());

        for (VideoGameBoxResponseDto importBox: validatedBoxes) {
            try {
                int boxId = videoGameBoxService.getIdByTitleAndSystemId(importBox.title(), importBox.system().id());
                if (boxId > 0) {
                    existingCount++;
                    boxIds.put(importBox.id(), boxId);
                    continue;
                }

                ValidatedVideoGameResults validatedVideoGameResults = validateVideoGames(importBox.videoGames(), customFieldIds, systemIds, gameIds, exceptionBackupImport);
                if (validatedVideoGameResults.newGames().isEmpty() && validatedVideoGameResults.existingIds().isEmpty()) {
                    exceptionBackupImport.addVideoGameBoxException(new Exception("Error importing video game box data: Video Game Box with title: '"
                            + importBox.title() + "' had no valid Video Games included with the import data. Video Game Boxes must include at least one valid video game."));
                    continue;
                }

                VideoGameBox createdGameBox = videoGameBoxService.createNew(new VideoGameBoxRequestDto(
                    importBox.title(),
                    importBox.system().id(),
                    validatedVideoGameResults.existingIds(),
                    validatedVideoGameResults.newGames().values().stream().toList(),
                    importBox.isPhysical(),
                    importBox.customFieldValues()
                ));
                createdCount++;

                //Assuming that games in the same collection do NOT have the same name AND system
                for (Map.Entry<Integer, VideoGameRequestDto> entry : validatedVideoGameResults.newGames().entrySet()) {
                    int originalSlimVideoGameId = entry.getKey();
                    VideoGameRequestDto gameRequest = entry.getValue();
                    for (SlimVideoGame createdGame : createdGameBox.getVideoGames()) {
                        if (createdGame.title().equals(gameRequest.title()) && createdGame.system().id() == gameRequest.systemId()) {
                            gameIds.put(originalSlimVideoGameId, createdGame.id());
                            break;
                        }
                    }
                }

                boxIds.put(importBox.id(), createdGameBox.getId());

            } catch (Exception exception) {
                exceptionBackupImport.addVideoGameBoxException(new Exception("Error importing video game box data with title: '" + importBox.title()
                        + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(boxIds, existingCount, createdCount);
    }

    private List<VideoGameBoxResponseDto> validateVideoGameBoxes(List<VideoGameBoxResponseDto> videoGameBoxes, Map<Integer, Integer> customFieldIds, Map<Integer, Integer> systemIds, ExceptionBackupImport exceptionBackupImport) {

        List<VideoGameBoxResponseDto> validatedBoxes = new ArrayList<>(videoGameBoxes.size());
        for (VideoGameBoxResponseDto videoGameBox : videoGameBoxes) {
            boolean skipped = false;
            Integer systemId = systemIds.get(videoGameBox.system().id());
            if (null == systemId) {
                skipped = true;
                exceptionBackupImport.addVideoGameBoxException(new Exception("Error importing video game box data: Imported System not found but expected for video game box titled: '"
                    + videoGameBox.title() + "' with system ID '" + videoGameBox.system().id() + "'. The system must be included on the import and not just existing in the database."));
            } else {
                //Since records are immutable we need to create a new dto with the proper relationship
                SystemResponseDto updatedSystem = new SystemResponseDto(
                    videoGameBox.system().key(),
                    systemId,
                    videoGameBox.system().name(),
                    videoGameBox.system().generation(),
                    videoGameBox.system().handheld(),
                    videoGameBox.system().createdAt(),
                    videoGameBox.system().updatedAt(),
                    videoGameBox.system().deletedAt(),
                    videoGameBox.system().customFieldValues()
                );
                videoGameBox = new VideoGameBoxResponseDto(
                    videoGameBox.key(),
                    videoGameBox.id(),
                    videoGameBox.title(),
                    updatedSystem,
                    videoGameBox.videoGames(),
                    videoGameBox.isPhysical(),
                    videoGameBox.isCollection(),
                    videoGameBox.createdAt(),
                    videoGameBox.updatedAt(),
                    videoGameBox.deletedAt(),
                    videoGameBox.customFieldValues()
                );
            }

            for (CustomFieldValue value : videoGameBox.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addVideoGameBoxException(new Exception("Error importing video game box data: Imported Custom Field not found but expected for video game box titled: '"
                        + videoGameBox.title() + "' with custom field value named '" + value.getCustomFieldName()
                        + "' The custom field must be included on the import and not just existing in the database."));
                } else {
                    value.setCustomFieldId(customFieldId);
                }
            }

            if (videoGameBox.videoGames().isEmpty()) {
                skipped = true;
                exceptionBackupImport.addVideoGameBoxException(new Exception("Error import video game box data: Imported video game box with title: '"
                + videoGameBox.title() + "' had no included video games, video game boxes must include at least one video game."));
            }
            
            if (!skipped) {
                validatedBoxes.add(videoGameBox);
            }
        }
        
        return validatedBoxes;
    }

    private ValidatedVideoGameResults validateVideoGames(List<SlimVideoGame> videoGamesToImport, Map<Integer, Integer> customFieldIds, Map<Integer, Integer> systemIds, Map<Integer, Integer> videoGameIds, ExceptionBackupImport exceptionBackupImport) {
        List<Integer> importedVideoGamesIds = new ArrayList<>(videoGamesToImport.size());
        List<SlimVideoGame> gamesToValidate = new ArrayList<>(videoGamesToImport.size());
        Map<Integer, VideoGameRequestDto> newGames = new HashMap<>(videoGamesToImport.size());

        for (SlimVideoGame slimVideoGame : videoGamesToImport) {
            Integer existingGameId = videoGameIds.get(slimVideoGame.id());
            if (null != existingGameId) {
                importedVideoGamesIds.add(existingGameId);
            } else {
                gamesToValidate.add(slimVideoGame);
            }
        }

        for (SlimVideoGame validatingGame : gamesToValidate) {
            try {
                boolean skipped = false;
                Integer systemId = systemIds.get(validatingGame.system().id());
                if (null == systemId) {
                    skipped = true;
                    exceptionBackupImport.addVideoGameException(new Exception("Error importing video game data: Imported System not found but expected for video game titled: '"
                            + validatingGame.title() + "' with system ID '" + validatingGame.system().id() + "'. The system must be included on the import and not just existing in the database."));
                } else {
                    //Since records are immutable we need to create a new dto with the proper relationship
                    SystemResponseDto updatedSystem = new SystemResponseDto(
                            validatingGame.system().key(),
                            systemId,
                            validatingGame.system().name(),
                            validatingGame.system().generation(),
                            validatingGame.system().handheld(),
                            validatingGame.system().createdAt(),
                            validatingGame.system().updatedAt(),
                            validatingGame.system().deletedAt(),
                            validatingGame.system().customFieldValues()
                    );
                    validatingGame = new SlimVideoGame(
                            validatingGame.id(),
                            validatingGame.title(),
                            updatedSystem,
                            validatingGame.createdAt(),
                            validatingGame.updatedAt(),
                            validatingGame.deletedAt(),
                            validatingGame.customFieldValues()
                    );
                }

                for (CustomFieldValue value : validatingGame.customFieldValues()) {
                    Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                    if (null == customFieldId) {
                        skipped = true;
                        exceptionBackupImport.addVideoGameException(new Exception("Error importing video game data: Imported Custom Field not found but expected for video game titled: '"
                            + validatingGame.title() + "' with custom field value named '" + value.getCustomFieldName()
                            + "' The custom field must be included on the import and not just existing in the database."));
                    } else {
                        value.setCustomFieldId(customFieldId);
                    }
                }

                if (!skipped) {
                    VideoGameRequestDto gameToBeCreated = new VideoGameRequestDto(
                            validatingGame.title(),
                            validatingGame.system().id(),
                            validatingGame.customFieldValues()
                    );
                    newGames.put(validatingGame.id(), gameToBeCreated);
                }
            } catch (Exception exception) {
                exceptionBackupImport.addVideoGameException(exception);
            }
        }
        
        return new ValidatedVideoGameResults(importedVideoGamesIds, newGames);
    }
}

record ImportEntityResults(Map<Integer, Integer> entityIds, int existingCount, int createdCount) { }
record ValidatedVideoGameResults(List<Integer> existingIds, Map<Integer, VideoGameRequestDto> newGames ) { }
