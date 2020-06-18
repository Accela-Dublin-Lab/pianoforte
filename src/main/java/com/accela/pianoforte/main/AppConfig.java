package com.accela.pianoforte.main;

import io.vavr.control.Try;

import java.util.Properties;

public class AppConfig {
    public static final String CHECKOUT_URL = "checkout-url";
    public static final String API_VERSION = "api-version";
    public static final String API_LOCATION_ID = "api-location-id";
    public static final String API_ACCESS_KEY = "api-access-key";
    public static final String API_SECURE_KEY = "api-secure-key";
    public static final String REST_CONTEXT_PATH = "rest-context-path";
    public static final String REST_SERVER_HOST = "rest-server-host";
    public static final String REST_LOCAL_PORT = "rest-local-port";
    public static final String REST_BASE = "rest-base";
    public static final String REST_CHECKOUT_PAYMENT = "rest-checkout-payment";
    public static final String REST_RETURN_URL = "rest-return-url";
    private final Properties appProps;

    public AppConfig(final String propertyFile) {
        appProps = new Properties();
        Try.of(() -> {
            appProps.load(this.getClass().getResourceAsStream(propertyFile));
            return null;
        });
    }

    public void update(final String key, final String value) {
        appProps.put(key, value);
    }

    public String getCheckoutUrl() {
        return appProps.getProperty(CHECKOUT_URL, "");
    }

    public String getApiLocationId() {
        return appProps.getProperty(API_LOCATION_ID, "");
    }

    public String getApiVersion() {
        return appProps.getProperty(API_VERSION, "");
    }

    public String getApiAccessKey() {
        return appProps.getProperty(API_ACCESS_KEY, "");
    }

    public String getApiSecureKey() {
        return appProps.getProperty(API_SECURE_KEY, "");
    }
    public String mapTransactionType(final String code) {
        return appProps.getProperty("forte.transaction.code."+code,"");
    }

    public String getRestContextPath() {
        return appProps.getProperty(REST_CONTEXT_PATH, "");
    }

    public String getRestServerHost() {
        return appProps.getProperty(REST_SERVER_HOST, "");
    }

    public int getRestLocalPort() {
        return Integer.parseInt(appProps.getProperty(REST_LOCAL_PORT, "0"));
    }

    public String getBaseUrl() {
        return String.format("%s/%s", getRestServerHost(), getRestContextPath());
    }

    public String getRestBase() {
        return appProps.getProperty(REST_BASE, "");
    }

    public String getRestCheckoutPayment() {
        return appProps.getProperty(REST_CHECKOUT_PAYMENT, "");
    }

    public String getRestReturnUrl() {
        return appProps.getProperty(REST_RETURN_URL, "");
    }

    public String getResponseDescription(final String code) {
        return appProps.getProperty("forte.response.code."+code, "("+code+" is undefined)");
    }
}
