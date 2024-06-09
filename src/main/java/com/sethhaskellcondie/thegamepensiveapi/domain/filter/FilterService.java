package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FilterService {
    private final CustomFieldRepository customFieldRepository;

    public FilterService(CustomFieldRepository customFieldRepository) {
        this.customFieldRepository = customFieldRepository;
    }

    public Map<String, String> getFilterFieldsByKey(String key) {
        Map<String, String> fields = FilterEntity.getFilterFieldsByKey(key);
        List<CustomField> customFields = customFieldRepository.getAllByKey(key);
        for (CustomField customField : customFields) {
            fields.put(customField.name(), customField.type());
        }
        return fields;
    }

    public Map<String, List<String>> getFiltersByKey(String key) {
        Map<String, String> fields = getFilterFieldsByKey(key);
        //using a linkedHashMap to preserve the order of the elements as they are added to the map.
        Map<String, List<String>> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            filters.put(field.getKey(), Filter.getFilterOperators(field.getValue(), false));
        }
        return filters;
    }

    public List<Filter> convertFilterRequestDtosToFilters(List<FilterRequestDto> filterRequestDtos) {
        String key = filterRequestDtos.get(0).key();
        List<String> customFieldNames = customFieldRepository.getAllByKey(key).stream().map(CustomField::name).toList();
        List<Filter> filters = new ArrayList<>();
        for (FilterRequestDto filterRequestDto : filterRequestDtos) {
            filters.add(new Filter(
                    filterRequestDto.key(),
                    filterRequestDto.field(),
                    filterRequestDto.operator(),
                    filterRequestDto.operand(),
                    customFieldNames.contains(filterRequestDto.field())
            ));
        }
        return filters;
    }
}
