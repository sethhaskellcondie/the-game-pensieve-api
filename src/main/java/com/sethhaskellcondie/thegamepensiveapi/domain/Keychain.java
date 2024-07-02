package com.sethhaskellcondie.thegamepensiveapi.domain;

import java.util.List;

/**
 * Each entity will have a key that is essentially the name of the entity it will
 * act like a key when implementing features like filters and custom fields.
 * The keys are stored on the keychain, this will be the master list of all the
 * entities in the system, when a new entity is created a new key should be made for it here.
 * <p>
 * Each entity will have a getKey() function that will pull the key from the keychain, but
 * other parts of the program can pull keys from the keychain for comparison.
 * <p>
 * Keys will be singular, and lowercase with spaces.
 */
public class Keychain {
    public static final String SYSTEM_KEY = "system";
    public static final String TOY_KEY = "toy";
    public static final String VIDEO_GAME_KEY = "videoGame";
    public static final String VIDEO_GAME_BOX_KEY = "videoGameBox";

    public static List<String> getAllKeys() {
        return List.of(
            SYSTEM_KEY,
            TOY_KEY,
            VIDEO_GAME_KEY,
            VIDEO_GAME_BOX_KEY
        );
    }

    //This is used to construct the filter objects into SQL, this function should return the table alias used in the base query of that entities' repository
    //The table alias for custom fields is always 'fields' and for custom field values is always 'values'
    public static String getTableAliasByKey(String key) {
        switch (key) {
            case SYSTEM_KEY -> {
                return "systems";
            }
            case TOY_KEY -> {
                return "toys";
            }
            case VIDEO_GAME_KEY -> {
                return "video_games";
            }
            case VIDEO_GAME_BOX_KEY -> {
                return "video_game_boxes";
            }
            default -> {
                throw new RuntimeException("Keychain.getTableAliasByKey() called with unknown key:" + key + " available keys are: " + String.join(", ", getAllKeys()));
            }
        }
    }
}
