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
        // A showcase view reads the owner's metadata via RLS, but ui-settings is overridden with the fixed guest
        // settings so the public read surface never exposes the owner's personal editor preferences.
        if (access.isShowcaseView()) {
            return ShowcaseMetadata.withGuestUiSettings(allMetadata);
        }
        return allMetadata;
    }

    public Metadata getByKey(String key) {
        // Serve the fixed guest ui-settings for a showcase view; every other key (e.g. default_sort_options) passes
        // through to the owner's own row so a guest mirrors the owner's configured default sort.
        if (access.isShowcaseView() && ShowcaseMetadata.UI_SETTINGS_KEY.equals(key)) {
            return ShowcaseMetadata.guestUiSettings();
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
}
