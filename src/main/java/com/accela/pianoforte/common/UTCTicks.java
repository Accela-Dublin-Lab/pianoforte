package com.accela.pianoforte.common;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

public class UTCTicks {

	public static Long getUtcTime(final OffsetDateTime dateTime) {
		return Duration.between(Instant.parse("0001-01-01T00:00:00.00Z"),
				dateTime.toInstant()).toMillis() * 10000;
	}

}
