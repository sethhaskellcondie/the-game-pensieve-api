package com.sethhaskellcondie.thegamepensieveapi.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sethhaskellcondie.thegamepensieveapi.TestFactory;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Custom-field definitions on the public showcase read surface, under the {@code secured} profile.
 * {@code GET /v1/custom_fields/entity/{key}} (and the list-all {@code GET /v1/custom_fields}) join the same
 * anonymous, {@code X-Showcase}-scoped, GUEST read surface as the entity read/search/filters endpoints: an
 * anonymous viewer reads the showcase owner's definitions so the front end can build custom-field columns.
 * Owner resolution, the default-showcase fallback, and the unknown-slug 404 all come from the tenant filter +
 * RLS (unchanged); writes stay authenticated and a GUEST showcase view rejects them (403).
 */
@SpringBootTest
@ActiveProfiles({"test-container", "secured"})
@AutoConfigureMockMvc
public class CustomFieldShowcaseSecuredProfileTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    private TestFactory factory;
    private static final String PASSWORD = "Sup3rSecret!";
    private static final String CUSTOM_FIELDS_URL = "/v1/custom_fields";
    private static final String ENTITY_KEY = "videoGame";
    private static final String DEFINITIONS_URL = CUSTOM_FIELDS_URL + "/entity/" + ENTITY_KEY;
    private static final String FILTERS_URL = "/v1/filters/" + ENTITY_KEY;
    private static final String SHOWCASE_HEADER = "X-Showcase";

    @BeforeEach
    void setUp() {
        factory = new TestFactory(mockMvc);
    }

    /**
     * Given an anonymous caller sending an {@code X-Showcase} slug, then {@code GET /custom_fields/entity/{key}}
     * serves exactly that owner's definitions — another owner's fields must not leak — and the same definitions
     * the {@code filters/{key}} endpoint advertises for that owner.
     */
    @Test
    void anonymousWithShowcaseHeader_ReturnsShowcaseOwnersDefinitions() throws Exception {
        final ShowcaseOwner ownerA = createShowcaseOwnerWithField();
        final ShowcaseOwner ownerB = createShowcaseOwnerWithField();

        final ResultActions viewA = mockMvc.perform(get(DEFINITIONS_URL).header(SHOWCASE_HEADER, ownerA.slug()))
                .andExpect(status().isOk());
        final List<String> names = definitionNames(viewA);
        assertTrue(names.contains(ownerA.fieldName()),
                "X-Showcase with owner A's slug should serve A's custom-field definitions.");
        assertFalse(names.contains(ownerB.fieldName()),
                "Owner B's custom fields must not leak into owner A's showcase view.");

        // The definitions match what filters advertises for the same owner (the front end reads both).
        final String filtersBody = mockMvc.perform(get(FILTERS_URL).header(SHOWCASE_HEADER, ownerA.slug()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(filtersBody.contains(ownerA.fieldName()),
                "The filters endpoint should advertise the same custom field for that owner.");
    }

    /**
     * Given an anonymous caller with no header, then the definitions endpoint serves the seeded default
     * showcase owner's fields (the same fallback the rest of the read surface uses).
     */
    @Test
    void anonymousNoHeader_ReturnsDefaultShowcaseDefinitions() throws Exception {
        final String fieldName = "Default-Showcase-Field-" + uniqueSuffix();
        seedCustomFieldOwnedByDefaultShowcase(fieldName);

        final ResultActions result = mockMvc.perform(get(DEFINITIONS_URL)).andExpect(status().isOk());
        assertTrue(definitionNames(result).contains(fieldName),
                "An anonymous request with no header should read the default showcase's definitions.");
    }

    /** Given an anonymous caller sending a slug no visible owner holds, then the request is a 404. */
    @Test
    void anonymousWithUnknownShowcaseSlug_Is404() throws Exception {
        mockMvc.perform(get(DEFINITIONS_URL).header(SHOWCASE_HEADER, "no-such-slug"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.errors[0]").value("No public showcase exists for the requested X-Showcase slug."));
    }

    /** The list-all read variant is opened for the same reason and is likewise X-Showcase-scoped. */
    @Test
    void listAll_anonymousWithShowcaseHeader_ReturnsShowcaseOwnersDefinitions() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwnerWithField();
        final ResultActions result = mockMvc.perform(get(CUSTOM_FIELDS_URL).header(SHOWCASE_HEADER, owner.slug()))
                .andExpect(status().isOk());
        assertTrue(definitionNames(result).contains(owner.fieldName()),
                "GET /custom_fields (list-all) should serve the showcase owner's definitions.");
    }

    /** Given an anonymous caller, then writes still require a token (401) — the read surface is GET-only. */
    @Test
    void anonymousWrites_Are401() throws Exception {
        mockMvc.perform(post(CUSTOM_FIELDS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatCustomFieldPayload("Anon-" + uniqueSuffix(), "text", ENTITY_KEY)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(CUSTOM_FIELDS_URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"custom_field\":{\"name\":\"x\",\"order\":0}}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(CUSTOM_FIELDS_URL + "/1"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Given an authenticated caller sending {@code X-Showcase} (a GUEST showcase view), then writes to custom
     * fields are rejected (403) even though the caller is logged in — browsing a showcase can never mutate it.
     */
    @Test
    void showcaseView_Writes_Are403() throws Exception {
        final ShowcaseOwner owner = createShowcaseOwnerWithField();
        // The owner themselves, viewing their own showcase via the header, is GUEST-scoped and cannot write.
        mockMvc.perform(post(CUSTOM_FIELDS_URL)
                        .header("Authorization", "Bearer " + owner.token())
                        .header(SHOWCASE_HEADER, owner.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatCustomFieldPayload("Should-Fail-" + uniqueSuffix(), "text", ENTITY_KEY)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(CUSTOM_FIELDS_URL + "/" + owner.fieldId())
                        .header("Authorization", "Bearer " + owner.token())
                        .header(SHOWCASE_HEADER, owner.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"custom_field\":{\"name\":\"Renamed\",\"order\":0}}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(CUSTOM_FIELDS_URL + "/" + owner.fieldId())
                        .header("Authorization", "Bearer " + owner.token())
                        .header(SHOWCASE_HEADER, owner.slug()))
                .andExpect(status().isForbidden());
    }

    /**
     * Given an authenticated owner with no header, then reading and writing their own custom fields is unchanged:
     * the definitions endpoint serves their own collection and writes still succeed.
     */
    @Test
    void authenticatedOwnCollection_ReadAndWrite_Unchanged() throws Exception {
        final String email = factory.randomEmail();
        final String token = registerAndLogin(email);
        makePaid(email);
        final String fieldName = "Own-Field-" + uniqueSuffix();
        createCustomField(token, fieldName);

        final ResultActions ownRead = mockMvc.perform(get(DEFINITIONS_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertTrue(definitionNames(ownRead).contains(fieldName),
                "An authenticated owner should read their own custom-field definitions.");
    }

    // ------------------------------- Private helpers -------------------------------

    /** A paid showcase owner plus one videoGame custom field they own (name + id captured). */
    private record ShowcaseOwner(String email, String token, String slug, String fieldName, int fieldId) {
    }

    private ShowcaseOwner createShowcaseOwnerWithField() throws Exception {
        final String email = factory.randomEmail();
        final String token = registerAndLogin(email);
        makePaid(email);
        final String slug = "showcase-" + uniqueSuffix();
        grantShowcase(email, slug);
        final String fieldName = "Showcase-Field-" + uniqueSuffix();
        final int fieldId = createCustomField(token, fieldName);
        return new ShowcaseOwner(email, token, slug, fieldName, fieldId);
    }

    /** POST a text custom field on videoGame as the authenticated caller, returning its id. */
    private int createCustomField(String token, String name) throws Exception {
        final ResultActions result = mockMvc.perform(post(CUSTOM_FIELDS_URL)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(factory.formatCustomFieldPayload(name, "text", ENTITY_KEY)))
                .andExpect(status().isCreated());
        return factory.resultToDto(result, CustomField.class).id();
    }

    /**
     * Insert a custom field owned by the seeded default showcase directly via JDBC (the secured API cannot create
     * showcase-owned rows); the superuser test connection sidesteps RLS and the tenant filter.
     */
    private void seedCustomFieldOwnedByDefaultShowcase(String name) {
        jdbcTemplate.update(
                "INSERT INTO custom_fields(name, type, entity_key, owner_id) "
                        + "VALUES (?, 'text', ?, (SELECT id FROM users WHERE is_public_showcase))",
                name, ENTITY_KEY);
    }

    private List<String> definitionNames(ResultActions result) throws Exception {
        return factory.extractDataList(result, new TypeReference<List<CustomField>>() { })
                .stream().map(CustomField::name).toList();
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
