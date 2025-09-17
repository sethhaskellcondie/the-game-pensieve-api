package com.sethhaskellcondie.thegamepensieveapi.domain.filter;

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
    public static final String FIELD_TYPE_SYSTEM = "system";
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
}

