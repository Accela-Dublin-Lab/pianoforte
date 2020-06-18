import com.accela.pianoforte.common.HMacMD5;
import com.accela.pianoforte.common.UTCTicks;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HashTest {
    final String version = "2.0";
    final String method = "sale";
    final String txOrderNb = "A1234";
    final String amount = "10.00";
    final String timestamp = "636397036957980000";
    final String customerToken = "";
    final String paymentToken = "";

    @Test
    @DisplayName("hash code is correctly generated")
    public void hashGenerationTest() {
        final String secureKey = "eedce6b47748968641a6af8bcd4756fe";
        final String accessId = "8dcd03dc50d5aeed2f221e7e88ee4d23";

        final String hashData = String.join("|", ImmutableList.<String>builder()
                .add(accessId, method, version, amount, timestamp, txOrderNb, customerToken, paymentToken)
                .build());
        System.out.println(hashData);
        final String signature = HMacMD5.getHmacMD5(hashData, secureKey);
        System.out.println(signature);

        assertEquals("8dcd03dc50d5aeed2f221e7e88ee4d23|sale|2.0|10.00|636397036957980000|A1234||", hashData);
        assertEquals("44575464e3b99f8638858ac627eb9f03", signature);
    }

    @Test
    @DisplayName("generate hash code for tests")
    public void generateTestHash() {
        final String accessId = "570930c741e4998f8679efa361e0c309";
        final String secureKey = "d8837aaec70deccd44098fe7fa375170";
        final String txOrderNb = "A1234";
        final String amount = "10.00";
        final String timestamp = UTCTicks.getUtcTime(OffsetDateTime.now()).toString();
        final String hashData = String.join("|", ImmutableList.<String>builder()
                .add(accessId, method, version, amount, timestamp, txOrderNb, customerToken, paymentToken)
                .build());
        System.out.println("timestamp: "+timestamp);
        System.out.println(hashData);
        final String signature = HMacMD5.getHmacMD5(hashData, secureKey);
        System.out.println(signature);
    }
}
