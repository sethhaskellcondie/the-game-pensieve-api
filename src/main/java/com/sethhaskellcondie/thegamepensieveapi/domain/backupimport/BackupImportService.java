package com.sethhaskellcondie.thegamepensieveapi.domain.backupimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldOption;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldOptionRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.SlimBoardGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgame.BoardGameService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.boardgamebox.BoardGameBoxRequestDto;
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
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.SlimVideoGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameService;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.VideoGameBoxService;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionBackupImport;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.Metadata;
import com.sethhaskellcondie.thegamepensieveapi.domain.metadata.MetadataGateway;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BackupImportService {
    private final CustomFieldRepository customFieldRepository;
    private final CustomFieldOptionRepository customFieldOptionRepository;
    private final SystemService systemService;
    private final ToyService toyService;
    private final VideoGameService videoGameService;
    private final VideoGameBoxService videoGameBoxService;
    private final BoardGameBoxService boardGameBoxService;
    private final BoardGameService boardGameService;
    private final MetadataGateway metadataGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected BackupImportService(CustomFieldRepository customFieldRepository, CustomFieldOptionRepository customFieldOptionRepository,
                                  SystemService systemService, ToyService toyService,
                                  VideoGameService videoGameService, VideoGameBoxService videoGameBoxService,
                                  BoardGameBoxService boardGameBoxService, BoardGameService boardGameService,
                                  MetadataGateway metadataGateway) {
        this.customFieldRepository = customFieldRepository;
        this.customFieldOptionRepository = customFieldOptionRepository;
        this.systemService = systemService;
        this.toyService = toyService;
        this.videoGameService = videoGameService;
        this.videoGameBoxService = videoGameBoxService;
        this.boardGameBoxService = boardGameBoxService;
        this.boardGameService = boardGameService;
        this.metadataGateway = metadataGateway;
    }

    protected BackupDataDto getBackupData() {
        List<CustomField> customFields = customFieldRepository.getAllCustomFields();
        List<ToyResponseDto> toys = toyService.getWithFilters(new ArrayList<>()).stream().map(Toy::convertToResponseDto).toList();
        List<SystemResponseDto> systems = systemService.getWithFilters(new ArrayList<>()).stream().map(System::convertToResponseDto).toList();
        List<VideoGameBoxResponseDto> videoGameBoxes = videoGameBoxService.getWithFilters(new ArrayList<>()).stream().map(VideoGameBox::convertToResponseDto).toList();
        List<BoardGameBoxResponseDto> boardGameBoxes = boardGameBoxService.getWithFilters(new ArrayList<>()).stream().map(BoardGameBox::convertToResponseDto).toList();
        List<Metadata> metadata = metadataGateway.getAllMetadata();

        return new BackupDataDto(customFields, toys, systems, videoGameBoxes, boardGameBoxes, metadata);
    }

    protected ImportResultsDto importBackupData(BackupDataDto backupDataDto) {
        final Map<Integer, Integer> customFieldIds;
        final Map<Integer, Integer> optionIds;
        ExceptionBackupImport exceptionBackupImport = new ExceptionBackupImport();

        final CustomFieldImportResults customFieldResults = importCustomFields(backupDataDto.customFields(), exceptionBackupImport);
        //There is no point trying to import the rest of the data if the custom fields are broken, if any error are detected then return early importing no data.
        if (!exceptionBackupImport.getExceptions().isEmpty()) {
            exceptionBackupImport.setHeader("There were errors importing Custom Fields. No additional data imported.");
            return new ImportResultsDto(customFieldResults.existingCount(), customFieldResults.createdCount(), exceptionBackupImport);
        }
        customFieldIds = customFieldResults.customFieldIds();
        optionIds = customFieldResults.optionIds();

        ImportEntityResults toyResults = importToys(backupDataDto.toys(), customFieldIds, optionIds, exceptionBackupImport);

        ImportEntityResults systemResults = importSystems(backupDataDto.systems(), customFieldIds, optionIds, exceptionBackupImport);

        ImportEntityResults videoGameBoxResults = importVideoGameBoxes(backupDataDto.videoGameBoxes(), customFieldIds, optionIds, systemResults.entityIds(), exceptionBackupImport);

        ImportEntityResults boardGameBoxResults = importBoardGameBoxes(backupDataDto.boardGameBoxes(), customFieldIds, optionIds, exceptionBackupImport);

        ImportEntityResults metadataResults = importMetadata(backupDataDto.metadata(), exceptionBackupImport);

        if (!exceptionBackupImport.getExceptions().isEmpty()) {
            exceptionBackupImport.setHeader("There were errors importing Entity data, all valid data was imported, data with errors was skipped.");
        }

        return new ImportResultsDto(
                customFieldResults.existingCount(), customFieldResults.createdCount(),
                toyResults.existingCount(), toyResults.createdCount(),
                systemResults.existingCount(), systemResults.createdCount(),
                videoGameBoxResults.existingCount(), videoGameBoxResults.createdCount(),
                boardGameBoxResults.existingCount(), boardGameBoxResults.createdCount(),
                metadataResults.existingCount(), metadataResults.createdCount(),
                exceptionBackupImport
        );
    }

    private CustomFieldImportResults importCustomFields(final List<CustomField> customFields, ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;

        validateCustomFieldIds(customFields, exceptionBackupImport);
        if (!exceptionBackupImport.getExceptions().isEmpty()) {
            return new CustomFieldImportResults(new HashMap<>(), new HashMap<>(), existingCount, createdCount);
        }

        final Map<Integer, Integer> customFieldIds = new HashMap<>(customFields.size());
        //Maps an option id from the backup file to the option id assigned in the database, so enum entity values can be remapped.
        final Map<Integer, Integer> optionIds = new HashMap<>();
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
                    savedCustomField = customFieldRepository.insertCustomField(CustomFieldRequestDto.withoutOptions(customField.name(), customField.type(), customField.entityKey()));
                    //insertCustomField does not persist options, so recreate them here from the backup. Without this the enum
                    //field is created with no options and every entity value that references one fails validation on import.
                    if (CustomField.isEnumType(customField.type()) && null != customField.options()) {
                        for (CustomFieldOption option : customField.options()) {
                            customFieldOptionRepository.insertOption(savedCustomField.id(), option.name(), option.isDefault(), option.order());
                        }
                    }
                    createdCount++;
                } catch (Exception exception) {
                    exceptionBackupImport.addCustomFieldException(new Exception("Error Importing Custom Field Data: Provided custom field with name: '"
                            + customField.name() + "' Message: " + exception.getMessage()));
                }
            }
            if (null != savedCustomField) {
                //The first ID comes from the import and is used to determine relationships with other objects to be imported, and the second is the new ID after being written to the database.
                customFieldIds.put(customField.id(), savedCustomField.id());
                mapOptionIds(customField, savedCustomField.id(), optionIds);
            }
        }
        return new CustomFieldImportResults(customFieldIds, optionIds, existingCount, createdCount);
    }

    //Builds the backup-option-id -> database-option-id mapping for an enum field by matching options on their name
    //(custom_field_options has a UNIQUE (custom_field_id, name) constraint, so the name is a stable natural key).
    private void mapOptionIds(CustomField importedField, int savedFieldId, Map<Integer, Integer> optionIds) {
        if (!CustomField.isEnumType(importedField.type()) || null == importedField.options()) {
            return;
        }
        final Map<String, Integer> savedOptionIdsByName = new HashMap<>();
        for (CustomFieldOption savedOption : customFieldRepository.getById(savedFieldId).options()) {
            savedOptionIdsByName.put(savedOption.name(), savedOption.id());
        }
        for (CustomFieldOption importedOption : importedField.options()) {
            final Integer savedOptionId = savedOptionIdsByName.get(importedOption.name());
            if (null != savedOptionId) {
                optionIds.put(importedOption.id(), savedOptionId);
            }
        }
    }

    //Returns an error message if an enum value's option reference cannot be remapped, otherwise remaps it in place and returns null.
    private String remapValueOptionId(CustomFieldValue value, Map<Integer, Integer> optionIds) {
        if (!CustomField.isEnumType(value.getCustomFieldType())) {
            return null;
        }
        final Integer newOptionId = optionIds.get(value.getValueOptionId());
        if (null == newOptionId) {
            return "the custom field value named '" + value.getCustomFieldName() + "' referenced an option (valueOptionId '"
                    + value.getValueOptionId() + "') that was not found in the import data.";
        }
        value.setValueOptionId(newOptionId);
        return null;
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

    private ImportEntityResults importToys(List<ToyResponseDto> toysToBeImported, Map<Integer, Integer> customFieldIds, Map<Integer, Integer> optionIds, ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;
        if (null == toysToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
        }
        Map<Integer, Integer> toyIds = new HashMap<>(toysToBeImported.size());

        List<ToyResponseDto> validatedToys = new ArrayList<>(toysToBeImported.size());
        for (ToyResponseDto validatingToy : toysToBeImported) {
            boolean skipped = false;
            if (null == validatingToy.name() || validatingToy.name().trim().isEmpty()) {
                skipped = true;
                exceptionBackupImport.addToyException(new Exception("Error importing toy data: Toy must have a name, but name was missing or empty."));
            }
            for (CustomFieldValue value: validatingToy.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addToyException(new Exception("Error Importing Toy Data: Custom field relationships not found but expected for toy with name: '"
                        + validatingToy.name() + "' and set '" + validatingToy.set() + "' with custom field value named '" + value.getCustomFieldName()
                        + "' and custom field ID provided on the toy as '" + value.getCustomFieldId() + "'."));
                } else {
                    value.setCustomFieldId(customFieldId);
                    final String optionError = remapValueOptionId(value, optionIds);
                    if (null != optionError) {
                        skipped = true;
                        exceptionBackupImport.addToyException(new Exception("Error Importing Toy Data: for toy with name: '"
                            + validatingToy.name() + "' and set '" + validatingToy.set() + "' " + optionError));
                    }
                }
            }
            if (!skipped) {
                validatedToys.add(validatingToy);
            }
        }

        for (ToyResponseDto importToy: validatedToys) {
            try {
                int toyId = toyService.duplicationCheck(importToy.name(), importToy.set());
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

    private ImportEntityResults importSystems(List<SystemResponseDto> systemsToBeImported, Map<Integer, Integer> customFieldIds,
                                              Map<Integer, Integer> optionIds, ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;
        if (null == systemsToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
        }
        Map<Integer, Integer> systemIds = new HashMap<>(systemsToBeImported.size());

        List<SystemResponseDto> validatedSystems = new ArrayList<>(systemsToBeImported.size());
        for (SystemResponseDto validatingSystem: systemsToBeImported) {
            boolean skipped = false;
            if (null == validatingSystem.name() || validatingSystem.name().trim().isEmpty()) {
                skipped = true;
                exceptionBackupImport.addSystemException(new Exception("Error importing system data: System must have a name, but name was missing or empty."));
            }
            for (CustomFieldValue value: validatingSystem.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addSystemException(new Exception("Error importing system data: Imported Custom Field not found but expected for system named: '"
                        + validatingSystem.name() + "' with custom field value named '" + value.getCustomFieldName()
                        + "' The custom field must be included on the import and not just existing in the database."));
                } else {
                    value.setCustomFieldId(customFieldId);
                    final String optionError = remapValueOptionId(value, optionIds);
                    if (null != optionError) {
                        skipped = true;
                        exceptionBackupImport.addSystemException(new Exception("Error importing system data: for system named: '"
                            + validatingSystem.name() + "' " + optionError));
                    }
                }
            }
            if (!skipped) {
                validatedSystems.add(validatingSystem);
            }
        }

        for (SystemResponseDto importSystem: validatedSystems) {
            try {
                int systemId = systemService.duplicationCheck(importSystem.name());
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

    private ImportEntityResults importVideoGameBoxes(List<VideoGameBoxResponseDto> videoGameBoxToBeImported,
                                                     Map<Integer, Integer> customFieldIds, Map<Integer, Integer> optionIds, Map<Integer, Integer> systemIds,
                                                     ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;
        if (null == videoGameBoxToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
        }
        Map<Integer, Integer> boxIds = new HashMap<>(videoGameBoxToBeImported.size());
        List<VideoGameBoxResponseDto> validatedBoxes = validateVideoGameBoxes(videoGameBoxToBeImported, customFieldIds, optionIds, systemIds, exceptionBackupImport);
        Map<Integer, Integer> gameIds = new HashMap<>(videoGameBoxToBeImported.size());

        for (VideoGameBoxResponseDto importBox: validatedBoxes) {
            try {
                int boxId = videoGameBoxService.duplicationCheck(importBox.title(), importBox.system().id());
                if (boxId > 0) {
                    existingCount++;
                    boxIds.put(importBox.id(), boxId);
                    continue;
                }

                ValidatedVideoGameResults validatedVideoGameResults = validateVideoGames(importBox.videoGames(), customFieldIds, optionIds, systemIds, gameIds, exceptionBackupImport);
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

    private List<VideoGameBoxResponseDto> validateVideoGameBoxes(List<VideoGameBoxResponseDto> videoGameBoxes,
                                                                 Map<Integer, Integer> customFieldIds, Map<Integer, Integer> optionIds, Map<Integer, Integer> systemIds,
                                                                 ExceptionBackupImport exceptionBackupImport) {

        List<VideoGameBoxResponseDto> validatedBoxes = new ArrayList<>(videoGameBoxes.size());
        for (VideoGameBoxResponseDto videoGameBox : videoGameBoxes) {
            boolean skipped = false;
            if (null == videoGameBox.title() || videoGameBox.title().trim().isEmpty()) {
                skipped = true;
                exceptionBackupImport.addVideoGameBoxException(new Exception("Error importing video game box data: Video game box must have a title, but title was missing or empty."));
            }
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
                    final String optionError = remapValueOptionId(value, optionIds);
                    if (null != optionError) {
                        skipped = true;
                        exceptionBackupImport.addVideoGameBoxException(new Exception("Error importing video game box data: for video game box titled: '"
                            + videoGameBox.title() + "' " + optionError));
                    }
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

    private ValidatedVideoGameResults validateVideoGames(List<SlimVideoGame> videoGamesToImport, Map<Integer, Integer> customFieldIds, Map<Integer, Integer> optionIds,
                                                         Map<Integer, Integer> systemIds, Map<Integer, Integer> videoGameIds,
                                                         ExceptionBackupImport exceptionBackupImport) {
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

                if (!skipped) {
                    //The same physical game (title + system) can be referenced by more than one box, sometimes under
                    //different file ids. The videoGameIds map only catches reuse already seen this run; this check
                    //catches a game an earlier box already created (or that pre-existed) so we reuse it instead of
                    //trying to insert a duplicate, which the unique (title, system) constraint would reject.
                    int existingDbGameId = videoGameService.duplicationCheck(validatingGame.title(), systemId);
                    if (existingDbGameId > 0) {
                        importedVideoGamesIds.add(existingDbGameId);
                        videoGameIds.put(validatingGame.id(), existingDbGameId);
                        continue;
                    }
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
                        final String optionError = remapValueOptionId(value, optionIds);
                        if (null != optionError) {
                            skipped = true;
                            exceptionBackupImport.addVideoGameException(new Exception("Error importing video game data: for video game titled: '"
                                + validatingGame.title() + "' " + optionError));
                        }
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

    private ImportEntityResults importBoardGameBoxes(List<BoardGameBoxResponseDto> boardGameBoxesToBeImported, Map<Integer, Integer> customFieldIds,
                                                     Map<Integer, Integer> optionIds, ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;
        if (null == boardGameBoxesToBeImported) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
        }
        Map<Integer, Integer> boardGameBoxIds = new HashMap<>(boardGameBoxesToBeImported.size());
        Map<Integer, Integer> boardGameIds = new HashMap<>(boardGameBoxesToBeImported.size());
        List<BoardGameBoxResponseDto> validatedBoxes = validateBoardGameBoxes(boardGameBoxesToBeImported, customFieldIds, optionIds, exceptionBackupImport);
        validatedBoxes.sort(Comparator.comparing(BoardGameBoxResponseDto::isExpansion)); //import base set boxes first, then expansions after that

        for (BoardGameBoxResponseDto importBox : validatedBoxes) {
            try {
                Integer realBoardGameId = boardGameIds.get(importBox.boardGame().id());
                if (realBoardGameId == null) {
                    int foundGameId = boardGameService.duplicationCheck(importBox.boardGame().title());
                    if (foundGameId > 0) {
                        realBoardGameId = foundGameId;
                        boardGameIds.put(importBox.boardGame().id(), foundGameId);
                    }
                }
                int boxId = (realBoardGameId != null)
                        ? boardGameBoxService.duplicationCheck(importBox.title(), realBoardGameId)
                        : -1;
                if (boxId > 0) {
                    existingCount++;
                    boardGameBoxIds.put(importBox.id(), boxId);
                } else {
                    Integer existingBoardGameId = realBoardGameId;
                    Integer baseSetId = boardGameIds.get(importBox.baseSetId());
                    BoardGameRequestDto gameToBeCreated = null;
                    if (null == existingBoardGameId) {
                        gameToBeCreated = new BoardGameRequestDto(importBox.boardGame().title(), importBox.boardGame().customFieldValues());
                    }

                    BoardGameBox createdBoardGameBox = boardGameBoxService.createNew(new BoardGameBoxRequestDto(
                        importBox.title(),
                        importBox.isExpansion(),
                        importBox.isStandAlone(),
                        baseSetId,
                        existingBoardGameId,
                        gameToBeCreated,
                        importBox.customFieldValues()
                    ));
                    createdCount++;
                    boardGameBoxIds.put(importBox.id(), createdBoardGameBox.getId());
                    boardGameIds.put(importBox.boardGame().id(), createdBoardGameBox.getBoardGame().id());
                }
            } catch (Exception exception) {
                exceptionBackupImport.addBoardGameBoxException(new Exception("Error importing board game box data with title: '" + importBox.title()
                        + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(boardGameBoxIds, existingCount, createdCount);
    }

    private List<BoardGameBoxResponseDto> validateBoardGameBoxes(List<BoardGameBoxResponseDto> boardGameBoxes, Map<Integer, Integer> customFieldIds,
                                                                 Map<Integer, Integer> optionIds, ExceptionBackupImport exceptionBackupImport) {
        List<BoardGameBoxResponseDto> validatedBoxes = new ArrayList<>(boardGameBoxes.size());

        for (BoardGameBoxResponseDto boardGameBox : boardGameBoxes) {
            boolean skipped = false;

            if (null == boardGameBox.title() || boardGameBox.title().trim().isEmpty()) {
                skipped = true;
                exceptionBackupImport.addBoardGameBoxException(new Exception("Error importing board game box data: Board game box must have a title, but title was missing or empty."));
            }

            // Validate board game (parent game) if it exists
            if (null != boardGameBox.boardGame()) {
                BoardGameRequestDto validatedGame = validateBoardGame(boardGameBox.boardGame(), customFieldIds, optionIds, exceptionBackupImport);
                if (null == validatedGame) {
                    skipped = true;
                }
            }

            for (CustomFieldValue value : boardGameBox.customFieldValues()) {
                Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
                if (null == customFieldId) {
                    skipped = true;
                    exceptionBackupImport.addBoardGameBoxException(new Exception("Error importing board game box data: Imported Custom Field not found but expected for board game box titled: '"
                        + boardGameBox.title() + "' with custom field value named '" + value.getCustomFieldName()
                        + "' The custom field must be included on the import and not just existing in the database."));
                } else {
                    value.setCustomFieldId(customFieldId);
                    final String optionError = remapValueOptionId(value, optionIds);
                    if (null != optionError) {
                        skipped = true;
                        exceptionBackupImport.addBoardGameBoxException(new Exception("Error importing board game box data: for board game box titled: '"
                            + boardGameBox.title() + "' " + optionError));
                    }
                }
            }

            if (!skipped) {
                validatedBoxes.add(boardGameBox);
            }
        }
        
        return validatedBoxes;
    }

    private BoardGameRequestDto validateBoardGame(SlimBoardGame boardGame, Map<Integer, Integer> customFieldIds, Map<Integer, Integer> optionIds, ExceptionBackupImport exceptionBackupImport) {
        if (null == boardGame) {
            return null;
        }

        boolean valid = true;

        if (null == boardGame.title() || boardGame.title().trim().isEmpty()) {
            valid = false;
            exceptionBackupImport.addBoardGameException(new Exception("Error importing board game data: Board game must have a title, but title was missing or empty."));
        }

        for (CustomFieldValue value : boardGame.customFieldValues()) {
            Integer customFieldId = customFieldIds.get(value.getCustomFieldId());
            if (null == customFieldId) {
                valid = false;
                exceptionBackupImport.addBoardGameException(new Exception("Error importing board game data: Imported Custom Field not found but expected for board game with title: '"
                    + boardGame.title() + "' with custom field value named '" + value.getCustomFieldName()
                    + "' The custom field must be included on the import and not just existing in the database."));
            } else {
                value.setCustomFieldId(customFieldId);
                final String optionError = remapValueOptionId(value, optionIds);
                if (null != optionError) {
                    valid = false;
                    exceptionBackupImport.addBoardGameException(new Exception("Error importing board game data: for board game with title: '"
                        + boardGame.title() + "' " + optionError));
                }
            }
        }

        if (valid) {
            return new BoardGameRequestDto(boardGame.title(), boardGame.customFieldValues());
        }

        return null;
    }

    private ImportEntityResults importMetadata(List<Metadata> metadataToImport, ExceptionBackupImport exceptionBackupImport) {
        int existingCount = 0;
        int createdCount = 0;
        if (null == metadataToImport) {
            return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
        }
        for (Metadata metadata : metadataToImport) {
            if (null == metadata.key() || metadata.key().trim().isEmpty()) {
                exceptionBackupImport.addMetadataException("Error importing metadata: A metadata entry is missing a key. Metadata must have a non-empty key.");
                continue;
            }
            Metadata existing;
            try {
                existing = metadataGateway.getByKey(metadata.key());
            } catch (ExceptionResourceNotFound ignored) {
                existing = null;
            }
            try {
                if (null == existing) {
                    metadataGateway.createNew(metadata);
                    createdCount++;
                } else if (isBlankStub(existing.value())) {
                    //The front end seeds empty placeholder rows for keys it expects to exist. Treat those stubs as
                    //absent and overwrite them with the imported value; a stub that was skipped would silently drop the import.
                    metadataGateway.updateValue(metadata);
                    createdCount++;
                } else {
                    existingCount++;
                }
            } catch (Exception exception) {
                exceptionBackupImport.addMetadataException(new Exception("Error importing metadata with key: '" + metadata.key() + "' " + exception.getMessage()));
            }
        }
        return new ImportEntityResults(new HashMap<>(), existingCount, createdCount);
    }

    //A blank stub is an existing metadata value that holds no real data: null, empty/whitespace text, or JSON that
    //parses to null, an empty object, an empty array, or an empty string. A value that fails to parse is treated as
    //real data (not a stub) so we never clobber something unexpected.
    private boolean isBlankStub(String value) {
        if (null == value || value.trim().isEmpty()) {
            return true;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (null == node || node.isNull() || node.isMissingNode()) {
                return true;
            }
            if (node.isContainerNode()) {
                return node.isEmpty();
            }
            if (node.isTextual()) {
                return node.asText().trim().isEmpty();
            }
            return false;
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }
}

record ImportEntityResults(Map<Integer, Integer> entityIds, int existingCount, int createdCount) { }
record CustomFieldImportResults(Map<Integer, Integer> customFieldIds, Map<Integer, Integer> optionIds, int existingCount, int createdCount) { }
record ValidatedVideoGameResults(List<Integer> existingIds, Map<Integer, VideoGameRequestDto> newGames) { }
