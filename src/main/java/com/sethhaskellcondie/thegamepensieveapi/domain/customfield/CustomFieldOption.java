package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

public record CustomFieldOption(int id, int customFieldId, String name, boolean isDefault, int order) {
}
