package com.sethhaskellcondie.thegamepensieveapi.domain.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * The metadata a public showcase (GUEST, {@code X-Showcase}) view is served in place of the owner's own, so a
 * read-only showcase presents a clean, fixed reader experience rather than the owner's personal editor state.
 *
 * <p>Only {@code ui-settings} is overridden: a public visitor always gets beginner mode on, every other mode off,
 * both collection default views {@code "list"}, and every standard field shown ({@code true} = shown). The other
 * keys a showcase view reads — notably {@code default_sort_options} — intentionally pass through to the owner's own
 * row via Row-Level Security, so a guest sees, and stays in sync with, the owner's configured default sort.
 */
public final class ShowcaseMetadata {

    /** The metadata key the front end stores UI settings under (mirrors the web's {@code UI_SETTINGS_KEY}). */
    public static final String UI_SETTINGS_KEY = "ui-settings";

    // Beginner mode on, all other modes off, both default views "list", every standard field shown. Whitespace is
    // insignificant JSON — the front end JSON.parses this value string, reading only the fields it recognizes.
    private static final String GUEST_UI_SETTINGS_VALUE = """
            {
              "mass_input_mode": false,
              "mass_edit_mode": false,
              "developer_mode": false,
              "hide_animations": false,
              "beginner_mode": true,
              "video_games_default_view": "list",
              "board_games_default_view": "list",
              "standard_fields": {
                "toy": { "set": true },
                "system": { "generation": true, "handheld": true },
                "board_game": { "boxes": true },
                "board_game_box": { "board_game": true, "expansion": true, "stand_alone": true, "base_set": true },
                "video_game": { "system": true, "boxes": true },
                "video_game_box": { "system": true, "games": true, "physical": true, "collection": true }
              }
            }""";

    private ShowcaseMetadata() {
    }

    /** The fixed ui-settings a showcase (GUEST) view is served, independent of the owner's own stored settings. */
    public static Metadata guestUiSettings() {
        return new Metadata(0, UI_SETTINGS_KEY, GUEST_UI_SETTINGS_VALUE, null, null, null);
    }

    /**
     * The owner's metadata list with the guest ui-settings substituted in (appended if the owner stored none), for a
     * showcase view's {@code GET /metadata}. Every other key passes through unchanged.
     */
    public static List<Metadata> withGuestUiSettings(List<Metadata> ownerMetadata) {
        final List<Metadata> result = new ArrayList<>();
        boolean replaced = false;
        for (Metadata metadata : ownerMetadata) {
            if (UI_SETTINGS_KEY.equals(metadata.key())) {
                result.add(guestUiSettings());
                replaced = true;
            } else {
                result.add(metadata);
            }
        }
        if (!replaced) {
            result.add(guestUiSettings());
        }
        return result;
    }
}
