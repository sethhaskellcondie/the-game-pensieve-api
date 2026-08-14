package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;

import java.util.List;
import java.util.regex.Pattern;

//Custom Fields are NOT entities, they don't follow the entity pattern
//Custom Field is a pseudo-DTO this is why it is allowed to be public and used in the api layer
public record CustomField(int id, String name, String type, String entityKey, int order, List<CustomFieldOption> options) {

    //Custom Field Types MUST always also be a Filter Field Type, otherwise custom fields wouldn't work as filters
    public static final String TYPE_TEXT         = Filter.FIELD_TYPE_TEXT;
    public static final String TYPE_NUMBER       = Filter.FIELD_TYPE_NUMBER;
    public static final String TYPE_BOOLEAN      = Filter.FIELD_TYPE_BOOLEAN;
    public static final String TYPE_DROPDOWN     = Filter.FIELD_TYPE_DROPDOWN;
    public static final String TYPE_RADIO_BUTTON = Filter.FIELD_TYPE_RADIO_BUTTON;
    public static final String TYPE_PROGRESS_BAR = Filter.FIELD_TYPE_PROGRESS_BAR; // new!

    public static CustomField withoutOptions(int id, String name, String type, String entityKey) {
        return new CustomField(id, name, type, entityKey, 0, List.of());
    }

    public static CustomField withoutOptions(int id, String name, String type, String entityKey, int order) {
        return new CustomField(id, name, type, entityKey, order, List.of());
    }

    public static List<String> getAllCustomFieldTypes() {
        return List.of(TYPE_TEXT, TYPE_NUMBER, TYPE_BOOLEAN,
                TYPE_DROPDOWN, TYPE_RADIO_BUTTON, TYPE_PROGRESS_BAR);
    }

    public static List<String> getEnumCustomFieldTypes() {
        return List.of(TYPE_DROPDOWN, TYPE_RADIO_BUTTON, TYPE_PROGRESS_BAR);
    }

    public static boolean isEnumType(String type) {
        return getEnumCustomFieldTypes().contains(type);
    }

    public static final int NAME_MAX_LENGTH = 100;

    //A custom field's name is chosen by the user and then travels into the filter query, where it selects which
    //custom field is being filtered on. FilterService binds it as a parameter, so this allowlist is defense in
    //depth behind that binding, not the primary control: it keeps quotes, semicolons and backslashes out of the
    //column entirely, so a stored name can never become the seed of a second-order injection if some future query
    //path forgets to bind it. Letters and digits are Unicode-aware so non-English names still work.
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} _\\-.,()/&+#:?!]*$");

    public static boolean isValidName(String name) {
        return name != null && name.length() <= NAME_MAX_LENGTH && NAME_PATTERN.matcher(name).matches();
    }

    public static String nameRequirements() {
        return "Custom Field names must start with a letter or digit, be no longer than " + NAME_MAX_LENGTH
                + " characters, and contain only letters, digits, spaces and the characters _ - . , ( ) / & + # : ? !";
    }
}
