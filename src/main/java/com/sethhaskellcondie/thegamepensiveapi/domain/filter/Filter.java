package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInternalError;

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

    private final String entityType;
    private final String field;
    private final String operator;
    private final String operand;

    public Filter(String entityType, String field, String operator, String operand) {
        this.entityType = entityType;
        this.field = field;
        this.operator = operator;
        this.operand = operand;
    }

    public String getEntityType() {
        return entityType;
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

    public static List<String> getResourcesThatHaveFilters() {
        return List.of(
                RESOURCE_SYSTEM,
                RESOURCE_TOY
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
                throw new ExceptionInternalError("getFieldsForResource() called with an unknown resource");
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
                throw new ExceptionInternalError("getFilterList() called with an unknown fieldType");
            }
        }
        return filters;
    }

    public static  List<String> convertFiltersToSql(List<Filter> filters) {
        if (filters.isEmpty()) {
            return List.of("");
        }
        //validate that this filter works for this entity
        //convert this filter into clauses that can be added to the end of a SQL query
        //make sure that the filters are not venerable to SQL injection
    }
}

record FilterResponseDto(String type, Map<String, String> fields, Map<String, List<String>> filters) { }
