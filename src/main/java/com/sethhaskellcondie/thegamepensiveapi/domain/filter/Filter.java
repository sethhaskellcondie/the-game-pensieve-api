package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInvalidFilter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Filters are completely decoupled from the Entities they work with. We could pull in the database fields or
 * the fields on the entity class but instead the field must be manually entered in the fields map as it would
 * work in a SQL query to be used on that table. Perhaps in the future they will couple together and the basic
 * filters will work automagically but as for now setting up and using filters must be done manually.
 * <p>
 * Another direction that I could go is trying to find a library that already does this. But in this project I
 * want to build it myself.
 */
public class Filter {
    public static final String FIELD_TYPE_STRING = "string";
    public static final String FIELD_TYPE_NUMBER = "number";
    public static final String FIELD_TYPE_BOOLEAN = "boolean";
    public static final String FIELD_TYPE_TIME = "time";
    public static final String FIELD_TYPE_ENUM = "enum";
    public static final String FIELD_TYPE_PAGINATION = "pagination";

    public static final String FILTER_OPERATOR_EQUALS = "equals";
    public static final String FILTER_OPERATOR_NOT_EQUALS = "not_equals";
    public static final String FILTER_OPERATOR_CONTAINS = "contains";
    public static final String FILTER_OPERATOR_STARTS_WITH = "starts_with";
    public static final String FILTER_OPERATOR_ENDS_WITH = "ends_with";
    public static final String FILTER_OPERATOR_GREATER_THAN = "greater_than";
    public static final String FILTER_OPERATOR_LESS_THAN = "less_than";
    public static final String FILTER_OPERATOR_GREATER_THAN_EQUAL_TO = "greater_than_equal_to";
    public static final String FILTER_OPERATOR_LESS_THAN_EQUAL_TO = "less_than_equal_to";
    public static final String FILTER_OPERATOR_SINCE = "since";
    public static final String FILTER_OPERATOR_BEFORE = "before";
    public static final String FILTER_OPERATOR_ORDER_BY = "order_by";
    public static final String FILTER_OPERATOR_LIMIT = "limit";
    public static final String FILTER_OPERATOR_OFFSET = "offset";

    public static final String RESOURCE_SYSTEM = "system";
    public static final String RESOURCE_TOY = "toy";

    private final String resource;
    private final String field;
    private final String operator;
    private final String operand;

    public Filter(String resource, String field, String operator, String operand) {
        this.resource = resource;
        this.field = field;
        this.operator = operator;
        this.operand = operand;
    }

    public String getResource() {
        return resource;
    }

    public String getField() {
        return field;
    }

    public String getOperator() {
        return operator;
    }

    public String getOperand() {
        return operand;
    }

    /**
     * These are the characters and words that are not allowed in any filter strings,
     * this is a protection against SQL injection in the system.
     */
    private static List<String> getBlacklistedWords() {
        return List.of(
                ";",          // ; allows a statement to be terminated, then start a new one
                "=",          // = allows boolean based injection
                "or",         // 'or' allows boolean based injection
                " union ",    // 'union' allows union based injection
                " sleep ",    // 'sleep' allows time based injection
                " delete ",   // 'delete' is not allowed
                " select "    // 'select' is not allowed
                );
    }

    public static Map<String, String> getFieldsForResource(String resource) {
        //Using a LinkedHashMap to preserve the order of the elements as they are added to the Map.
        Map<String, String> fields = new LinkedHashMap<>();
        switch (resource) {
            case RESOURCE_SYSTEM -> {
                fields.put("name", FIELD_TYPE_STRING);
                fields.put("generation", FIELD_TYPE_NUMBER);
                fields.put("handheld", FIELD_TYPE_BOOLEAN);
            }
            case RESOURCE_TOY -> {
                fields.put("name", FIELD_TYPE_STRING);
                fields.put("set", FIELD_TYPE_STRING);
            }
            default -> {
                return new LinkedHashMap<>();
            }
        }
        fields.put("created", FIELD_TYPE_TIME);
        fields.put("updated", FIELD_TYPE_TIME);
        fields.put("pagination", FIELD_TYPE_PAGINATION);
        return fields;
    }

    public static List<String> getFilterOperators(String fieldType) {
        List<String> filters = new ArrayList<>();
        switch (fieldType) {
            case FIELD_TYPE_STRING -> {
                filters.add(FILTER_OPERATOR_EQUALS);
                filters.add(FILTER_OPERATOR_NOT_EQUALS);
                filters.add(FILTER_OPERATOR_CONTAINS);
                filters.add(FILTER_OPERATOR_STARTS_WITH);
                filters.add(FILTER_OPERATOR_ENDS_WITH);
            }
            case FIELD_TYPE_NUMBER -> {
                filters.add(FILTER_OPERATOR_EQUALS);
                filters.add(FILTER_OPERATOR_NOT_EQUALS);
                filters.add(FILTER_OPERATOR_GREATER_THAN);
                filters.add(FILTER_OPERATOR_GREATER_THAN_EQUAL_TO);
                filters.add(FILTER_OPERATOR_LESS_THAN);
                filters.add(FILTER_OPERATOR_LESS_THAN_EQUAL_TO);
            }
            case FIELD_TYPE_BOOLEAN -> {
                filters.add(FILTER_OPERATOR_EQUALS);
            }
            case FIELD_TYPE_TIME -> {
                filters.add(FILTER_OPERATOR_SINCE);
                filters.add(FILTER_OPERATOR_BEFORE);
            }
            case FIELD_TYPE_ENUM -> {
                filters.add(FILTER_OPERATOR_EQUALS);
                filters.add(FILTER_OPERATOR_NOT_EQUALS);
            }
            case FIELD_TYPE_PAGINATION -> {
                filters.add(FILTER_OPERATOR_ORDER_BY);
                filters.add(FILTER_OPERATOR_LIMIT);
                filters.add(FILTER_OPERATOR_OFFSET);
            }
            default -> {
                return new ArrayList<>();
            }
        }
        return filters;
    }

    public static Map<String, Object> convertFiltersToSql(List<Filter> filters) {
        if (filters.isEmpty()) {
            return new LinkedHashMap<>();
        }

        ExceptionInvalidFilter exceptionInvalidFilter = new ExceptionInvalidFilter();
        for (Filter filter : filters) {
            validateFilter(exceptionInvalidFilter, filter);
        }

        if (exceptionInvalidFilter.exceptionsFound()) {
            throw exceptionInvalidFilter;
        }

        Map<String, Object> whereClauses = new LinkedHashMap<>();

        for (Filter filter : filters) {
            whereClauses.put(filterToWhereSql(filter), filter.getOperand());

        }
        return whereClauses;
    }

    private static void validateFilter(ExceptionInvalidFilter exception, Filter filter) {
        Map<String, String> fields = getFieldsForResource(filter.getResource());
        if (!fields.containsKey(filter.getField())) {
            exception.addException(filter.getField() + " is not allowed for " + filter.getResource() + ".");
        }
        String fieldType = fields.get(filter.getField());
        List<String> operators = getFilterOperators(fieldType);
        if (!operators.contains(filter.getOperator())) {
            exception.addException(filter.getField() + " is not allowed with operator " + filter.getOperator());
        }
        for (String blacklistedWord : getBlacklistedWords()) {
            if (filter.getOperand().contains(blacklistedWord)) {
                exception.addException(blacklistedWord + " is not allowed in filters");
            }
        }
    }

    private static String filterToWhereSql(Filter filter) {
        String where = "";

        switch (filter.getOperator()) {
            case FILTER_OPERATOR_EQUALS -> {
                return " AND " + filter.getField() + " = ? ";
            }
            case FILTER_OPERATOR_NOT_EQUALS -> {
                return " AND " + filter.getField() + " <> ? ";
            }
            case FILTER_OPERATOR_CONTAINS -> {
                return " AND " + filter.getField() + "  %?% ";
            }
            case FILTER_OPERATOR_STARTS_WITH -> {
                return " AND " + filter.getField() + " ?% ";
            }
            case FILTER_OPERATOR_ENDS_WITH -> {
                return " AND " + filter.getField() + " %? ";
            }
            case FILTER_OPERATOR_GREATER_THAN -> {
                return " AND " + filter.getField() + " > ? ";
            }
            case FILTER_OPERATOR_LESS_THAN -> {
                return " AND " + filter.getField() + " < ? ";
            }
            case FILTER_OPERATOR_GREATER_THAN_EQUAL_TO -> {
                return " AND " + filter.getField() + " >= ? ";
            }
            case FILTER_OPERATOR_LESS_THAN_EQUAL_TO -> {
                return " AND " + filter.getField() + " <= ? ";
            }
            case FILTER_OPERATOR_SINCE -> {
                return " AND " + filter.getField() + " > ?  ";
            }
            case FILTER_OPERATOR_BEFORE -> {
                return " AND " + filter.getField() + " < ?  ";
            }
        }
        return where;
    }
}

record FilterResponseDto(String type, Map<String, String> fields, Map<String, List<String>> filters) { }
