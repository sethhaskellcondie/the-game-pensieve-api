package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityData;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.SlimVideoGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.VideoGameRequestDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VideoGameBox implements Entity<VideoGameBoxRequestDto, VideoGameBoxResponseDto> {

    private final EntityData entityData;
    private String title;
    private int systemId; //This is the systemId saved in the database it should correspond to a system in the system table.
    private System system; //This works like a flag if the systemId is valid then a system (can be deleted) can be pulled from the database, set in the service not the constructor
    private List<Integer> videoGameIds; //the videoGameId's and videoGames work in a similar fashion to the systemId and systemName
    private List<SlimVideoGame> videoGames; //a list of games that can be played on the cart/disc/box, one or more
    private boolean physical;
    private boolean collection;

    public VideoGameBox() {
        this.entityData = new EntityData();
        this.videoGameIds = new ArrayList<>();
        this.videoGames = new ArrayList<>();
    }

    //used by the repository to get the data from the database, the videoGames and system will be set later
    public VideoGameBox(Integer id, String title, int systemId, boolean physical, boolean collection,
                        Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        this.entityData = new EntityData(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.title = title;
        this.systemId = systemId;
        this.system = null;
        this.videoGameIds = new ArrayList<>();
        this.videoGames = new ArrayList<>();
        this.physical = physical;
        this.collection = collection;
    }

    @Override
    public Integer getId() {
        return entityData.getId();
    }

    @Override
    public String getKey() {
        return Keychain.VIDEO_GAME_BOX_KEY;
    }

    @Override
    public List<CustomFieldValue> getCustomFieldValues() {
        return entityData.getCustomFieldValues();
    }

    @Override
    public void setCustomFieldValues(List<CustomFieldValue> customFieldValues) {
        entityData.setCustomFieldValues(customFieldValues);
    }

    @Override
    public boolean isPersisted() {
        return entityData.isPersisted();
    }

    @Override
    public boolean isDeleted() {
        return entityData.isDeleted();
    }

    public Timestamp getCreatedAt() {
        return entityData.getCreatedAt();
    }

    public Timestamp getUpdatedAt() {
        return entityData.getUpdatedAt();
    }

    public Timestamp getDeletedAt() {
        return entityData.getDeletedAt();
    }

    public String getTitle() {
        return title;
    }

    public int getSystemId() {
        return systemId;
    }

    public System getSystem() {
        return system;
    }

    public void setSystem(System system) {
        this.system = system;
    }

    public List<Integer> getVideoGameIds() {
        if (!videoGames.isEmpty()) {
            List<Integer> gameIds = new ArrayList<>();
            for (SlimVideoGame game : videoGames) {
                gameIds.add(game.id());
            }
            return gameIds;
        }
        return this.videoGameIds;
    }

    public void setVideoGameIds(List<Integer> videoGameIds) {
        if (null == videoGameIds) {
            this.videoGameIds = new ArrayList<>();
            this.collection = computeIsCollection();
            return;
        }
        this.videoGameIds = videoGameIds;
        this.collection = computeIsCollection();
    }

    public List<SlimVideoGame> getVideoGames() {
        return videoGames;
    }

    public void setVideoGames(List<SlimVideoGame> videoGames) {
        if (null == videoGames) {
            this.videoGames = new ArrayList<>();
            this.collection = computeIsCollection();
            return;
        }
        this.videoGames = videoGames;
        this.collection = computeIsCollection();
    }

    public boolean isPhysical() {
        return physical;
    }

    public boolean isCollection() {
        return collection;
    }

    private boolean computeIsCollection() {
        return videoGameIds.size() > 1 || videoGames.size() > 1;
    }

    public boolean isSystemValid() {
        return null != system;
    }

    public boolean isVideoGamesValid() {
        return !videoGames.isEmpty();
    }

    @Override
    public VideoGameBox updateFromRequestDto(VideoGameBoxRequestDto requestDto) {
        //the videoGames, the videoGameIds, and the System are all setup outside of this
        ExceptionMalformedEntity exception = new ExceptionMalformedEntity();
        this.title = requestDto.title();
        try {
            this.systemId = requestDto.systemId();
        } catch (NullPointerException ignored) {
            exception.addException(new ExceptionInputValidation("Video Game Box object error, the systemId cannot be null."));
        }
        this.physical = requestDto.isPhysical();
        entityData.setCustomFieldValues(requestDto.customFieldValues());

        try {
            this.validate();
        } catch (ExceptionMalformedEntity e) {
            exception.appendExceptions(e.getExceptions());
        }

        if (!exception.isEmpty()) {
            throw exception;
        }
        return this;
    }

    @Override
    public VideoGameBoxResponseDto convertToResponseDto() {
        SystemResponseDto systemResponseDto = null;
        if (isSystemValid()) {
            systemResponseDto = this.system.convertToResponseDto();
        }
        return new VideoGameBoxResponseDto(this.getKey(), entityData.getId(), this.title, systemResponseDto, this.videoGames, this.physical, this.collection,
                entityData.getCreatedAt(), entityData.getUpdatedAt(), entityData.getDeletedAt(), entityData.getCustomFieldValues()
        );
    }

    @Override
    public VideoGameBoxRequestDto convertToRequestDto() {
        List<VideoGameRequestDto> videoGameRequests = new ArrayList<>();
        for (SlimVideoGame videoGame : this.videoGames) {
            videoGameRequests.add(new VideoGameRequestDto(videoGame.title(), videoGame.system().id(), videoGame.customFieldValues()));
        }
        return new VideoGameBoxRequestDto(this.title, this.systemId, this.videoGameIds, videoGameRequests, this.physical, entityData.getCustomFieldValues());
    }

    public SlimVideoGameBox convertToSlimVideoGameBox() {
        SystemResponseDto systemResponseDto = null;
        if (isSystemValid()) {
            systemResponseDto = this.system.convertToResponseDto();
        }
        return new SlimVideoGameBox(entityData.getId(), this.title, systemResponseDto, this.physical, this.collection,
                entityData.getCreatedAt(), entityData.getUpdatedAt(), entityData.getDeletedAt(), entityData.getCustomFieldValues());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof VideoGameBox other)) {
            return false;
        }
        if (!other.isPersisted()) {
            return false;
        }
        return Objects.equals(this.getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    private void validate() {
        ExceptionMalformedEntity exception = new ExceptionMalformedEntity();
        if (null == this.title || this.title.isBlank()) {
            exception.addException(new ExceptionInputValidation("Video Game Box error, title cannot be blank."));
        }
        if (this.systemId <= 0) {
            exception.addException("Video Game Box error, invalid system ID.");
        }
        if (!exception.isEmpty()) {
            throw exception;
        }
    }
}
