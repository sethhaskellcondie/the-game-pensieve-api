package com.sethhaskellcondie.thegamepensieveapi.domain.metadata;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AccessService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Capability;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionForbidden;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MetadataGateway {
    private final MetadataRepository repository;
    private final AccessService access;

    public MetadataGateway(MetadataRepository repository, AccessService access) {
        this.repository = repository;
        this.access = access;
    }

    public Metadata createNew(Metadata metadata) {
        requireWrite();
        return repository.insertMetadata(metadata);
    }

    public List<Metadata> getAllMetadata() {
        final List<Metadata> allMetadata = repository.getAllMetadata();
        // A showcase view reads the owner's metadata via RLS, but the result is narrowed to the four keys the
        // showcase actually renders from, and ui-settings is overridden with the fixed guest settings so the
        // public read surface never exposes the owner's personal editor preferences.
        if (access.isShowcaseView()) {
            return ShowcaseMetadata.withGuestUiSettings(allMetadata);
        }
        return allMetadata;
    }

    public Metadata getByKey(String key) {
        if (access.isShowcaseView()) {
            // A showcase view may read ONLY the allowlisted keys. Enforced here rather than only in
            // SecurityConfig's anonymous URL allowlist, because an authenticated caller satisfies
            // authenticated() on every metadata route and could otherwise send X-Showcase to scope RLS into
            // the owner's tenant and read any key they liked. See ShowcaseMetadata.SHOWCASE_READABLE_KEYS.
            requireShowcaseReadable(key);
            // Serve the fixed guest ui-settings; the other three pass through to the owner's own row so a
            // guest mirrors the owner's configured default sort and saved filters.
            if (ShowcaseMetadata.UI_SETTINGS_KEY.equals(key)) {
                return ShowcaseMetadata.guestUiSettings();
            }
        }
        return repository.getByKey(key);
    }

    public Metadata updateValue(Metadata metadata) {
        requireWrite();
        return repository.updateValue(metadata, true);
    }

    public void deleteByKey(String key) {
        requireWrite();
        repository.deleteByKey(key);
    }

    // Metadata is read-only on the public showcase read surface: a GUEST showcase view (and any non-writing role)
    // lacks the WRITE capability, so mutations are rejected (403). Enforcement is off in the default permit-all
    // build, so the single-user public build keeps unrestricted writes. Mirrors CustomFieldGateway.requireWrite().
    private void requireWrite() {
        if (!access.can(Capability.WRITE)) {
            throw new ExceptionForbidden("An active subscription is required to create, update, or delete data.");
        }
    }

    // 403 rather than 404: the key may well exist on the owner's row, and pretending otherwise would be a
    // lie that still answers the attacker's question differently for existing and non-existing keys.
    private void requireShowcaseReadable(String key) {
        if (!ShowcaseMetadata.isReadableByShowcase(key)) {
            throw new ExceptionForbidden(
                    "A showcase view may only read the metadata it renders from (" + ShowcaseMetadata.readableKeysForMessage() + ").");
        }
    }
}
