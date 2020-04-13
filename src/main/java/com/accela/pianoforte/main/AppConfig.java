package com.accela.pianoforte.main;

import io.vavr.control.Try;

import java.util.Properties;

public class AppConfig {
    public static final String CHECKOUT_URL = "checkout-url";
    public static final String SECURITY_KEY = "security-key";
    public static final String API_VERSION = "api-version";
    public static final String API_LOGIN_ID = "api-login-id";
    public static final String TRANSACTION_CCARD_SALE = "transaction-ccard-sale";
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

    public String getApiLoginId() {
        return appProps.getProperty(API_LOGIN_ID, "");
    }

    public String getSecurityKey() {
        return appProps.getProperty(SECURITY_KEY, "");
    }

    public String getApiVersion() {
        return appProps.getProperty(API_VERSION, "");
    }

    public String getTransactionType() {
        return appProps.getProperty(TRANSACTION_CCARD_SALE, "");
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
}
