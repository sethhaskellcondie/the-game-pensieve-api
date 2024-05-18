package com.sethhaskellcondie.thegamepensiveapi.domain;

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
}
