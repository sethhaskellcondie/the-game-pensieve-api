package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FilterGateway {

    private final FilterService service;

    public FilterGateway(FilterService service) {
        this.service = service;
    }

    public FilterResponseDto getFiltersForResource(String resource) {
        if (!Filter.getResourcesThatHaveFilters().contains(resource)) {
            return new FilterResponseDto(resource + "_filters", new LinkedHashMap<>(), new LinkedHashMap<>());
        }
        Map<String, List<String>> fields = service.getFiltersForResource(resource);
        return new FilterResponseDto(resource + "_filters", Filter.getFieldsForResource(resource), fields);
    }
}
