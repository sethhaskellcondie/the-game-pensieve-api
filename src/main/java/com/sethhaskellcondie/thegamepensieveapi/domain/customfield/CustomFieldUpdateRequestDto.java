package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import java.util.List;

public record CustomFieldUpdateRequestDto(String name, int order, List<CustomFieldOptionDto> options) {
}
