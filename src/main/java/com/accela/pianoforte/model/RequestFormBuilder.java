package com.accela.pianoforte.model;

import com.accela.pianoforte.common.HMacMD5;
import com.accela.pianoforte.common.UTCTicks;
import com.accela.pianoforte.main.AppConfig;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Try;

import java.net.URI;
import java.net.URLEncoder;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class RequestFormBuilder {
    private final Supplier<OffsetDateTime> timestamper;
    private final String securityKey;
    private final String apiLoginId;
    private final String apiVersion;
    private final String returnUrl;
    private final AppConfig config;

    public RequestFormBuilder(
            final Supplier<OffsetDateTime> timestamper, final AppConfig config) {
        this.timestamper = timestamper;
        this.config = config;
        this.securityKey = config.getApiSecureKey();
        this.apiLoginId = config.getApiAccessKey();
        this.apiVersion = config.getApiVersion();
        this.returnUrl = config.getBaseUrl() + config.getRestBase() + config.getRestReturnUrl();
    }

    public Map<String,String> build(final Request request) {
        final String transactionType = "sale";
        final String utcTime = UTCTicks.getUtcTime(timestamper.get()).toString();
        final String pgTsHash = calculateHash(
                apiLoginId, transactionType, request.getAmount().toString(), utcTime,
                request.getTransactionId(), apiVersion);
        final String completionUrl = String.format("%s/complete/%s",
                request.getClientLocation(), urlencoder.apply(request.getTransactionId().toString()));
        return ImmutableMap.<String, String>builder()
                .put("request_id", UUID.randomUUID().toString())
                .put("version_number", apiVersion)
                .put("api_access_id",apiLoginId)
                .put("allowed_methods", "visa,mast,disc,amex,echeck")
                .put("total_amount", request.getAmount().toString())
                .put("method", transactionType)
                .put("utc_time", utcTime)
                .put("signature", pgTsHash)
                .put("hash_method", "md5")
                .put("location_id", config.getApiLocationId())
                .put("order_number", request.getTransactionId().toString())
                .put("billing_name", String.format("%s %s",
                        orBlank.apply(request.getPersonalName().getFirstName()),
                        orBlank.apply(request.getPersonalName().getLastName())))
                .put("billing_company_name", orBlank.apply(request.getContact().getCompany()))
                .put("billing_street_line1", request.getContact().getStreet1())
                .put("billing_street_line", orBlank.apply(request.getContact().getStreet2()))
                .put("billing_locality", request.getContact().getCity())
                .put("billing_region", request.getContact().getState())
                .put("billing_postal_code", request.getContact().getPostCode())
                .put("billing_phone_number", orBlank.apply(request.getContact().getTelephone()))
                .put("billing_email_address", orBlank.apply(request.getContact().getEmail()))
                .put("return_url", returnUrl)
                .put("return_method", "AsyncPost")
                //.put("pg_continue_url", completionUrl)
                //.put("pg_cancel_url", completionUrl)
                .build();
    }

    private static final Function<String,String> orBlank = value ->
            Strings.isNullOrEmpty(value) ? "" : value.trim();

    private static final Function<String,String> urlencoder = value ->
            Try.of(() -> URLEncoder.encode(value, "UTF-8")).getOrElse(value);

    private String calculateHash(final String accessId, final String txType, final String amount,
                                 final String timestamp, final URI txOrderNb, final String version) {
        return HMacMD5.getHmacMD5(String.join("|", ImmutableList.<String>builder()
                .add(accessId, txType, version, amount, timestamp, txOrderNb.toString(), "", "")
                .build()), securityKey);
    }
}