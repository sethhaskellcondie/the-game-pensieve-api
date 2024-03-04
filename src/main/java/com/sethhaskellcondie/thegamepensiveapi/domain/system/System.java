package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.Entity;

/**
 * The Entity object will extend the Entity abstract class, this will enforce the ID equality
 * and persistent check.
 */
public class System extends Entity<SystemRequestDto, SystemResponseDto> {
    /**
     * Inherit ID from Entity
     */
    private String name;
    private int generation;
    private boolean handheld;

    /**
     * Define the DTO on the Entity if the shape of an object needs to be changed
     * all of those changes can be made here on the Entity with minimal changes elsewhere
     * in the project
     * <p>
     * Most DTO's that contain more than one Entity will be a composite of the existing DTO's
     * but if there is a case where a completely new DTO will need to be created that will
     * be complete on the Service.
     */

    public System() {
        super();
    }

    /**
     * Every Entity will have a constructor that takes an ID
     * this constructor should only be used in repositories and tests.
     * To hydrate an Entity with an ID call getWithId on the repository.
     */
    public System(Integer id, String name, int generation, boolean handheld) {
        super(id);
        this.name = name;
        this.generation = generation;
        this.handheld = handheld;
    }

    public String getName() {
        return name;
    }

    public int getGeneration() {
        return generation;
    }

    public boolean isHandheld() {
        return handheld;
    }

    /**
     * Also inherit isPersistent() from Entity
     * this will return True if the Entity has an ID
     * meaning that the Entity has been persisted to the database
     */

    public System updateFromRequest(SystemRequestDto requestDto) {
        this.name = requestDto.name();
        this.generation = requestDto.generation();
        this.handheld = requestDto.handheld();
        return this;
    }

    public SystemResponseDto convertToResponseDto() {
        return new SystemResponseDto(this.id, this.name, this.generation, this.handheld);
    }
}

record SystemResponseDto(Integer id, String name, int generation, boolean handheld) {
}
