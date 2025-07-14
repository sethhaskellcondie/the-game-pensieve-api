package com.sethhaskellcondie.thegamepensieveapi.domain.filter;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalError;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInvalidFilter;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.FIELD_TYPE_BOOLEAN;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.FIELD_TYPE_NUMBER;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.FIELD_TYPE_PAGINATION;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.FIELD_TYPE_SORT;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.FIELD_TYPE_TEXT;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.FIELD_TYPE_TIME;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_BEFORE;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_CONTAINS;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_ENDS_WITH;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_EQUALS;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_GREATER_THAN;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_GREATER_THAN_EQUAL_TO;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_LESS_THAN;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_LESS_THAN_EQUAL_TO;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_LIMIT;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_NOT_EQUALS;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_OFFSET;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_ORDER_BY;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_ORDER_BY_DESC;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_SINCE;
import static com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter.OPERATOR_STARTS_WITH;
import static java.lang.Integer.parseInt;

@Service
public class FilterService {
    private final CustomFieldRepository customFieldRepository;

    public FilterService(CustomFieldRepository customFieldRepository) {
        this.customFieldRepository = customFieldRepository;
    }

    public Map<String, String> getFilterFieldsByKey(String key) {
        Map<String, String> fields = FilterEntity.getNonCustomFieldFiltersByKey(key);
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
            filters.put(field.getKey(), FilterService.getFilterOperators(field.getValue(), false));
        }
        return filters;
    }

    public List<Filter> convertFilterRequestDtosToFilters(List<FilterRequestDto> filterRequestDtos) {
        if (null == filterRequestDtos || filterRequestDtos.isEmpty()) {
            return new ArrayList<>();
        }

        String key = filterRequestDtos.get(0).key();
        List<CustomField> customFields = customFieldRepository.getAllByKey(key);

        Map<String, String> customFieldFilterFields = new HashMap<>();
        for (CustomField customField : customFields) {
            customFieldFilterFields.put(customField.name(), customField.type());
        }

        Map<String, String> filterFields = FilterEntity.getNonCustomFieldFiltersByKey(key);
        List<Filter> filters = new ArrayList<>();
        for (FilterRequestDto filterRequestDto : filterRequestDtos) {
            if (customFieldFilterFields.containsKey(filterRequestDto.field())) {
                filters.add(new Filter(
                        filterRequestDto.key(),
                        customFieldFilterFields.get(filterRequestDto.field()),
                        filterRequestDto.field(),
                        filterRequestDto.operator(),
                        filterRequestDto.operand(),
                        true)
                );
            } else {
                filters.add(new Filter(
                        filterRequestDto.key(),
                        filterFields.get(filterRequestDto.field()),
                        filterRequestDto.field(),
                        filterRequestDto.operator(),
                        filterRequestDto.operand(),
                        false)
                );
            }
        }
        return filters;
    }

    /**
     * These are the characters and words that are not allowed in any filter strings,
     * this is a protection against SQL injection in the system.
     */
    public static List<String> getBlacklistedWords() {
        return List.of(
                ";",          // ; allows a statement to be terminated, then start a new one
                "=",          // = allows boolean based injection
                "sleep(",     // 'sleep' allows time based injection
                " or ",       // 'or' allows boolean based injection
                " union ",    // 'union' allows union based injection
                " delete ",   // 'delete' is not allowed
                " select "    // 'select' is not allowed
        );
    }

    /**
     * All fields can use the sort (order_by) operators, if includeSort is 'true' the sort filters are included in EACH list of field types
     * if includeSort is 'false' then it will only be included in the 'all_fields' list
     * generally includeSort is set to false when we are returning the filters in a response
     * and includeSort is set to true when performing internal validation on incoming requests
     */
    public static List<String> getFilterOperators(String fieldType, boolean includeSort) {
        List<String> filters = new ArrayList<>();
        if (null == fieldType) {
            return filters;
        }
        switch (fieldType) {
            case FIELD_TYPE_TEXT -> {
                filters.add(OPERATOR_EQUALS);
                filters.add(OPERATOR_NOT_EQUALS);
                filters.add(OPERATOR_CONTAINS);
                filters.add(OPERATOR_STARTS_WITH);
                filters.add(OPERATOR_ENDS_WITH);
            }
            case FIELD_TYPE_NUMBER -> {
                filters.add(OPERATOR_EQUALS);
                filters.add(OPERATOR_NOT_EQUALS);
                filters.add(OPERATOR_GREATER_THAN);
                filters.add(OPERATOR_GREATER_THAN_EQUAL_TO);
                filters.add(OPERATOR_LESS_THAN);
                filters.add(OPERATOR_LESS_THAN_EQUAL_TO);
            }
            case FIELD_TYPE_BOOLEAN -> {
                filters.add(OPERATOR_EQUALS);
            }
            case FIELD_TYPE_TIME -> {
                filters.add(OPERATOR_SINCE);
                filters.add(OPERATOR_BEFORE);
            }
            case FIELD_TYPE_SORT -> {
                if (!includeSort) {
                    filters.add(OPERATOR_ORDER_BY);
                    filters.add(OPERATOR_ORDER_BY_DESC);
                }
            }
            case FIELD_TYPE_PAGINATION -> {
                filters.add(OPERATOR_LIMIT);
                filters.add(OPERATOR_OFFSET);
            }
            default -> {
                return new ArrayList<>();
            }
        }
        if (includeSort) {
            filters.add(OPERATOR_ORDER_BY);
            filters.add(OPERATOR_ORDER_BY_DESC);
        }
        return filters;
    }

    public static List<Filter> validateAndOrderFilters(List<Filter> filters, List<CustomField> customFields) throws ExceptionInvalidFilter {
        // This ExceptionInvalidFilter will travel through validateAndOrderFilters collecting exceptions when errors are found
        // then at the end if exceptions are found then the exception is thrown else the list of filters are returned validated and ordered properly
        ExceptionInvalidFilter exceptionInvalidFilter = new ExceptionInvalidFilter();

        for (Filter filter : filters) {
            Map<String, String> fields;
            if (filter.isCustom()) {
                fields = getFiltersByKeyIncludingCustomFields(filter.getKey(), customFields);
            } else {
                fields = FilterEntity.getNonCustomFieldFiltersByKey(filter.getKey());
            }
            if (!fields.containsKey(filter.getField())) {
                exceptionInvalidFilter.addException(filter.getField() + " is not allowed for " + filter.getKey() + ".");
            }
            exceptionInvalidFilter = checkForBlacklistedWords(filter, exceptionInvalidFilter);

            String fieldType = fields.get(filter.getField());
            List<String> operators = getFilterOperators(fieldType, true);
            if (!Objects.equals(filter.getType(), fieldType)) {
                exceptionInvalidFilter.addException("Internal Error: Malformed Filter the filter field type '" + filter.getType()
                        + "' did not match the computed field type '" + fieldType + "'");
            }
            if (!operators.contains(filter.getOperator())) {
                exceptionInvalidFilter.addException(filter.getField() + " is not allowed with operator " + filter.getOperator() + ".");
            }

            if (null == fieldType) {
                continue;
            }
            switch (fieldType) {
                case FIELD_TYPE_NUMBER, FIELD_TYPE_PAGINATION -> exceptionInvalidFilter = additionalNumberAndPaginationFilterValidation(filter, exceptionInvalidFilter);
                case FIELD_TYPE_BOOLEAN -> exceptionInvalidFilter = additionalBooleanFilterValidation(filter, exceptionInvalidFilter);
                case FIELD_TYPE_TIME -> exceptionInvalidFilter = additionalTimeValidation(filter, exceptionInvalidFilter);
                default -> { }
            }
        }

        return orderFilters(filters, exceptionInvalidFilter);
    }

    private static Map<String, String> getFiltersByKeyIncludingCustomFields(String key, List<CustomField> customFields) {
        Map<String, String> fields = FilterEntity.getNonCustomFieldFiltersByKey(key);
        for (CustomField customField : customFields) {
            fields.put(customField.name(), customField.type());
        }
        return fields;
    }

    private static ExceptionInvalidFilter checkForBlacklistedWords(Filter filter, ExceptionInvalidFilter exceptionInvalidFilter) {
        for (String blacklistedWord : getBlacklistedWords()) {
            if (filter.getOperand().contains(blacklistedWord)) {
                exceptionInvalidFilter.addException(blacklistedWord + " is not allowed in any filters.");
            }
        }
        return exceptionInvalidFilter;
    }

    private static ExceptionInvalidFilter additionalNumberAndPaginationFilterValidation(Filter filter, ExceptionInvalidFilter exceptionInvalidFilter) {
        if (Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY) || Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY_DESC)) {
            return exceptionInvalidFilter;
        }
        try {
            parseInt(filter.getOperand());
        } catch (NumberFormatException exception) {
            exceptionInvalidFilter.addException("Number and pagination filters must include whole numbers as operands.");
        }
        return exceptionInvalidFilter;
    }

    private static ExceptionInvalidFilter additionalBooleanFilterValidation(Filter filter, ExceptionInvalidFilter exceptionInvalidFilter) {
        if (Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY) || Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY_DESC)) {
            return exceptionInvalidFilter;
        }
        if (!Objects.equals(filter.getOperand(), "true") && !Objects.equals(filter.getOperand(), "false")) {
            exceptionInvalidFilter.addException("Boolean type filters must have their operands match exactly 'true' or 'false'.");
        }
        return exceptionInvalidFilter;
    }

    private static ExceptionInvalidFilter additionalTimeValidation(Filter filter, ExceptionInvalidFilter exceptionInvalidFilter) {
        if (Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY) || Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY_DESC)) {
            return exceptionInvalidFilter;
        }
        try {
            Timestamp.valueOf(filter.getOperand());
        } catch (IllegalArgumentException exception) {
            exceptionInvalidFilter.addException("Operands for Time filters must be able to be formatted to YYYY-MM-DD HH24:MI:SS (24 hour) format. Example: 2024-05-17 00:00:00");
        }
        return exceptionInvalidFilter;
    }

    private static List<Filter> orderFilters(List<Filter> filters, ExceptionInvalidFilter exceptionInvalidFilter) throws ExceptionInvalidFilter {
        List<Filter> whereFilters = new ArrayList<>();
        Filter orderByFilter = null;
        Filter limitFilter = null;
        Filter offsetFilter = null;

        for (Filter filter : filters) {
            switch (filter.getOperator()) {
                case OPERATOR_ORDER_BY, OPERATOR_ORDER_BY_DESC -> {
                    if (null == orderByFilter) {
                        orderByFilter = filter;
                    } else {
                        exceptionInvalidFilter.addException("No more than one 'order_by' or 'order_by_desc' filter allowed in a single request");
                    }
                }
                case OPERATOR_LIMIT -> {
                    if (null == limitFilter) {
                        limitFilter = filter;
                    } else {
                        exceptionInvalidFilter.addException("No more than one 'limit' filter allowed in a single request");
                    }
                }
                case OPERATOR_OFFSET -> {
                    if (null == offsetFilter) {
                        offsetFilter = filter;
                    } else {
                        exceptionInvalidFilter.addException("No more than one 'offset' filter allowed in a single request");
                    }
                }
                default -> {
                    whereFilters.add(filter);
                }
            }
        }

        if (null == limitFilter && null != offsetFilter) {
            exceptionInvalidFilter.addException("'offset' filter is not allowed without also including one 'limit' filter");
        }

        if (!exceptionInvalidFilter.isEmpty()) {
            throw exceptionInvalidFilter;
        }

        whereFilters.add(orderByFilter);
        whereFilters.add(limitFilter);
        whereFilters.add(offsetFilter);
        return whereFilters.stream().filter(Objects::nonNull).toList();
    }

    public static List<String> formatWhereStatements(List<Filter> filters) {
        List<String> whereStatements = new ArrayList<>();
        for (Filter filter : filters) {
            if (filter.isCustom()) {
                //The table alias for custom_fields is always 'fields' as setup in each EntityRepository getBaseQueryJoinCustomFieldValues();
                whereStatements.add(" AND fields.name = '" + filter.getField() + "'");
                whereStatements.add(getCustomFilterWhereStatement(filter));
            } else {
                whereStatements.add(getWhereStatement(filter));
            }
        }
        return whereStatements;
    }

    private static String getCustomFilterWhereStatement(Filter filter) {
        final String whereStatement;
        //The table alias for custom_field_values is always 'values' as setup in each EntityRepository getBaseQueryJoinCustomFieldValues();
        switch (filter.getType()) {
            case CustomField.TYPE_TEXT, CustomField.TYPE_BOOLEAN -> {
                switch (filter.getOperator()) {
                    case OPERATOR_EQUALS -> whereStatement = " AND values.value_text = ?";
                    case OPERATOR_NOT_EQUALS -> whereStatement = " AND values.value_text <> ?";
                    case OPERATOR_CONTAINS, OPERATOR_STARTS_WITH, OPERATOR_ENDS_WITH -> whereStatement = " AND values.value_text LIKE ?";
                    default -> whereStatement = "";
                }
            }
            case CustomField.TYPE_NUMBER -> {
                switch (filter.getOperator()) {
                    case OPERATOR_EQUALS -> whereStatement = " AND values.value_number = ?";
                    case OPERATOR_NOT_EQUALS -> whereStatement = " AND values.value_number <> ?";
                    case OPERATOR_GREATER_THAN -> whereStatement = " AND values.value_number > ?";
                    case OPERATOR_LESS_THAN -> whereStatement = " AND values.value_number < ?";
                    case OPERATOR_GREATER_THAN_EQUAL_TO -> whereStatement = " AND values.value_number >= ?";
                    case OPERATOR_LESS_THAN_EQUAL_TO -> whereStatement = " AND values.value_number <= ?";
                    default -> whereStatement = "";
                }
            }
            default -> whereStatement = "";
        }
        return whereStatement;
    }

    private static String getWhereStatement(Filter filter) {
        final String tableAlias = Keychain.getTableAliasByKey(filter.getKey());
        final String whereStatement;
        //statements should always begin with a space and assume that the first WHERE statement is already included
        switch (filter.getOperator()) {
            case OPERATOR_EQUALS -> whereStatement = " AND " + tableAlias + "." + filter.getField() + " = ?";
            case OPERATOR_NOT_EQUALS -> whereStatement = " AND " + tableAlias + "." + filter.getField() + " <> ?";
            case OPERATOR_CONTAINS, OPERATOR_STARTS_WITH, OPERATOR_ENDS_WITH -> whereStatement = " AND " + tableAlias + "." + filter.getField() + " LIKE ?";
            case OPERATOR_GREATER_THAN -> whereStatement = " AND " + tableAlias + "." + filter.getField() + " > ?";
            case OPERATOR_LESS_THAN -> whereStatement = " AND " + tableAlias + "." + filter.getField() + " < ?";
            case OPERATOR_GREATER_THAN_EQUAL_TO -> whereStatement = " AND " + tableAlias + "." + filter.getField() + " >= ?";
            case OPERATOR_LESS_THAN_EQUAL_TO -> whereStatement = " AND " + tableAlias + "." + filter.getField() + " <= ?";
            // https://www.postgresqltutorial.com/postgresql-date-functions/postgresql-to_timestam/
            case OPERATOR_SINCE -> whereStatement = " AND " + tableAlias + "." + filter.getField() + " >= TO_TIMESTAMP( ? , 'YYYY-MM-DD HH24:MI:SS')";
            case OPERATOR_BEFORE -> whereStatement = " AND " + tableAlias + "." + filter.getField() + " <= TO_TIMESTAMP( ? , 'YYYY-MM-DD HH24:MI:SS')";
            case OPERATOR_ORDER_BY -> whereStatement = " ORDER BY " + tableAlias + "." + filter.getField() + " ASC";
            case OPERATOR_ORDER_BY_DESC -> whereStatement = " ORDER BY " + tableAlias + "." + filter.getField() + " DESC";
            case OPERATOR_LIMIT -> whereStatement = " LIMIT ?";
            case OPERATOR_OFFSET -> whereStatement = " OFFSET ?";
            default -> whereStatement = "";
        }
        return whereStatement;
    }

    public static List<Object> formatOperands(List<Filter> filters) {
        List<Object> operands = new ArrayList<>();
        for (Filter filter : filters) {
            final Object operand = filter.getOperand();
            switch (filter.getOperator()) {
                case OPERATOR_CONTAINS -> operands.add("%" + operand + "%");
                case OPERATOR_STARTS_WITH -> operands.add(operand + "%");
                case OPERATOR_ENDS_WITH -> operands.add("%" + operand);
                case OPERATOR_ORDER_BY, OPERATOR_ORDER_BY_DESC -> { } //the order_by operators are ignored
                default -> operands.add(castOperand(filter));
            }
        }
        return operands;
    }

    private static Object castOperand(Filter filter) {
        switch (filter.getType()) {
            case FIELD_TYPE_NUMBER, FIELD_TYPE_PAGINATION -> {
                try {
                    return parseInt(filter.getOperand());
                } catch (NumberFormatException exception) {
                    throw new ExceptionInternalError("Filters not validated before casting operands, call validateAndOrderFilters() before calling formatOperands()", exception);
                }
            }
            case FIELD_TYPE_BOOLEAN -> {
                if (filter.isCustom()) {
                    //Do not cast custom boolean filters they are stored in the database as strings not booleans
                    return filter.getOperand();
                }
                if (Objects.equals(filter.getOperand(), "true") || Objects.equals(filter.getOperand(), "false")) {
                    return Boolean.parseBoolean(filter.getOperand());
                } else {
                    throw new ExceptionInternalError("Filters not validated before casting operands, call validateAndOrderFilters() before calling formatOperands()");
                }
            }
            default -> {
                return filter.getOperand();
            }
        }
    }

}
