package com.accela.pianoforte.common;

import io.vavr.control.Try;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;

public class HMacMD5 {
	private static final String ALGORITHM = "HmacMD5";

	public static String getHmacMD5(final String data, final String keyString) {
		return Try.of(() -> {
			final Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(keyString.getBytes(StandardCharsets.UTF_8), ALGORITHM));
			return DatatypeConverter.printHexBinary(
					mac.doFinal(data.getBytes(StandardCharsets.US_ASCII))).toLowerCase();
		}).get();
	}
}
