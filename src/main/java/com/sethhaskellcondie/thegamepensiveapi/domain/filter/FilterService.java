package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FilterService {

    public Map<String, List<String>> getFiltersForResource(String resource) {
        Map<String, String> fields = Filter.getFieldsForResource(resource);
        //using a linkedHashMap to preserve the order of the elements as they are added to the map.
        Map<String, List<String>> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            filters.put(field.getKey(), Filter.getFilterOperators(field.getValue()));
        }
        return filters;
    }
}
