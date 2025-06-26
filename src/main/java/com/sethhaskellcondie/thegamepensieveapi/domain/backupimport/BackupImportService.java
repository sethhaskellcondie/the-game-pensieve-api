package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.Toy;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.toy.ToyService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBox;
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
    private final VideoGameService videoGameService;
    private final VideoGameBoxService videoGameBoxService;
    private final BoardGameService boardGameService;
    private final BoardGameBoxService boardGameBoxService;

    protected BackupImportService(CustomFieldRepository customFieldRepository, SystemService systemService, ToyService toyService, VideoGameService videoGameService,
                                  VideoGameBoxService videoGameBoxService, BoardGameService boardGameService, BoardGameBoxService boardGameBoxService) {
        this.customFieldRepository = customFieldRepository;
        this.systemService = systemService;
        this.toyService = toyService;
        this.videoGameService = videoGameService;
        this.videoGameBoxService = videoGameBoxService;
        this.boardGameService = boardGameService;
        this.boardGameBoxService = boardGameBoxService;
    }

    protected BackupDataDto getBackupData() {
        List<CustomField> customFields = customFieldRepository.getAllCustomFields();
        List<ToyResponseDto> toys = toyService.getWithFilters(new ArrayList<>()).stream().map(Toy::convertToResponseDto).toList();
        List<SystemResponseDto> systems = systemService.getWithFilters(new ArrayList<>()).stream().map(System::convertToResponseDto).toList();
        List<VideoGameResponseDto> videoGames = videoGameService.getWithFilters(new ArrayList<>()).stream().map(VideoGame::convertToResponseDto).toList();
        List<VideoGameBoxResponseDto> videoGameBoxes = videoGameBoxService.getWithFilters(new ArrayList<>()).stream().map(VideoGameBox::convertToResponseDto).toList();
        List<BoardGameResponseDto> boardGames = boardGameService.getWithFilters(new ArrayList<>()).stream().map(BoardGame::convertToResponseDto).toList();
        List<BoardGameBoxResponseDto> boardGameBoxes = boardGameBoxService.getWithFilters(new ArrayList<>()).stream().map(BoardGameBox::convertToResponseDto).toList();

        return new BackupDataDto(customFields, toys, systems, videoGames, videoGameBoxes, boardGames, boardGameBoxes);
    }

    protected ImportResultsDto importBackupData(BackupDataDto backupDataDto) {
        final Map<Integer, Integer> customFieldIds;
        final ImportCustomFieldsResults customFieldResults;
        customFieldResults = importCustomFields(backupDataDto);
        if (!customFieldResults.exceptionBackupImport().getExceptions().isEmpty()) {
            ExceptionBackupImport customFieldsException = new ExceptionBackupImport("There were errors importing Custom Fields. No additional data imported.");
            customFieldsException.appendExceptions(customFieldResults.exceptionBackupImport().getExceptions());
            return new ImportResultsDto(customFieldResults.existingCount(), customFieldResults.createdCount(), customFieldsException);
        }

        customFieldIds = customFieldResults.customFieldIds();
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();

        ImportEntityResults toyResults = importToys(backupDataDto, customFieldIds);
        if (!toyResults.exceptionBackupImport().getExceptions().isEmpty()) {
            exceptionBackupImport.appendExceptions(toyResults.exceptionBackupImport().getExceptions());
        }

        ImportEntityResults systemResults = importSystems(backupDataDto, customFieldIds);
        if (!systemResults.exceptionBackupImport().getExceptions().isEmpty()) {
            exceptionBackupImport.appendExceptions(systemResults.exceptionBackupImport().getExceptions());
        }

//        ImportEntityResults videoGameResults = importVideoGames(backupDataDto, customFieldIds);
//        if (!videoGameResults.exceptionBackupImport().isEmpty()) {
//            exceptionBackupImport.appendExceptions(videoGameResults.exceptionBackupImport().getExceptions());
//        }
//
//        if (!exceptionBackupImport.getExceptions().isEmpty()) {
//            ExceptionBackupImport importException = new ExceptionBackupImport("There were errors importing Entity data, all valid data was imported, data with errors was skipped.");
//            importException.appendExceptions(exceptionBackupImport.getExceptions());
//            exceptionBackupImport = importException;
//        }

        return new ImportResultsDto(
                customFieldResults.existingCount(), customFieldResults.createdCount(),
                toyResults.existingCount(), toyResults.createdCount(),
                systemResults.existingCount(), systemResults.createdCount(),
//                videoGameResults.existingCount(), videoGameResults.createdCount(),
                0, 0,
                exceptionBackupImport
        );
    }

    private ImportCustomFieldsResults importCustomFields(BackupDataDto backupDataDto) {
        final ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;

        try {
            validateCustomFieldIds(backupDataDto);
        } catch (Exception exception) {
            exceptionBackupImport.addException(exception);
        }
        if (!exceptionBackupImport.getExceptions().isEmpty()) {
            return new ImportCustomFieldsResults(new HashMap<>(), existingCount, createdCount, exceptionBackupImport);
        }
        
        final List<CustomField> customFields = backupDataDto.customFields();
        final Map<Integer, Integer> customFieldIds = new HashMap<>(customFields.size());

        for (CustomField customField : customFields) {
            CustomField savedCustomField;
            try {
                savedCustomField = customFieldRepository.getByKeyAndName(customField.entityKey(), customField.name());
                if (!Objects.equals(savedCustomField.type(), customField.type())) {
                    exceptionBackupImport.addException(new Exception("Error Importing Custom Field Data: Provided custom field with name: '"
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
                    exceptionBackupImport.addException(new Exception("Error Importing Custom Field Data: Provided custom field with name: '"
                            + customField.name() + "' Message: " + exception.getMessage()));
                }
            }
            if (null != savedCustomField) {
                //The first ID comes from the import and is used to determine relationships with other objects to be imported, and the second is the new ID after being written to the database.
                customFieldIds.put(customField.id(), savedCustomField.id());
            }
        }
        return new ImportCustomFieldsResults(customFieldIds, existingCount, createdCount, exceptionBackupImport);
    }

    private ImportEntityResults importToys(BackupDataDto backupDataDto, Map<Integer, Integer> customFieldIds) {
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;
        List<ToyResponseDto> toysToBeImported = backupDataDto.toys();
        if (null == toysToBeImported) {
            return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
        }

        List<ToyResponseDto> toyRequestsReady = new ArrayList<>(toysToBeImported.size());
        for (ToyResponseDto toyResponseDto : toysToBeImported) {
            boolean skipped = false;
            for (CustomFieldValue value: toyResponseDto.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addException(new Exception("Error Importing Toy Data: Custom field relationships not found but expected for toy with name: '"
                        + toyResponseDto.name() + "' and set '" + toyResponseDto.set() + "' with custom field value named '" + value.getCustomFieldName()
                        + "' and custom field ID provided on the toy as '" + value.getCustomFieldId() + "'."));
                } else {
                    value.setCustomFieldId(customFieldId);
                }
            }
            if (!skipped) {
                toyRequestsReady.add(toyResponseDto);
            }
        }

        for (ToyResponseDto toyResponseDto: toyRequestsReady) {
            try {
                int toyId = toyService.getIdByNameAndSet(toyResponseDto.name(), toyResponseDto.set());
                if (toyId > 0) {
                    existingCount++;
                } else {
                    toyService.createNew(
                            new ToyRequestDto(
                                    toyResponseDto.name(),
                                    toyResponseDto.set(),
                                    toyResponseDto.customFieldValues()
                            )
                    );
                    createdCount++;
                }
            } catch (Exception exception) {
                exceptionBackupImport.addException(new Exception("Error importing toy data with name: '" + toyResponseDto.name()
                        + "' and set '" + toyResponseDto.set() + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
    }

    private ImportEntityResults importSystems(BackupDataDto backupDataDto, Map<Integer, Integer> customFieldIds) {
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;
        List<SystemResponseDto> systemRequestToBeUpdated = backupDataDto.systems();
        if (null == systemRequestToBeUpdated) {
            return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
        }

        List<SystemResponseDto> systemRequestsReady = new ArrayList<>(systemRequestToBeUpdated.size());
        for (SystemResponseDto systemResponseDto: systemRequestToBeUpdated) {
            boolean skipped = false;
            for (CustomFieldValue value: systemResponseDto.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addException(new Exception("Error importing system data: Imported Custom Field not found but expected for system named: '"
                        + systemResponseDto.name() + "' with custom field value named '" + value.getCustomFieldName()
                        + "' The custom field must be included on the import and not just existing in the database."));
                } else {
                    value.setCustomFieldId(customFieldId);
                }
            }
            if (!skipped) {
                systemRequestsReady.add(systemResponseDto);
            }
        }

        for (SystemResponseDto systemResponseDto: systemRequestsReady) {
            try {
                int systemId = systemService.getIdByName(systemResponseDto.name());
                if (systemId > 0) {
                    existingCount++;
                } else {
                    systemService.createNew(new SystemRequestDto(
                            systemResponseDto.name(),
                            systemResponseDto.generation(),
                            systemResponseDto.handheld(),
                            systemResponseDto.customFieldValues()
                    ));
                    createdCount++;
                }
            } catch (Exception exception) {
                exceptionBackupImport.addException(new Exception("Error importing system data with name: '" + systemResponseDto.name()
                        + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
    }

//    private ImportEntityResults importVideoGames(BackupDataDto backupDataDto, Map<Integer, Integer> customFieldIds) {
//        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
//        int existingCount = 0;
//        int createdCount = 0;
//
//        List<VideoGameRequestDto> videoGameData = backupDataDto.videoGames();
//        if (null == videoGameData) {
//            return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
//        }
//        List<VideoGameRequestDto> videoGamesToBeImported = new ArrayList<>(videoGameData.size());
//        for (VideoGameRequestDto videoGameRequestDto: videoGameData) {
//            boolean skipped = false;
//            for (CustomFieldValue value: videoGameRequestDto.customFieldValues()) {
//                Integer customFieldId = customFieldIds.get(customFieldComboKey(Keychain.VIDEO_GAME_KEY, value));
//                if (null == customFieldId) {
//                    skipped = true;
//                    exceptionBackupImport.addException(new Exception("Error importing video game data: Imported Custom Field not found but expected for video game with title: '"
//                            + videoGameRequestDto.title() + "' with custom field value named '" + value.getCustomFieldName()
//                            + "' The custom field must be included on the import and not just existing in the database."));
//                } else {
//                    value.setCustomFieldId(customFieldId);
//                }
//            }
//            if (!skipped) {
//                videoGamesToBeImported.add(videoGameRequestDto);
//            }
//        }
//
//        for (VideoGameRequestDto videoGameRequestDto: videoGamesToBeImported) {
//            try {
//                int videoGameId = videoGameService.getIdByTitleAndSystem(videoGameRequestDto.title(), videoGameRequestDto.systemId());
//                if (videoGameId > 0) {
//                    VideoGame videoGame = videoGameService.getById(videoGameId);
//                    videoGame.updateFromRequestDto(videoGameRequestDto);
//                    videoGameService.updateExisting(videoGameId, videoGameRequestDto);
//                    existingCount++;
//                } else {
//                    videoGameService.createNew(videoGameRequestDto);
//                    createdCount++;
//                }
//            } catch (Exception exception) {
//                exceptionBackupImport.addException(new Exception("Error importing video game data with title: '" + videoGameRequestDto.title()
//                        + "' " + exception.getMessage()));
//            }
//        }
//        return new ImportEntityResults(existingCount, createdCount, exceptionBackupImport);
//    }

    private void validateCustomFieldIds(BackupDataDto backupDataDto) throws Exception {
        final List<CustomField> customFields = backupDataDto.customFields();
        if (null == customFields || customFields.isEmpty()) {
            return;
        }

        List<Integer> seenIds = new ArrayList<>();
        for (CustomField customField : customFields) {
            int customFieldId = customField.id();

            if (customFieldId <= 0) {
                throw new Exception("Error Importing Custom Field Data: Custom field with name '" + customField.name() 
                        + "' and entity key '" + customField.entityKey() + "' has an invalid ID. ID must be a positive integer, but was: " + customFieldId);
            }

            if (seenIds.contains(customFieldId)) {
                throw new Exception("Error Importing Custom Field Data: Duplicate custom field ID found: " + customFieldId 
                        + ". Each custom field must have a unique ID in the import data.");
            }
            
            seenIds.add(customFieldId);
        }
    }
}

record ImportCustomFieldsResults(Map<Integer, Integer> customFieldIds, int existingCount, int createdCount, ExceptionBackupImport exceptionBackupImport) { }
record ImportEntityResults(int existingCount, int createdCount, ExceptionBackupImport exceptionBackupImport) { }

