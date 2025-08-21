package com.sethhaskellcondie.thegamepensieveapi.domain.metadata;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MetadataGateway {
    private final MetadataRepository repository;

    public MetadataGateway(MetadataRepository repository) {
        this.repository = repository;
    }

    public Metadata createNew(Metadata metadata) {
        return repository.insertMetadata(metadata);
    }

    public List<Metadata> getAllMetadata() {
        return repository.getAllMetadata();
    }

    public Metadata getByKey(String key) {
        return repository.getByKey(key);
    }

    public Metadata updateValue(Metadata metadata) {
        return repository.updateValue(metadata, true);
    }

    public void deleteByKey(String key) {
        repository.deleteByKey(key);
    }
}