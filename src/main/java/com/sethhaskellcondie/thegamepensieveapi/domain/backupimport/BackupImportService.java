package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

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
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.*;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxService;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionBackupImport;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.MultiException;
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

        return new BackupDataDto(customFields, toys, systems, videoGameBoxes);
    }

    protected ImportResultsDto importBackupData(BackupDataDto backupDataDto) {
        final Map<Integer, Integer> customFieldIds;
        final ImportEntityResults customFieldResults;
        customFieldResults = importCustomFields(backupDataDto);
        if (!customFieldResults.exceptionBackupImport().getExceptions().isEmpty()) {
            ExceptionBackupImport customFieldsException = new ExceptionBackupImport("There were errors importing Custom Fields. No additional data imported.");
            customFieldsException.appendExceptions(customFieldResults.exceptionBackupImport().getExceptions());
            return new ImportResultsDto(customFieldResults.existingCount(), customFieldResults.createdCount(), customFieldsException);
        }

        customFieldIds = customFieldResults.entityIds();
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();

        ImportEntityResults toyResults = importToys(backupDataDto, customFieldIds);
        if (!toyResults.exceptionBackupImport().getExceptions().isEmpty()) {
            exceptionBackupImport.appendExceptions(toyResults.exceptionBackupImport().getExceptions());
        }

        ImportEntityResults systemResults = importSystems(backupDataDto, customFieldIds);
        if (!systemResults.exceptionBackupImport().getExceptions().isEmpty()) {
            exceptionBackupImport.appendExceptions(systemResults.exceptionBackupImport().getExceptions());
        }

        ImportEntityResults videoGameBoxResults = importVideoGameBoxes(backupDataDto, customFieldIds, systemResults.entityIds());
        if (!videoGameBoxResults.exceptionBackupImport().isEmpty()) {
            exceptionBackupImport.appendExceptions(videoGameBoxResults.exceptionBackupImport().getExceptions());
        }

        if (!exceptionBackupImport.getExceptions().isEmpty()) {
            ExceptionBackupImport importException = new ExceptionBackupImport("There were errors importing Entity data, all valid data was imported, data with errors was skipped.");
            importException.appendExceptions(exceptionBackupImport.getExceptions());
            exceptionBackupImport = importException;
        }

        return new ImportResultsDto(
                customFieldResults.existingCount(), customFieldResults.createdCount(),
                toyResults.existingCount(), toyResults.createdCount(),
                systemResults.existingCount(), systemResults.createdCount(),
                videoGameBoxResults.existingCount(), videoGameBoxResults.createdCount(),
                exceptionBackupImport
        );
    }

    private ImportEntityResults importCustomFields(BackupDataDto backupDataDto) {
        final ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        int existingCount = 0;
        int createdCount = 0;

        try {
            validateCustomFieldIds(backupDataDto);
        } catch (MultiException multiException) {
            exceptionBackupImport.appendExceptions(multiException.getExceptions());
        }
        if (!exceptionBackupImport.getExceptions().isEmpty()) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount, exceptionBackupImport);
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
        return new ImportEntityResults(customFieldIds, existingCount, createdCount, exceptionBackupImport);
    }

    private void validateCustomFieldIds(BackupDataDto backupDataDto) throws MultiException {
        final List<CustomField> customFields = backupDataDto.customFields();
        if (null == customFields || customFields.isEmpty()) {
            return;
        }

        MultiException multiException = new MultiException();
        List<Integer> seenIds = new ArrayList<>();

        for (CustomField customField : customFields) {
            int customFieldId = customField.id();

            if (customFieldId <= 0) {
                multiException.addException("Error Importing Custom Field Data: Custom field with name '" + customField.name()
                        + "' and entity key '" + customField.entityKey() + "' has an invalid ID. ID must be a positive integer, but was: " + customFieldId);
            }

            if (seenIds.contains(customFieldId)) {
                multiException.addException("Error Importing Custom Field Data: Duplicate custom field ID found: " + customFieldId
                        + ". Each custom field must have a unique ID in the import data.");
            }

            seenIds.add(customFieldId);
        }

        if (!multiException.isEmpty()) {
            throw multiException;
        }
    }

    private ImportEntityResults importToys(BackupDataDto backupDataDto, Map<Integer, Integer> customFieldIds) {
        int existingCount = 0;
        int createdCount = 0;
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        List<ToyResponseDto> toysToBeImported = backupDataDto.toys();
        if (null == toysToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount, exceptionBackupImport);
        }
        Map<Integer, Integer> toyIds = new HashMap<>(backupDataDto.toys().size());

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
                    Toy createdToy = toyService.createNew(
                            new ToyRequestDto(
                                    toyResponseDto.name(),
                                    toyResponseDto.set(),
                                    toyResponseDto.customFieldValues()
                            )
                    );
                    createdCount++;
                    toyIds.put(toyResponseDto.id(), createdToy.getId());
                }
            } catch (Exception exception) {
                exceptionBackupImport.addException(new Exception("Error importing toy data with name: '" + toyResponseDto.name()
                        + "' and set '" + toyResponseDto.set() + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(toyIds, existingCount, createdCount, exceptionBackupImport);
    }

    private ImportEntityResults importSystems(BackupDataDto backupDataDto, Map<Integer, Integer> customFieldIds) {
        int existingCount = 0;
        int createdCount = 0;
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        List<SystemResponseDto> systemRequestToBeImported = backupDataDto.systems();
        if (null == systemRequestToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount, exceptionBackupImport);
        }
        Map<Integer, Integer> systemIds = new HashMap<>(backupDataDto.systems().size());

        List<SystemResponseDto> systemRequestsReady = new ArrayList<>(systemRequestToBeImported.size());
        for (SystemResponseDto systemResponseDto: systemRequestToBeImported) {
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
                    System createdSystem = systemService.createNew(new SystemRequestDto(
                            systemResponseDto.name(),
                            systemResponseDto.generation(),
                            systemResponseDto.handheld(),
                            systemResponseDto.customFieldValues()
                    ));
                    createdCount++;
                    systemIds.put(systemResponseDto.id(), createdSystem.getId());
                }
            } catch (Exception exception) {
                exceptionBackupImport.addException(new Exception("Error importing system data with name: '" + systemResponseDto.name()
                        + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(systemIds, existingCount, createdCount, exceptionBackupImport);
    }

    private ImportEntityResults importVideoGameBoxes(BackupDataDto backupDataDto, Map<Integer, Integer> customFieldIds, Map<Integer, Integer> systemIds) {
        int existingCount = 0;
        int createdCount = 0;
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();
        List<VideoGameBoxResponseDto> videoGameBoxToBeImported = backupDataDto.videoGameBoxes();
        if (null == videoGameBoxToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount, exceptionBackupImport);
        }
        Map<Integer, Integer> videoGameBoxIds = new HashMap<>(videoGameBoxToBeImported.size());
        List<VideoGameBoxResponseDto> validatedBoxes = validateVideoGameBoxes(videoGameBoxToBeImported, customFieldIds, systemIds, exceptionBackupImport);

        for (VideoGameBoxResponseDto videoGameBoxResponseDto: validatedBoxes) {
            try {
                int boxId = videoGameBoxService.getIdByTitleAndSystemId(videoGameBoxResponseDto.title(), videoGameBoxResponseDto.system().id());
                if (boxId > 0) {
                    existingCount++;
                } else {
                    List<Integer> existingGameIds = importVideoGames(videoGameBoxResponseDto.videoGames(), customFieldIds, systemIds, exceptionBackupImport);
                    VideoGameBox createdGameBox = videoGameBoxService.createNew(new VideoGameBoxRequestDto(
                        videoGameBoxResponseDto.title(),
                        videoGameBoxResponseDto.system().id(),
                        existingGameIds,
                        new ArrayList<>(),
                        videoGameBoxResponseDto.isPhysical(),
                        videoGameBoxResponseDto.customFieldValues()
                    ));
                    createdCount++;
                    videoGameBoxIds.put(videoGameBoxResponseDto.id(), createdGameBox.getId());
                }
            } catch (Exception exception) {
                exceptionBackupImport.addException(new Exception("Error importing video game box data with title: '" + videoGameBoxResponseDto.title()
                        + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(videoGameBoxIds, existingCount, createdCount, exceptionBackupImport);
    }

    private List<VideoGameBoxResponseDto> validateVideoGameBoxes(List<VideoGameBoxResponseDto> videoGameBoxes, Map<Integer, Integer> customFieldIds, Map<Integer, Integer> systemIds, ExceptionBackupImport exceptionBackupImport) {
        List<VideoGameBoxResponseDto> validatedBoxes = new ArrayList<>(videoGameBoxes.size());
        
        for (VideoGameBoxResponseDto videoGameBox : videoGameBoxes) {
            boolean skipped = false;
            Integer systemId = systemIds.get(videoGameBox.system().id());
            if (null == systemId) {
                skipped = true;
                exceptionBackupImport.addException(new Exception("Error importing video game box data: Imported System not found but expected for video game box titled: '"
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
                    exceptionBackupImport.addException(new Exception("Error importing video game box data: Imported Custom Field not found but expected for video game box titled: '"
                        + videoGameBox.title() + "' with custom field value named '" + value.getCustomFieldName()
                        + "' The custom field must be included on the import and not just existing in the database."));
                } else {
                    value.setCustomFieldId(customFieldId);
                }
            }
            
            if (!skipped) {
                validatedBoxes.add(videoGameBox);
            }
        }
        
        return validatedBoxes;
    }

    private List<Integer> importVideoGames(List<SlimVideoGame> videoGamesToImport, Map<Integer, Integer> customFieldIds, Map<Integer, Integer> systemIds, ExceptionBackupImport exceptionBackupImport) {

        List<SlimVideoGame> validatedVideoGames = new ArrayList<>(videoGamesToImport.size());
        for (SlimVideoGame slimVideoGame : videoGamesToImport) {
            try {
                //Validate the system information
                boolean skipped = false;
                Integer systemId = systemIds.get(slimVideoGame.system().id());
                if (null == systemId) {
                    skipped = true;
                    exceptionBackupImport.addException(new Exception("Error importing video game box data: Imported System not found but expected for video game box titled: '"
                            + slimVideoGame.title() + "' with system ID '" + slimVideoGame.system().id() + "'. The system must be included on the import and not just existing in the database."));
                } else {
                    //Since records are immutable we need to create a new dto with the proper relationship
                    SystemResponseDto updatedSystem = new SystemResponseDto(
                            slimVideoGame.system().key(),
                            systemId,
                            slimVideoGame.system().name(),
                            slimVideoGame.system().generation(),
                            slimVideoGame.system().handheld(),
                            slimVideoGame.system().createdAt(),
                            slimVideoGame.system().updatedAt(),
                            slimVideoGame.system().deletedAt(),
                            slimVideoGame.system().customFieldValues()
                    );
                    slimVideoGame = new SlimVideoGame(
                            slimVideoGame.id(),
                            slimVideoGame.title(),
                            updatedSystem,
                            slimVideoGame.createdAt(),
                            slimVideoGame.updatedAt(),
                            slimVideoGame.deletedAt(),
                            slimVideoGame.system().customFieldValues()
                    );
                }

                // Validate custom field values
                List<CustomFieldValue> validatedCustomFieldValues = new ArrayList<>();
                for (CustomFieldValue value : slimVideoGame.customFieldValues()) {
                    Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                    if (null == customFieldId) {
                        skipped = true;
                        exceptionBackupImport.addException(new Exception("Error importing video game data: Imported Custom Field not found but expected for video game titled: '"
                            + slimVideoGame.title() + "' with custom field value named '" + value.getCustomFieldName()
                            + "' The custom field must be included on the import and not just existing in the database."));
                    } else {
                        value.setCustomFieldId(customFieldId);
                    }
                }

                if (!skipped) {
                    validatedVideoGames.add(slimVideoGame);
                }
            } catch (Exception exception) {
                exceptionBackupImport.addException(exception);
            }
        }

        List<Integer> importedVideoGamesIds = new ArrayList<>(validatedVideoGames.size());
        for (SlimVideoGame slimVideoGame : validatedVideoGames) {
            int existingGameId = videoGameService.getIdByTitleAndSystem(slimVideoGame.title(), slimVideoGame.system().id());
            if (existingGameId > 0) {
                importedVideoGamesIds.add(existingGameId);
            } else {
                VideoGame createdVideoGame = videoGameService.createNew(new VideoGameRequestDto(
                        slimVideoGame.title(),
                        slimVideoGame.system().id(),
                        slimVideoGame.customFieldValues()
                ));
                importedVideoGamesIds.add(createdVideoGame.getId());
            }
        }
        
        return importedVideoGamesIds;
    }
}

record ImportEntityResults(Map<Integer, Integer> entityIds, int existingCount, int createdCount, ExceptionBackupImport exceptionBackupImport) { }
