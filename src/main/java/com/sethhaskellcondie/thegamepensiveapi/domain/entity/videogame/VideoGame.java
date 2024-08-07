package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.System;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox.SlimVideoGameBox;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;

public class VideoGame extends Entity<VideoGameRequestDto, VideoGameResponseDto> {

    private String title;
    private int systemId;
    private System system; //The system is a flag that also indicates that the systemId is valid, it must be set manually in the service and cannot be set in the constructor
    private List<Integer> videoGameBoxIds; // A list of the ids that are found in the database they should all match with a video game box in the database
    private List<SlimVideoGameBox> videoGameBoxes; // A list of the video game boxes that match the ids found in the database, hydrated in the service

    public VideoGame() {
        super();
    }

    public VideoGame(Integer id, String title, int systemId,
                     Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        super(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.title = title;
        this.systemId = systemId;
        this.system = null;
        this.videoGameBoxIds = new ArrayList<>();
        this.videoGameBoxes = new ArrayList<>();
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
        List<Exception> exceptions = new ArrayList<>();
        this.title = requestDto.title();
        try {
            this.systemId = requestDto.systemId();
        } catch (NullPointerException e) {
            exceptions.add(new ExceptionInputValidation("Video Game object error, the systemId cannot be null."));
        }
        this.system = null;
        setCustomFieldValues(requestDto.customFieldValues());
        try {
            this.validate();
        } catch (ExceptionMalformedEntity e) {
            exceptions.addAll(e.getExceptions());
        }
        if (!exceptions.isEmpty()) {
            throw new ExceptionMalformedEntity(exceptions);
        }
        return this;
    }

    @Override
    public VideoGameResponseDto convertToResponseDto() {
        SystemResponseDto systemResponseDto = null;
        if (isSystemValid()) {
            systemResponseDto = this.system.convertToResponseDto();
        }
        return new VideoGameResponseDto(this.getKey(), this.id, this.title, systemResponseDto, videoGameBoxes,
                this.created_at, this.updated_at, this.deleted_at, this.customFieldValues
        );
    }

    @Override
    public VideoGameRequestDto convertToRequestDto() {
        return new VideoGameRequestDto(this.title, this.systemId, this.customFieldValues);
    }

    @Override
    public String getKey() {
        return Keychain.VIDEO_GAME_KEY;
    }

    public SlimVideoGame convertToSlimVideoGame() {
        SystemResponseDto systemResponseDto = null;
        if (isSystemValid()) {
            systemResponseDto = this.system.convertToResponseDto();
        }
        return new SlimVideoGame(this.id, this.title, systemResponseDto, this.created_at, this.updated_at, this.deleted_at, this.customFieldValues);
    }

    private void validate() throws ExceptionMalformedEntity {
        List<Exception> exceptions = new ArrayList<>();
        if (null == this.title || this.title.isBlank()) {
            exceptions.add(new ExceptionInputValidation("Video Game object error, title cannot be blank"));
        }
        if (this.systemId <= 0) {
            exceptions.add(new ExceptionInputValidation("Video Game Object error, invalid systemId."));
        }
        if (!exceptions.isEmpty()) {
            throw new ExceptionMalformedEntity(exceptions);
        }
    }
}
