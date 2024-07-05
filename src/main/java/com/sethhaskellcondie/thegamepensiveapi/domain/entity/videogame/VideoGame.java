package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;

public class VideoGame extends Entity<VideoGameRequestDto, VideoGameResponseDto> {

    private String title;
    private int systemId;
    //The system name is a flag that also indicates that the systemId is valid
    //it must be set manually and cannot be set in the constructor.
    //TODO refactor the game to have the entire system instead of just the name
    private String systemName;

    public VideoGame() {
        super();
    }

    public VideoGame(Integer id, String title, int systemId,
                     Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        super(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.title = title;
        this.systemId = systemId;
        this.systemName = null;
    }

    public String getTitle() {
        return title;
    }

    public int getSystemId() {
        return systemId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public boolean isSystemIdValid() {
        return null != systemName;
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
        this.systemName = null;
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
        return new VideoGameResponseDto(this.getKey(), this.id, this.title, this.systemId, this.systemName,
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
