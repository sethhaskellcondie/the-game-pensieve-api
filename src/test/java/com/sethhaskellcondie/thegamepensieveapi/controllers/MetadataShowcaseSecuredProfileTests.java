package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Metadata on the public showcase read surface, under the {@code secured} profile. A showcase ({@code X-Showcase},
 * GUEST) view is served a fixed guest {@code ui-settings} in place of the owner's own — beginner mode on, every
 * other mode off, both default views {@code "list"}, every standard field shown — so a public visitor never sees
 * the owner's personal editor state. Every other key passes through to the owner's own row via RLS, so a guest
 * mirrors, and stays in sync with, the owner's configured {@code default_sort_options}, {@code saved-filters}, and
 * {@code saved-filter-categories}. Writes are rejected (403) on a showcase view; an authenticated owner with no
 * header reads their own settings unchanged.
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class MetadataShowcaseSecuredProfileTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PASSWORD = "Sup3rSecret!";
    private static final String METADATA_URL = "/v1/metadata";
    private static final String UI_SETTINGS_URL = METADATA_URL + "/ui-settings";
    private static final String SORT_URL = METADATA_URL + "/default_sort_options";
    private static final String SHOWCASE_HEADER = "X-Showcase";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    /**
     * Given a showcase owner whose own ui-settings have beginner mode OFF, developer mode ON, and a standard field
     * hidden, then a GUEST showcase view of ui-settings is the fixed guest payload — the owner's contradicting
     * settings never leak into the public view.
     */
    @Test
    void showcaseView_UiSettings_ReturnsFixedGuestSettings() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwner();
        // The owner's OWN ui-settings deliberately contradict the guest payload on every asserted field.
        putMetadata(owner.token(), "ui-settings",
                "{\"beginner_mode\":false,\"developer_mode\":true,\"video_games_default_view\":\"shelf\","
                        + "\"standard_fields\":{\"toy\":{\"set\":false}}}");

        final JsonNode guest = readValue(mockMvc.perform(get(UI_SETTINGS_URL)
                .header(SHOWCASE_HEADER, owner.slug())).andExpect(status().isOk()));

        assertTrue(guest.get("beginner_mode").asBoolean(), "Guest showcase view has beginner mode on.");
        assertFalse(guest.get("developer_mode").asBoolean(), "Guest showcase view has developer mode off.");
        assertFalse(guest.get("mass_input_mode").asBoolean(), "Guest showcase view has mass input mode off.");
        assertFalse(guest.get("mass_edit_mode").asBoolean(), "Guest showcase view has mass edit mode off.");
        assertFalse(guest.get("hide_animations").asBoolean(), "Guest showcase view has hide animations off.");
        assertEquals("list", guest.get("video_games_default_view").asText(), "Guest video-games view is list.");
        assertEquals("list", guest.get("board_games_default_view").asText(), "Guest board-games view is list.");
        assertTrue(guest.get("standard_fields").get("toy").get("set").asBoolean(),
                "Every standard field is shown for a guest, overriding the owner's hidden toy.set.");
    }

    /**
     * Given an owner who has no ui-settings row at all, then a GUEST showcase view still gets the fixed guest
     * payload (it is synthesized, never read from the database).
     */
    @Test
    void showcaseView_UiSettings_WithoutOwnerRow_StillReturnsGuestSettings() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwner();

        final JsonNode guest = readValue(mockMvc.perform(get(UI_SETTINGS_URL)
                .header(SHOWCASE_HEADER, owner.slug())).andExpect(status().isOk()));

        assertTrue(guest.get("beginner_mode").asBoolean(), "Guest ui-settings are served even with no owner row.");
    }

    /**
     * Given an owner who has configured default sort options, then a GUEST showcase view reads exactly the owner's
     * row (mirroring), and after the owner updates it the showcase view reflects the update.
     */
    @Test
    void showcaseView_DefaultSortOptions_MirrorsOwnerAndStaysInSync() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwner();
        putMetadata(owner.token(), "default_sort_options",
                "{\"video_game\":[{\"field\":\"title\",\"direction\":\"asc\"}]}");

        final JsonNode mirrored = readValue(mockMvc.perform(get(SORT_URL)
                .header(SHOWCASE_HEADER, owner.slug())).andExpect(status().isOk()));
        assertEquals("title", mirrored.get("video_game").get(0).get("field").asText(),
                "A showcase view mirrors the owner's default sort options.");
        assertEquals("asc", mirrored.get("video_game").get(0).get("direction").asText());

        // The owner updates their default sort; the showcase view must reflect the new value.
        patchMetadata(owner.token(), "default_sort_options",
                "{\"video_game\":[{\"field\":\"title\",\"direction\":\"desc\"}]}");
        final JsonNode updated = readValue(mockMvc.perform(get(SORT_URL)
                .header(SHOWCASE_HEADER, owner.slug())).andExpect(status().isOk()));
        assertEquals("desc", updated.get("video_game").get(0).get("direction").asText(),
                "When the owner updates their default sort, the showcase view stays in sync.");
    }

    /**
     * Given an authenticated owner sending {@code X-Showcase} (a GUEST showcase view), then metadata writes are
     * rejected (403) even though the caller is logged in — a public showcase is read-only.
     */
    @Test
    void showcaseView_Writes_Are403() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwner();
        putMetadata(owner.token(), "default_sort_options", "{\"video_game\":[]}");

        mockMvc.perform(post(METADATA_URL)
                        .header("Authorization", "Bearer " + owner.token())
                        .header(SHOWCASE_HEADER, owner.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("some_key", "{\"a\":1}")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(SORT_URL)
                        .header("Authorization", "Bearer " + owner.token())
                        .header(SHOWCASE_HEADER, owner.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"{\\\"video_game\\\":[]}\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(SORT_URL)
                        .header("Authorization", "Bearer " + owner.token())
                        .header(SHOWCASE_HEADER, owner.slug()))
                .andExpect(status().isForbidden());
    }

    /**
     * Given an authenticated owner with no header, then reading their own ui-settings is unchanged — they see their
     * own stored settings, not the guest payload. Guards against the override hijacking a real own-account read.
     */
    @Test
    void authenticatedOwnerNoHeader_UiSettings_ReturnsOwnSettings() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwner();
        putMetadata(owner.token(), "ui-settings",
                "{\"beginner_mode\":false,\"developer_mode\":true}");

        final JsonNode own = readValue(mockMvc.perform(get(UI_SETTINGS_URL)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()));
        assertFalse(own.get("beginner_mode").asBoolean(), "An owner reads their own beginner mode (off), not guest.");
        assertTrue(own.get("developer_mode").asBoolean(), "An owner reads their own developer mode (on), not guest.");
    }

    /**
     * Given a showcase owner who has configured saved filters and categories, then a GUEST showcase view — even an
     * anonymous one with no token — reads exactly the owner's rows (mirroring), and reflects the owner's later edits.
     * Proves the saved-filters / saved-filter-categories keys join the anonymous showcase read surface.
     */
    @Test
    void showcaseView_SavedFiltersAndCategories_MirrorOwnerForAnonymousGuest() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwner();
        putMetadata(owner.token(), "saved-filters", "{\"filters\":[{\"name\":\"Favorites\"}]}");
        putMetadata(owner.token(), "saved-filter-categories", "{\"categories\":[{\"name\":\"Shelf\"}]}");

        // Anonymous (no Authorization) X-Showcase reads reach the owner's rows via the opened read surface.
        final JsonNode filters = readValue(mockMvc.perform(get(METADATA_URL + "/saved-filters")
                .header(SHOWCASE_HEADER, owner.slug())).andExpect(status().isOk()));
        assertEquals("Favorites", filters.get("filters").get(0).get("name").asText(),
                "An anonymous showcase view mirrors the owner's saved filters.");

        final JsonNode categories = readValue(mockMvc.perform(get(METADATA_URL + "/saved-filter-categories")
                .header(SHOWCASE_HEADER, owner.slug())).andExpect(status().isOk()));
        assertEquals("Shelf", categories.get("categories").get(0).get("name").asText(),
                "An anonymous showcase view mirrors the owner's saved-filter categories.");

        // When the owner edits their filters, the showcase view reflects it — the guest stays in sync.
        patchMetadata(owner.token(), "saved-filters", "{\"filters\":[{\"name\":\"Renamed\"}]}");
        final JsonNode updated = readValue(mockMvc.perform(get(METADATA_URL + "/saved-filters")
                .header(SHOWCASE_HEADER, owner.slug())).andExpect(status().isOk()));
        assertEquals("Renamed", updated.get("filters").get(0).get("name").asText(),
                "When the owner updates their saved filters, the showcase view stays in sync.");
    }

    // ------------------------------- Private helpers -------------------------------

    private record ShowcaseOwner(String email, String token, String slug) {
    }

    private ShowcaseOwner createShowcaseOwner() throws Exception {
        final String email = factory.randomEmail();
        final String token = registerAndLogin(email);
        makePaid(email);
        final String slug = "showcase-" + uniqueSuffix();
        grantShowcase(email, slug);
        return new ShowcaseOwner(email, token, slug);
    }

    /** Upsert a metadata key as the authenticated owner (POST revives/creates), returning nothing. */
    private void putMetadata(String token, String key, String value) throws Exception {
        mockMvc.perform(post(METADATA_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(key, value)))
                .andExpect(status().isCreated());
    }

    private void patchMetadata(String token, String key, String value) throws Exception {
        mockMvc.perform(patch(METADATA_URL + "/" + key)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isOk());
    }

    private String postBody(String key, String value) {
        return "{\"metadata\":{\"id\":null,\"key\":\"" + key + "\",\"value\":\""
                + value.replace("\"", "\\\"") + "\",\"createdAt\":null,\"updatedAt\":null,\"deletedAt\":null}}";
    }

    /** Extract and parse the metadata record's `value` (a JSON-encoded string) from a response. */
    private JsonNode readValue(ResultActions result) throws Exception {
        final String body = result.andReturn().getResponse().getContentAsString();
        final String value = objectMapper.readTree(body).get("data").get("value").asText();
        return objectMapper.readTree(value);
    }

    private void grantShowcase(String email, String slug) {
        jdbcTemplate.update("UPDATE users SET showcase_slug = ?, showcase_name = ? WHERE email = ?",
                slug, "Showcase " + slug, email);
    }

    private void makePaid(String email) {
        jdbcTemplate.update(
                "UPDATE users SET plan = 'paid', subscription_status = 'active', "
                        + "access_until = now() + interval '30 days' WHERE email = ?",
                email);
    }

    private String uniqueSuffix() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerAndLogin(String email) throws Exception {
        factory.registerReturnResult(email, PASSWORD).andExpect(status().isCreated());
        return factory.extractToken(factory.loginReturnResult(email, PASSWORD), "accessToken");
    }
}
