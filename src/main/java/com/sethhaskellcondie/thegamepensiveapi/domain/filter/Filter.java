package com.sethhaskellcondie.thegamepensiveapi.domain.filter;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInternalError;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInvalidFilter;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.lang.Integer.parseInt;

/**
 * Filters are completely decoupled from the Entities they work with. We could pull in the database fields or
 * the fields on the entity class but instead the field must be manually entered in the fields map as it would
 * work in a SQL query to be used on that table. Perhaps in the future they will be more tightly coupled and the basic
 * filters will work automagically but as for now setting up and using filters must be done manually.
 * <p>
 * Another direction that I could go is trying to find a library that already does this. But in this project I
 * want to build it myself.
 */
public class Filter {
    public static final String ALL_FIELDS = "all_fields";
    public static final String PAGINATION_FIELDS = "pagination_fields";

    public static final String FIELD_TYPE_TEXT = "text";
    public static final String FIELD_TYPE_NUMBER = "number";
    public static final String FIELD_TYPE_BOOLEAN = "boolean";
    public static final String FIELD_TYPE_TIME = "time";
    public static final String FIELD_TYPE_SORT = "sort";
    public static final String FIELD_TYPE_PAGINATION = "pagination";

    public static final String OPERATOR_EQUALS = "equals";
    public static final String OPERATOR_NOT_EQUALS = "not_equals";
    public static final String OPERATOR_CONTAINS = "contains";
    public static final String OPERATOR_STARTS_WITH = "starts_with";
    public static final String OPERATOR_ENDS_WITH = "ends_with";
    public static final String OPERATOR_GREATER_THAN = "greater_than";
    public static final String OPERATOR_LESS_THAN = "less_than";
    public static final String OPERATOR_GREATER_THAN_EQUAL_TO = "greater_than_equal_to";
    public static final String OPERATOR_LESS_THAN_EQUAL_TO = "less_than_equal_to";
    public static final String OPERATOR_SINCE = "since";
    public static final String OPERATOR_BEFORE = "before";
    public static final String OPERATOR_ORDER_BY = "order_by";
    public static final String OPERATOR_ORDER_BY_DESC = "order_by_desc";
    public static final String OPERATOR_LIMIT = "limit";
    public static final String OPERATOR_OFFSET = "offset";

    private final String key;
    private final String type;
    private final String field;
    private final String operator;
    private final String operand;
    private final boolean custom;

    //TODO add type
    public Filter(String key, String type, String field, String operator, String operand, boolean isCustom) {
        this.key = key;
        this.type = type;
        this.field = field;
        this.operator = operator;
        this.operand = operand;
        this.custom = isCustom;
    }

    public String getKey() {
        return key;
    }

    public String getType() {
        return type;
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

    public boolean isCustom() {
        return custom;
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
     *     if includeSort is 'false' then it will only be included in the 'all_fields' list
     *     generally includeSort is set to false when we are returning the filters in a response
     *     and includeSort is set to true when performing internal validation on incoming requests
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

    //TODO fix checkstyle error
    public static List<Filter> validateAndOrderFilters(List<Filter> filters) {
        ExceptionInvalidFilter exceptionInvalidFilter = new ExceptionInvalidFilter();

        for (Filter filter : filters) {
            Map<String, String> fields = FilterEntity.getFilterFieldsByKey(filter.getKey());
            if (!filter.isCustom()) {
                if (!fields.containsKey(filter.getField())) {
                    exceptionInvalidFilter.addException(filter.getField() + " is not allowed for " + filter.getKey() + ".");
                }
                String fieldType = fields.get(filter.getField());
                List<String> operators = getFilterOperators(fieldType, true);
                if (!Objects.equals(filter.getType(), fieldType)) {
                    exceptionInvalidFilter.addException("Internal Error: Malformed Filter the filter field type '" + filter.getType()
                            + "' did not match the computed field type '" + fieldType + "'");
                }
                if (!operators.contains(filter.getOperator())) {
                    exceptionInvalidFilter.addException(filter.getField() + " is not allowed with operator " + filter.getOperator() + ".");
                }
            }
            for (String blacklistedWord : getBlacklistedWords()) {
                if (filter.getOperand().contains(blacklistedWord)) {
                    exceptionInvalidFilter.addException(blacklistedWord + " is not allowed in filters.");
                }
            }

            if (Objects.equals(fields.get(filter.field), FIELD_TYPE_NUMBER) || Objects.equals(fields.get(filter.field), FIELD_TYPE_PAGINATION)) {
                exceptionInvalidFilter = additionalNumberAndPaginationFilterValidation(filter, exceptionInvalidFilter);
            }

            if (Objects.equals(fields.get(filter.field), FIELD_TYPE_BOOLEAN)) {
                exceptionInvalidFilter = additionalBooleanFilterValidation(filter, exceptionInvalidFilter);
            }

            if (Objects.equals(fields.get(filter.field), FIELD_TYPE_TIME)) {
                exceptionInvalidFilter = additionalTimeValidation(filter, exceptionInvalidFilter);
            }
        }

        return orderFilters(filters, exceptionInvalidFilter);
    }

    private static ExceptionInvalidFilter additionalNumberAndPaginationFilterValidation(Filter filter, ExceptionInvalidFilter exceptionInvalidFilter) {
        if (Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY) || Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY_DESC)) {
            return exceptionInvalidFilter;
        }
        try {
            parseInt(filter.operand);
        } catch (NumberFormatException exception) {
            exceptionInvalidFilter.addException("Number and pagination filters must include whole numbers as operands.");
        }
        return exceptionInvalidFilter;
    }

    private static ExceptionInvalidFilter additionalBooleanFilterValidation(Filter filter, ExceptionInvalidFilter exceptionInvalidFilter) {
        if (Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY) || Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY_DESC)) {
            return exceptionInvalidFilter;
        }
        if (!Objects.equals(filter.operand, "true") && !Objects.equals(filter.operand, "false")) {
            exceptionInvalidFilter.addException("Boolean type filters must have their operands match exactly 'true' or 'false'.");
        }
        return exceptionInvalidFilter;
    }

    private static ExceptionInvalidFilter additionalTimeValidation(Filter filter, ExceptionInvalidFilter exceptionInvalidFilter) {
        if (Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY) || Objects.equals(filter.getOperator(), OPERATOR_ORDER_BY_DESC)) {
            return exceptionInvalidFilter;
        }
        try {
            Timestamp.valueOf(filter.operand);
        } catch (IllegalArgumentException exception) {
            exceptionInvalidFilter.addException("Operands for Time filters must be able to be formatted to yyyy-mm-dd hh:mm:ss format. Example: 2024-05-17 00:00:00");
        }
        return exceptionInvalidFilter;
    }

    // This was originally one function with validateAndOrderFilters() but it was very long and hard to read, it was split up to improve readability
    private static List<Filter> orderFilters(List<Filter> filters, ExceptionInvalidFilter exceptionInvalidFilter) {
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
            exceptionInvalidFilter.addException("'Offset' filter is not allowed without also including one 'limit' filter");
        }

        if (exceptionInvalidFilter.exceptionsFound()) {
            throw exceptionInvalidFilter;
        }

        whereFilters.add(orderByFilter);
        whereFilters.add(limitFilter);
        whereFilters.add(offsetFilter);
        // the NPathComplexity is over 200 if I put null checks on the add calls this causes the checkstyle to fail
        // instead all the filters include the null values will be added and the null values will be removed
        return whereFilters.stream().filter(Objects::nonNull).toList();
    }

    //TODO fix checkstyle error
    public static List<String> formatWhereStatements(List<Filter> filters) {
        List<String> whereStatements = new ArrayList<>();
        for (Filter filter : filters) {
            if (filter.isCustom()) {
                //TODO update this to work with the operator
                switch (filter.getType()) {
                    case CustomField.TYPE_TEXT, CustomField.TYPE_BOOLEAN -> {
                        whereStatements.add(" AND fields.name = '" + filter.getField() + "'");
                        whereStatements.add(" AND values.value.text = ?");
                    }
                    case CustomField.TYPE_NUMBER -> {
                        whereStatements.add(" AND fields.name = '" + filter.getField() + "'");
                        whereStatements.add(" AND values.value_number = ?");
                    }
                }
            } else {
                String tableAlias = Keychain.getTableAliasByKey(filters.get(0).getKey());
                switch (filter.getOperator()) {
                    case OPERATOR_EQUALS -> {
                        whereStatements.add(" AND " + tableAlias + "." + filter.getField() + " = ?");
                    }
                    case OPERATOR_NOT_EQUALS -> {
                        whereStatements.add(" AND " + tableAlias + "." + filter.getField() + " <> ?");
                    }
                    case OPERATOR_CONTAINS,
                            OPERATOR_STARTS_WITH,
                            OPERATOR_ENDS_WITH -> {
                        whereStatements.add(" AND " + tableAlias + "." + filter.getField() + " LIKE ?");
                    }
                    case OPERATOR_GREATER_THAN -> {
                        whereStatements.add(" AND " + tableAlias + "." + filter.getField() + " > ?");
                    }
                    case OPERATOR_LESS_THAN -> {
                        whereStatements.add(" AND " + tableAlias + "." + filter.getField() + " < ?");
                    }
                    case OPERATOR_GREATER_THAN_EQUAL_TO -> {
                        whereStatements.add(" AND " + tableAlias + "." + filter.getField() + " >= ?");
                    }
                    case OPERATOR_LESS_THAN_EQUAL_TO -> {
                        whereStatements.add(" AND " + tableAlias + "." + filter.getField() + " <= ?");
                    }
                    case OPERATOR_ORDER_BY -> {
                        whereStatements.add(" ORDER BY " + tableAlias + "." + filter.getField() + " ASC");
                    }
                    case OPERATOR_ORDER_BY_DESC -> {
                        whereStatements.add(" ORDER BY " + tableAlias + "." + filter.getField() + " DESC");
                    }
                    case OPERATOR_SINCE -> {
                        whereStatements.add(" AND " + tableAlias + "." + filter.getField() + " >= TO_TIMESTAMP( ? , 'yyyy-mm-dd hh:mm:ss')");
                    }
                    case OPERATOR_BEFORE -> {
                        whereStatements.add(" AND " + tableAlias + "." + filter.getField() + " <= TO_TIMESTAMP( ? , 'yyyy-mm-dd hh:mm:ss')");
                    }
                    case OPERATOR_LIMIT -> {
                        whereStatements.add(" LIMIT ?");
                    }
                    case OPERATOR_OFFSET -> {
                        whereStatements.add(" OFFSET ?");
                    }
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
                case OPERATOR_CONTAINS -> {
                    operands.add("%" + operand + "%");
                }
                case OPERATOR_STARTS_WITH -> {
                    operands.add(operand + "%");
                }
                case OPERATOR_ENDS_WITH -> {
                    operands.add("%" + operand);
                }
                case OPERATOR_ORDER_BY, OPERATOR_ORDER_BY_DESC -> {
                    //the order_by operators are ignored
                    continue;
                }
                default -> {
                    operands.add(castOperand(filter));
                }
            }
        }
        return operands;
    }

    private static Object castOperand(Filter filter) {
        switch (filter.getType()) {
            case FIELD_TYPE_NUMBER, FIELD_TYPE_PAGINATION -> {
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
