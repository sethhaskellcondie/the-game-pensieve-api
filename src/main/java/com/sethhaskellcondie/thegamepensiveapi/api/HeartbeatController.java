package com.sethhaskellcondie.thegamepensiveapi.api;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.toy.ToyGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class HeartbeatController {

    private final SystemGateway systemGateway;
    private final ToyGateway toyGateway;
    private final CustomFieldRepository customFieldRepository;

    public HeartbeatController(SystemGateway systemGateway, ToyGateway toyGateway, CustomFieldRepository customFieldRepository) {
        this.systemGateway = systemGateway;
        this.toyGateway = toyGateway;
        this.customFieldRepository = customFieldRepository;
    }

    @GetMapping("/v1/heartbeat")
    public String heartbeat() {
        return "thump thump";
    }

    @Deprecated
    @PostMapping("/v1/seedMyCollection")
    public String seedMyCollectionData() {

        // ----- seed custom fields -----
        final CustomField releaseDateCustomField = customFieldRepository.insertCustomField("Release Date", "number", Keychain.SYSTEM_KEY);

        CustomFieldValue releaseDate1987 = new CustomFieldValue(releaseDateCustomField.id(), releaseDateCustomField.name(), releaseDateCustomField.type(), "1987");
        CustomFieldValue releaseDate1999 = new CustomFieldValue(releaseDateCustomField.id(), releaseDateCustomField.name(), releaseDateCustomField.type(), "1999");
        CustomFieldValue releaseDate2007 = new CustomFieldValue(releaseDateCustomField.id(), releaseDateCustomField.name(), releaseDateCustomField.type(), "2007");
        CustomFieldValue releaseDate2012 = new CustomFieldValue(releaseDateCustomField.id(), releaseDateCustomField.name(), releaseDateCustomField.type(), "2012");

        final CustomField favoriteCustomField = customFieldRepository.insertCustomField("Favorite", "boolean", Keychain.SYSTEM_KEY);

        CustomFieldValue favoriteTrue = new CustomFieldValue(favoriteCustomField.id(), favoriteCustomField.name(), favoriteCustomField.type(), "true");
        CustomFieldValue favoriteFalse = new CustomFieldValue(favoriteCustomField.id(), favoriteCustomField.name(), favoriteCustomField.type(), "false");

        final CustomField publisherCustomField = customFieldRepository.insertCustomField("Publisher", "text", Keychain.SYSTEM_KEY);

        CustomFieldValue publisherNintendo = new CustomFieldValue(publisherCustomField.id(), publisherCustomField.name(), publisherCustomField.type(), "Nintendo");
        CustomFieldValue publisherSega = new CustomFieldValue(publisherCustomField.id(), publisherCustomField.name(), publisherCustomField.type(), "Sega");
        CustomFieldValue publisherSony = new CustomFieldValue(publisherCustomField.id(), publisherCustomField.name(), publisherCustomField.type(), "Sony");

        final CustomField setCustomField = customFieldRepository.insertCustomField("Set", "text", Keychain.TOY_KEY);

        CustomFieldValue setSuperMario = new CustomFieldValue(setCustomField.id(), setCustomField.name(), setCustomField.type(), "Super Mario");
        CustomFieldValue setSmashBros = new CustomFieldValue(setCustomField.id(), setCustomField.name(), setCustomField.type(), "Smash Bros");
        CustomFieldValue setStarWars = new CustomFieldValue(setCustomField.id(), setCustomField.name(), setCustomField.type(), "Star Wars");
        CustomFieldValue setMarvel = new CustomFieldValue(setCustomField.id(), setCustomField.name(), setCustomField.type(), "Marvel");

        final CustomField rankingCustomField = customFieldRepository.insertCustomField("Ranking", "number", Keychain.TOY_KEY);

        CustomFieldValue ranking1 = new CustomFieldValue(rankingCustomField.id(), rankingCustomField.name(), rankingCustomField.type(), "1");
        CustomFieldValue ranking2 = new CustomFieldValue(rankingCustomField.id(), rankingCustomField.name(), rankingCustomField.type(), "2");
        CustomFieldValue ranking3 = new CustomFieldValue(rankingCustomField.id(), rankingCustomField.name(), rankingCustomField.type(), "3");

        final CustomField brokenCustomField = customFieldRepository.insertCustomField("Broken", "boolean", Keychain.TOY_KEY);

        CustomFieldValue brokenTrue = new CustomFieldValue(brokenCustomField.id(), brokenCustomField.name(), brokenCustomField.type(), "true");
        CustomFieldValue brokenFalse = new CustomFieldValue(brokenCustomField.id(), brokenCustomField.name(), brokenCustomField.type(), "false");

        // ----- seed systems -----
        List<CustomFieldValue> nesValues = List.of(releaseDate1987, favoriteTrue, publisherNintendo);
        systemGateway.createNew("NES", 3, false, nesValues);
        List<CustomFieldValue> snesValues = List.of(releaseDate1999, favoriteTrue, publisherNintendo);
        systemGateway.createNew("SNES", 3, false, snesValues);
        List<CustomFieldValue> genesisValues = List.of(releaseDate1999, favoriteFalse, publisherSega);
        systemGateway.createNew("Genesis", 4, false, genesisValues);
        List<CustomFieldValue> segaCdValues = List.of(releaseDate1999, favoriteFalse, publisherSega);
        systemGateway.createNew("Sega CD", 4, false, segaCdValues);
        List<CustomFieldValue> gameBoyValues = List.of(releaseDate1999, favoriteTrue, publisherNintendo);
        systemGateway.createNew("Game Boy", 4, true, gameBoyValues);
        List<CustomFieldValue> gameGearValues = List.of(releaseDate1999, favoriteFalse, publisherSega);
        systemGateway.createNew("Game Gear", 4, true, gameGearValues);
        List<CustomFieldValue> nintendo64Values = List.of(releaseDate2007, favoriteFalse, publisherNintendo);
        systemGateway.createNew("Nintendo 64", 5, false, nintendo64Values);
        List<CustomFieldValue> playstationValues = List.of(releaseDate2007, favoriteFalse, publisherSony);
        systemGateway.createNew("Playstation", 5, false, playstationValues);
        List<CustomFieldValue> playstation2Values = List.of(releaseDate2012, favoriteTrue, publisherSony);
        systemGateway.createNew("Playstation 2", 6, false, playstation2Values);
        List<CustomFieldValue> wiiValues = List.of(releaseDate2012, favoriteTrue, publisherNintendo);
        systemGateway.createNew("Wii", 7, false, wiiValues);

        // ----- seed toys -----
        List<CustomFieldValue> superMarioValues = List.of(setSuperMario, ranking2, brokenTrue);
        toyGateway.createNew("Super Mario", "Amiibo", superMarioValues);
        List<CustomFieldValue> superMarioValues2 = List.of(setSmashBros, ranking3, brokenFalse);
        toyGateway.createNew("Super Mario", "Amiibo", superMarioValues2);
        List<CustomFieldValue> vaderValues = List.of(setStarWars, ranking3, brokenTrue);
        toyGateway.createNew("Darth Vader", "Disney Infinity", vaderValues);
        List<CustomFieldValue> captainAmericaValues = List.of(setMarvel, ranking1, brokenFalse);
        toyGateway.createNew("Captain America", "Disney Infinity", captainAmericaValues);
        List<CustomFieldValue> ironManValues = List.of(setMarvel, ranking2, brokenTrue);
        toyGateway.createNew("Iron Man", "Disney Infinity", ironManValues);
        List<CustomFieldValue> donkeyKongValues = List.of(setSuperMario, ranking3, brokenFalse);
        toyGateway.createNew("Donkey Kong", "Amiibo", donkeyKongValues);
        List<CustomFieldValue> megaManValues = List.of(setSmashBros, ranking1, brokenTrue);
        toyGateway.createNew("Mega Man", "Amiibo", megaManValues);

        return "Seeding Successful";
    }
}
