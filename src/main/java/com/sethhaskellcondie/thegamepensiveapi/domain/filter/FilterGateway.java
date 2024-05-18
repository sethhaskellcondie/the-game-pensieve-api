package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import org.springframework.stereotype.Component;

@Component
public class FilterGateway {

    private final FilterService service;

    public FilterGateway(FilterService service) {
        this.service = service;
    }

    public FilterResponseDto getFiltersForResource(String resource) {
        return new FilterResponseDto(
                resource + "_filters",
                FilterEntity.getFieldsForResource(resource),
                service.getFiltersForResource(resource)
        );
    }
}
