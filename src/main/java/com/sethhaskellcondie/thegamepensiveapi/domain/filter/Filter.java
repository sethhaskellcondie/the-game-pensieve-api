package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInternalError;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInvalidFilter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.lang.Integer.parseInt;

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
    public static final String ALL_FIELDS = "all_fields";
    public static final String PAGINATION_FIELDS = "pagination_fields";

    public static final String FIELD_TYPE_STRING = "string";
    public static final String FIELD_TYPE_NUMBER = "number";
    public static final String FIELD_TYPE_BOOLEAN = "boolean";
    public static final String FIELD_TYPE_TIME = "time";
    public static final String FIELD_TYPE_SORT = "sort";
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
    public static final String FILTER_OPERATOR_ORDER_BY_DESC = "order_by_desc";
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
        fields.put("created_at", FIELD_TYPE_TIME);
        fields.put("updated_at", FIELD_TYPE_TIME);
        fields.put(ALL_FIELDS, FIELD_TYPE_SORT);
        fields.put(PAGINATION_FIELDS, FIELD_TYPE_PAGINATION);
        return fields;
    }

    public static List<String> getFilterOperators(String fieldType, boolean includeSort) {
        List<String> filters = new ArrayList<>();
        if (null == fieldType) {
            return filters;
        }
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
            case FIELD_TYPE_SORT -> {
                //don't include the sort fields twice
                if (!includeSort) {
                    filters.add(FILTER_OPERATOR_ORDER_BY);
                    filters.add(FILTER_OPERATOR_ORDER_BY_DESC);
                }
            }
            case FIELD_TYPE_PAGINATION -> {
                filters.add(FILTER_OPERATOR_LIMIT);
                filters.add(FILTER_OPERATOR_OFFSET);
            }
            default -> {
                return new ArrayList<>();
            }
        }
        if (includeSort) {
            filters.add(FILTER_OPERATOR_ORDER_BY);
            filters.add(FILTER_OPERATOR_ORDER_BY_DESC);
        }
        return filters;
    }

    public static List<Filter> validateAndOrderFilters(List<Filter> filters) {
        ExceptionInvalidFilter exceptionInvalidFilter = new ExceptionInvalidFilter();

        List<Filter> whereFilters = new ArrayList<>();
        List<Filter> orderByFilter = new ArrayList<>();
        List<Filter> limitFilter = new ArrayList<>();
        List<Filter> offsetFilter = new ArrayList<>();

        for (Filter filter : filters) {
            Map<String, String> fields = getFieldsForResource(filter.getResource());
            if (!fields.containsKey(filter.getField())) {
                exceptionInvalidFilter.addException(filter.getField() + " is not allowed for " + filter.getResource() + ".");
            }
            String fieldType = fields.get(filter.getField());
            List<String> operators = getFilterOperators(fieldType, true);
            if (!operators.contains(filter.getOperator())) {
                exceptionInvalidFilter.addException(filter.getField() + " is not allowed with operator " + filter.getOperator());
            }
            for (String blacklistedWord : getBlacklistedWords()) {
                if (filter.getOperand().contains(blacklistedWord)) {
                    exceptionInvalidFilter.addException(blacklistedWord + " is not allowed in filters");
                }
            }

            //TODO refactor this section to not use triple if statements
            if (Objects.equals(fields.get(filter.field), FIELD_TYPE_NUMBER) || Objects.equals(fields.get(filter.field), FIELD_TYPE_PAGINATION)) {
                if (!Objects.equals(filter.getOperator(), FILTER_OPERATOR_ORDER_BY) && !Objects.equals(filter.getOperator(), FILTER_OPERATOR_ORDER_BY_DESC)) {
                    try {
                        parseInt(filter.operand);
                    } catch (NumberFormatException exception) {
                        exceptionInvalidFilter.addException("Number and Pagination must include whole numbers as operands");
                    }
                }
            }

            if (Objects.equals(fields.get(filter.field), FIELD_TYPE_BOOLEAN)) {
                if (!Objects.equals(filter.getOperator(), FILTER_OPERATOR_ORDER_BY) && !Objects.equals(filter.getOperator(), FILTER_OPERATOR_ORDER_BY_DESC)) {
                    if (!Objects.equals(filter.operand, "true") && !Objects.equals(filter.operand, "false")) {
                        exceptionInvalidFilter.addException("operands for Boolean type filters must equal exactly 'true' or 'false'");
                    }
                }
            }

            //TODO test the time filters to make sure they are formatted correctly

            switch (filter.getOperator()) {
                case FILTER_OPERATOR_ORDER_BY, FILTER_OPERATOR_ORDER_BY_DESC -> {
                    orderByFilter.add(filter);
                }
                case FILTER_OPERATOR_LIMIT -> {
                    limitFilter.add(filter);
                }
                case FILTER_OPERATOR_OFFSET -> {
                    offsetFilter.add(filter);
                }
                default -> {
                    whereFilters.add(filter);
                }
            }
        }
        if (orderByFilter.size() > 1) {
            exceptionInvalidFilter.addException("No more than one " + FILTER_OPERATOR_ORDER_BY + " allowed in a single request");
        }
        if (limitFilter.size() > 1) {
            exceptionInvalidFilter.addException("No more than one " + FILTER_OPERATOR_LIMIT + " allowed in a single request");
        }
        if (offsetFilter.size() > 1) {
            exceptionInvalidFilter.addException("No more than one " + FILTER_OPERATOR_OFFSET + " allowed in a single request");
        }
        if (limitFilter.size() < 1 && offsetFilter.size() > 0) {
            exceptionInvalidFilter.addException(FILTER_OPERATOR_OFFSET + " filter is not allowed without also including one " + FILTER_OPERATOR_LIMIT + " filter");
        }
        if (exceptionInvalidFilter.exceptionsFound()) {
            throw exceptionInvalidFilter;
        }

        if (orderByFilter.size() == 1) {
            whereFilters.add(orderByFilter.get(0));
        }
        if (limitFilter.size() == 1) {
            whereFilters.add(limitFilter.get(0));
        }
        if (offsetFilter.size() == 1) {
            whereFilters.add(offsetFilter.get(0));
        }
        return whereFilters;
    }

    public static List<String> formatWhereStatements(List<Filter> filters) {
        List<String> whereStatements = new ArrayList<>();
        for (Filter filter : filters) {
            switch (filter.getOperator()) {
                case FILTER_OPERATOR_EQUALS -> {
                    whereStatements.add(" AND " + filter.getField() + " = ?");
                }
                case FILTER_OPERATOR_NOT_EQUALS -> {
                    whereStatements.add(" AND " + filter.getField() + " <> ?");
                }
                case FILTER_OPERATOR_CONTAINS,
                        FILTER_OPERATOR_STARTS_WITH,
                        FILTER_OPERATOR_ENDS_WITH -> {
                    whereStatements.add(" AND " + filter.getField() + " LIKE ?");
                }
                case FILTER_OPERATOR_GREATER_THAN -> {
                    whereStatements.add(" AND " + filter.getField() + " > ?");
                }
                case FILTER_OPERATOR_LESS_THAN -> {
                    whereStatements.add(" AND " + filter.getField() + " < ?");
                }
                case FILTER_OPERATOR_GREATER_THAN_EQUAL_TO -> {
                    whereStatements.add(" AND " + filter.getField() + " >= ?");
                }
                case FILTER_OPERATOR_LESS_THAN_EQUAL_TO -> {
                    whereStatements.add(" AND " + filter.getField() + " <= ?");
                }
                case FILTER_OPERATOR_ORDER_BY -> {
                    whereStatements.add(" ORDER BY " + filter.getField() + " ASC");
                }
                case FILTER_OPERATOR_ORDER_BY_DESC -> {
                    whereStatements.add(" ORDER BY " + filter.getField() + " DESC");
                }
                case FILTER_OPERATOR_SINCE -> {
                    whereStatements.add(" AND " + filter.getField() + " > TO_TIMESTAMP( ? , 'YYYY-MM-DD')");
                }
                case FILTER_OPERATOR_BEFORE -> {
                    whereStatements.add(" AND " + filter.getField() + " < TO_TIMESTAMP( ? , 'YYYY-MM-DD')");
                }
                case FILTER_OPERATOR_LIMIT -> {
                    whereStatements.add(" LIMIT ?");
                }
                case FILTER_OPERATOR_OFFSET -> {
                    whereStatements.add(" OFFSET ?");
                }
            }
        }
        return whereStatements;
    }

    public static List<Object> formatOperands(List<Filter> filters) {
        List<Object> operands = new ArrayList<>();
        for (Filter filter : filters) {
            final Object operand = filter.getOperand();
            switch (filter.getOperator()) {
                case FILTER_OPERATOR_EQUALS,
                        FILTER_OPERATOR_NOT_EQUALS,
                        FILTER_OPERATOR_GREATER_THAN,
                        FILTER_OPERATOR_LESS_THAN,
                        FILTER_OPERATOR_GREATER_THAN_EQUAL_TO,
                        FILTER_OPERATOR_LESS_THAN_EQUAL_TO,
                        FILTER_OPERATOR_SINCE,
                        FILTER_OPERATOR_BEFORE,
                        FILTER_OPERATOR_LIMIT,
                        FILTER_OPERATOR_OFFSET -> {
                    operands.add(castOperand(filter));
                }
                case FILTER_OPERATOR_CONTAINS -> {
                    operands.add("%" + operand + "%");
                }
                case FILTER_OPERATOR_STARTS_WITH -> {
                    operands.add(operand + "%");
                }
                case FILTER_OPERATOR_ENDS_WITH -> {
                    operands.add("%" + operand);
                }
                case FILTER_OPERATOR_ORDER_BY,
                        FILTER_OPERATOR_ORDER_BY_DESC -> {
                    continue;
                }
            }
        }
        return operands;
    }

    private static Object castOperand(Filter filter) {
        Map<String, String> fields = getFieldsForResource(filter.resource);

        switch (fields.get(filter.field)) {
            case FIELD_TYPE_NUMBER,
                    FIELD_TYPE_PAGINATION -> {
                try {
                    return parseInt(filter.operand);
                } catch (NumberFormatException exception) {
                    throw new ExceptionInternalError("Filters not validated before casting operands, call validateAndOrderFilters() before calling formatOperands()");
                }
            }
            case FIELD_TYPE_BOOLEAN -> {
                if (Objects.equals(filter.operand, "true") || Objects.equals(filter.operand, "false")) {
                    return Boolean.parseBoolean(filter.operand);
                } else {
                    throw new ExceptionInternalError("Filters not validated before casting operands, call validateAndOrderFilters() before calling formatOperands()");
                }
            }
            default -> {
                return filter.operand;
            }
        }
    }
}

record FilterResponseDto(String type, Map<String, String> fields, Map<String, List<String>> filters) { }
