package com.accela.pianoforte.model;

import com.accela.pianoforte.common.HMacMD5;
import com.accela.pianoforte.common.UTCTicks;
import config.AppConfig;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Try;

import java.net.URI;
import java.net.URLEncoder;
import java.time.OffsetDateTime;
import java.util.Map;
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
        this.securityKey = config.getSecurityKey();
        this.apiLoginId = config.getApiLoginId();
        this.apiVersion = config.getApiVersion();
        this.returnUrl = config.getBaseUrl() + config.getRestBase() + config.getRestReturnUrl();
    }

    public Map<String,String> build(final Request request) {
        final String transactionType = config.mapTransactionType(request.getTransactionType());
        final String utcTime = UTCTicks.getUtcTime(timestamper.get()).toString();
        final String pgTsHash = calculateHash(
                apiLoginId, transactionType, request.getAmount().toString(), utcTime,
                request.getTransactionId(), apiVersion);
        final String completionUrl = String.format("%s/complete/%s",
                request.getClientLocation(), urlencoder.apply(request.getTransactionId().toString()));
        return ImmutableMap.<String, String>builder()
                .put("pg_billto_postal_name_first", orBlank.apply(request.getPersonalName().getFirstName()))
                .put("pg_billto_postal_name_last", orBlank.apply(request.getPersonalName().getLastName()))
                .put("pg_billto_postal_name_company", orBlank.apply(request.getContact().getCompany()))
                .put("pg_billto_postal_street_line1", request.getContact().getStreet1())
                .put("pg_billto_postal_street_line2", orBlank.apply(request.getContact().getStreet2()))
                .put("pg_billto_postal_city", request.getContact().getCity())
                .put("pg_billto_postal_stateprov", request.getContact().getState())
                .put("pg_billto_postal_postalcode", request.getContact().getPostCode())
                .put("pg_billto_telecom_phone_number", orBlank.apply(request.getContact().getTelephone()))
                .put("pg_billto_online_email", orBlank.apply(request.getContact().getEmail()))
                .put("pg_api_login_id",apiLoginId)
                .put("pg_transaction_type", transactionType)
                .put("pg_version_number", apiVersion)
                .put("pg_total_amount", request.getAmount().toString())
                .put("pg_utc_time", utcTime)
                .put("pg_transaction_order_number", request.getTransactionId().toString())
                .put("pg_ts_hash", pgTsHash)
                .put("pg_return_url", returnUrl)
                .put("pg_continue_url", completionUrl)
                .put("pg_cancel_url", completionUrl)
                .put("pg_return_method", "AsyncPost")
                .build();
    }

    private static final Function<String,String> orBlank = value ->
            Strings.isNullOrEmpty(value) ? "" : value.trim();

    private static final Function<String,String> urlencoder = value ->
            Try.of(() -> URLEncoder.encode(value, "UTF-8")).getOrElse(value);

    private String calculateHash(final String userId, final String txType, final String amount,
                                 final String utcTimestamp, final URI txOrderNb, final String version) {
        return HMacMD5.getHmacMD5(String.join("|", ImmutableList.<String>builder()
                .add(userId, txType, version, amount, utcTimestamp, txOrderNb.toString())
                .build()), securityKey);
    }
}