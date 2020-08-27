package com.accela.pianoforte.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Credentials {
    private final String apiAccessId;
    private final String method;
    private final String amount;
    private final String versionNumber;
    private final String locationId;
    private final String utcTime;
    private final String orderNumber;
    private final String signature;
}
