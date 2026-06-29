package com.sethhaskellcondie.thegamepensieveapi.domain.auth;

/**
 * A gated action in the role-based access model. Which roles hold which capabilities is the single source of
 * truth in the {@link AccessService} capability matrix; domain code asks {@code accessService.can(...)} rather
 * than testing roles directly, so adding or altering a role is a one-line matrix change.
 * <ul>
 *   <li>{@code FILTER} — run a filtered search (an unfiltered list is always allowed). Denied → 402.</li>
 *   <li>{@code WRITE} — create/update/delete a row. Denied → 403.</li>
 *   <li>{@code BACKUP} — export the caller's data. Denied → 403.</li>
 *   <li>{@code IMPORT} — import/seed data. Denied → 403.</li>
 *   <li>{@code ACCESS_ADMIN} — reach the admin role-management API. Denied → 403.</li>
 * </ul>
 */
public enum Capability {
    FILTER,
    WRITE,
    BACKUP,
    IMPORT,
    ACCESS_ADMIN
}
