package com.sethhaskellcondie.thegamepensieveapi.domain.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * The only metadata keys a showcase view may read. Everything else in the owner's metadata — anything
     * a future feature stores there — is invisible to a showcase visitor.
     *
     * <p><strong>This list must stay in step with {@code SecurityConfig.PUBLIC_METADATA_READ}</strong>,
     * which opens the same four keys to ANONYMOUS callers. The two are not redundant, and it is worth being
     * precise about why: {@code SecurityConfig}'s list is a URL allowlist on the security chain, so it only
     * decides what a caller with no token may reach. An <em>authenticated</em> caller passes
     * {@code authenticated()} on every metadata route, so sending {@code X-Showcase: <slug>} used to scope
     * RLS into the showcase owner's tenant and then read {@code GET /v1/metadata} (list-all) or
     * {@code /v1/metadata/{any-key}} — the owner's saved filters, sort preferences, and whatever else. The
     * enumeration guard existed only at the anonymous layer, so having an account was enough to walk past
     * it. This constant is the same policy enforced where it belongs: in the gateway, keyed on
     * {@code isShowcaseView()} rather than on whether a token was presented.
     */
    public static final Set<String> SHOWCASE_READABLE_KEYS = Set.of(
            UI_SETTINGS_KEY,
            "default_sort_options",
            "saved-filters",
            "saved-filter-categories"
    );

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

    /** Whether a showcase view is allowed to read this key at all. */
    public static boolean isReadableByShowcase(String key) {
        return SHOWCASE_READABLE_KEYS.contains(key);
    }

    /**
     * What a showcase view's {@code GET /metadata} returns: the owner's rows narrowed to
     * {@link #SHOWCASE_READABLE_KEYS}, with the guest ui-settings substituted in (appended if the owner
     * stored none). Keys outside the allowlist are dropped rather than refused — a list endpoint returning
     * fewer rows is the natural shape, and a 403 here would leak the fact that the owner has other keys.
     */
    public static List<Metadata> withGuestUiSettings(List<Metadata> ownerMetadata) {
        final List<Metadata> result = new ArrayList<>();
        boolean replaced = false;
        for (Metadata metadata : ownerMetadata) {
            if (UI_SETTINGS_KEY.equals(metadata.key())) {
                result.add(guestUiSettings());
                replaced = true;
            } else if (isReadableByShowcase(metadata.key())) {
                result.add(metadata);
            }
        }
        if (!replaced) {
            result.add(guestUiSettings());
        }
        return result;
    }

    /** The allowlisted keys, alphabetically, for error messages — Set.of has no defined iteration order. */
    public static String readableKeysForMessage() {
        return SHOWCASE_READABLE_KEYS.stream().sorted().collect(Collectors.joining(", "));
    }
}
