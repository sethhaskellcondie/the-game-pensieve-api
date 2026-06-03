package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityData;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox.SlimVideoGameBox;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionMalformedEntity;

public class VideoGame implements Entity<VideoGameRequestDto, VideoGameResponseDto> {

    private final EntityData entityData;
    private String title;
    private int systemId;
    private System system; //The system is a flag that also indicates that the systemId is valid, it must be set manually in the service and cannot be set in the constructor
    private List<Integer> videoGameBoxIds; // A list of the ids that are found in the database they should all match with a video game box in the database
    private List<SlimVideoGameBox> videoGameBoxes; // A list of the video game boxes that match the ids found in the database, hydrated in the service

    public VideoGame() {
        this.entityData = new EntityData();
        this.videoGameBoxIds = new ArrayList<>();
        this.videoGameBoxes = new ArrayList<>();
    }

    public VideoGame(Integer id, String title, int systemId,
                     Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        this.entityData = new EntityData(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.title = title;
        this.systemId = systemId;
        this.system = null;
        this.videoGameBoxIds = new ArrayList<>();
        this.videoGameBoxes = new ArrayList<>();
    }

    @Override
    public Integer getId() {
        return entityData.getId();
    }

    @Override
    public String getKey() {
        return Keychain.VIDEO_GAME_KEY;
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

    public boolean isSystemValid() {
        return null != system;
    }

    public List<Integer> getVideoGameBoxIds() {
        return videoGameBoxIds;
    }

    public void setVideoGameBoxIds(List<Integer> videoGameBoxIds) {
        if (null == videoGameBoxIds) {
            this.videoGameBoxIds = new ArrayList<>();
            return;
        }
        this.videoGameBoxIds = videoGameBoxIds;
    }

    public List<SlimVideoGameBox> getVideoGameBoxes() {
        return this.videoGameBoxes;
    }

    public void setVideoGameBoxes(List<SlimVideoGameBox> videoGameBoxes) {
        if (null == videoGameBoxes) {
            this.videoGameBoxes = new ArrayList<>();
            return;
        }
        this.videoGameBoxes = videoGameBoxes;
    }

    public boolean isVideoGameBoxesValid() {
        return !videoGameBoxes.isEmpty();
    }

    @Override
    public VideoGame updateFromRequestDto(VideoGameRequestDto requestDto) {
        ExceptionMalformedEntity exceptionMalformedEntity = new ExceptionMalformedEntity();
        this.title = requestDto.title();
        try {
            this.systemId = requestDto.systemId();
        } catch (NullPointerException e) {
            exceptionMalformedEntity.addException(new ExceptionInputValidation("Video Game object error, the systemId cannot be null."));
        }
        this.system = null;
        entityData.setCustomFieldValues(requestDto.customFieldValues());
        try {
            this.validate();
        } catch (ExceptionMalformedEntity e) {
            exceptionMalformedEntity.appendExceptions(e.getExceptions());
        }
        if (!exceptionMalformedEntity.isEmpty()) {
            throw exceptionMalformedEntity;
        }
        return this;
    }

    @Override
    public VideoGameResponseDto convertToResponseDto() {
        SystemResponseDto systemResponseDto = null;
        if (isSystemValid()) {
            systemResponseDto = this.system.convertToResponseDto();
        }
        return new VideoGameResponseDto(this.getKey(), entityData.getId(), this.title, systemResponseDto, videoGameBoxes,
                entityData.getCreatedAt(), entityData.getUpdatedAt(), entityData.getDeletedAt(), entityData.getCustomFieldValues()
        );
    }

    @Override
    public VideoGameRequestDto convertToRequestDto() {
        return new VideoGameRequestDto(this.title, this.systemId, entityData.getCustomFieldValues());
    }

    public SlimVideoGame convertToSlimVideoGame() {
        SystemResponseDto systemResponseDto = null;
        if (isSystemValid()) {
            systemResponseDto = this.system.convertToResponseDto();
        }
        return new SlimVideoGame(entityData.getId(), this.title, systemResponseDto,
                entityData.getCreatedAt(), entityData.getUpdatedAt(), entityData.getDeletedAt(), entityData.getCustomFieldValues());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof VideoGame other)) {
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

    private void validate() throws ExceptionMalformedEntity {
        ExceptionMalformedEntity exceptionMalformedEntity = new ExceptionMalformedEntity();
        if (null == this.title || this.title.isBlank()) {
            exceptionMalformedEntity.addException(new ExceptionInputValidation("Video Game object error, title cannot be blank"));
        }
        if (this.systemId <= 0) {
            exceptionMalformedEntity.addException(new ExceptionInputValidation("Video Game Object error, invalid systemId."));
        }
        if (!exceptionMalformedEntity.isEmpty()) {
            throw exceptionMalformedEntity;
        }
    }
}
