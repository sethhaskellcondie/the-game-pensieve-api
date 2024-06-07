package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import org.springframework.stereotype.Component;

@Component
public class FilterGateway {

    private final FilterService service;

    public FilterGateway(FilterService service) {
        this.service = service;
    }

    public FilterResponseDto getFiltersByKey(String key) {
        return new FilterResponseDto(
                key + "_filters",
                service.getFilterFieldsByKey(key),
                service.getFiltersByKey(key)
        );
    }
}
