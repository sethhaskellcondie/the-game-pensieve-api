package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.Entity;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGame;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGameResponseDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionInputValidation;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionMalformedEntity;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class VideoGameBox extends Entity<VideoGameBoxRequestDto, VideoGameBoxResponseDto> {

    private String title;
    private int systemId; //TODO refactor the video game box to have the entire system instead of just the name
    private String systemName; //the systemName is also a flags that is only set after the systemId has been verified
    private List<Integer> videoGameIds; //the videoGameId's and videoGames work in a similar fashion to the systemId and systemName
    private List<VideoGame> videoGames; //a list of games that can be played on the cart/disc/box, one or more
    private boolean physical;
    private boolean collection;

    public VideoGameBox() {
        super();
    }

    //used by the repository to get the data from the database, the videoGames and systemName will be set later
    public VideoGameBox(Integer id, String title, int systemId, boolean physical, boolean collection,
                        Timestamp createdAt, Timestamp updatedAt, Timestamp deletedAt, List<CustomFieldValue> customFieldValues) {
        super(id, createdAt, updatedAt, deletedAt, customFieldValues);
        this.title = title;
        this.systemId = systemId;
        this.systemName = null;
        this.videoGameIds = new ArrayList<>();
        this.videoGames = new ArrayList<>();
        this.physical = physical;
        this.collection = collection;
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

    public List<Integer> getVideoGameIds() {
        return this.videoGameIds;
    }

    public void setVideoGameIds(List<Integer> videoGameIds) {
        if (null == videoGameIds) {
            this.videoGameIds = new ArrayList<>();
            return;
        }
        this.videoGameIds = videoGameIds;
    }

    public List<VideoGame> getVideoGames() {
        return videoGames;
    }

    public void setVideoGames(List<VideoGame> videoGames) {
        if (null == videoGames) {
            this.videoGames = new ArrayList<>();
            return;
        }
        this.videoGames = videoGames;
    }

    public boolean isPhysical() {
        return physical;
    }

    public boolean isCollection() {
        return collection;
    }

    public boolean isSystemIdValid() {
        return null != systemName;
    }

    public boolean isVideoGamesValid() {
        return !videoGames.isEmpty();
    }

    @Override
    protected VideoGameBox updateFromRequestDto(VideoGameBoxRequestDto requestDto) {
        ExceptionMalformedEntity exception = new ExceptionMalformedEntity();
        this.title = requestDto.title();
        try {
            this.systemId = requestDto.systemId();
        } catch (NullPointerException ignored) {
            exception.addException(new ExceptionInputValidation("Video Game Box object error, the systemId cannot be null."));
        }
        this.systemName = null;
        if (requestDto.videoGameIds().isEmpty()) {
            exception.addException(new ExceptionInputValidation("Video Game Box object error, boxes must contain at least one game."));
        }
        this.videoGameIds = requestDto.videoGameIds();
        this.videoGames = new ArrayList<>();
        this.physical = requestDto.isPhysical();
        this.collection = requestDto.isCollection();
        this.customFieldValues = requestDto.customFieldValues();

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
    protected VideoGameBoxResponseDto convertToResponseDto() {
        List<VideoGameResponseDto> videoGameDtos = new ArrayList<>();
        for (VideoGame videoGame : this.videoGames) {
            videoGameDtos.add(videoGame.convertToResponseDto());
        }
        return new VideoGameBoxResponseDto(this.getKey(), this.id, this.title, this.systemId, this.systemName, this.videoGameIds, videoGameDtos, this.physical, this.collection,
                this.created_at, this.updated_at, this.deleted_at, this.customFieldValues
        );
    }

    @Override
    protected VideoGameBoxRequestDto convertToRequestDto() {
        return new VideoGameBoxRequestDto(this.title, this.systemId, this.videoGameIds, this.physical, this.collection, this.customFieldValues);
    }

    @Override
    public String getKey() {
        return Keychain.VIDEO_GAME_BOX_KEY;
    }

    private void validate() throws ExceptionMalformedEntity {
        ExceptionMalformedEntity exception = new ExceptionMalformedEntity();
        if (null == this.title || this.title.isBlank()) {
            exception.addException(new ExceptionInputValidation("Video Game Box error, title cannot be blank."));
        }
        if (this.systemId <= 0) {
            exception.addException("Video Game Box error, invalid system ID.");
        }
        if (this.videoGameIds.isEmpty() && this.videoGames.isEmpty()) {
            exception.addException(new ExceptionInputValidation("A Video Game Box must always have either a list of game ids, or a list of games."));
        }
        if (!exception.isEmpty()) {
            throw exception;
        }
    }
}
