package com.yennkasa.net.sockets;

import com.yennkasa.data.UserManager;
import com.yennkasa.util.GenericUtils;

import java.io.File;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;

/**
 * @author aminu on 7/9/2016.
 */
public class Sendable extends RealmObject {

    public static final String
            FIELD_INDEX = "index",
            FIELD_COLLAPSE_KEY = "collapseKey",
            FIELD_DATA = "data",
            FIELD_RETRIES = "retries",
            FEILD_MAX_RETRIES = "maxRetries",
            FIELD_START_PROCESSING_AT = "startProcessingAt",
            FIELD_VALID_UNTIL = "validUntil",
            FIELD_SURVIVES_RESTART = "surviveRestarts",
            FIELD_PROCESSING = "processing";
    public static final int INVALID_INDEX = -1;
    public static final int DEFAULT_EXPIRY_DATE = 1000 * 60 * 15;
    public static final int DEFAULT_MAX_RETRIES = 1;
    public static final int RETRY_FOREVER = -1;

    @Index
    private long index;
    @Index
    private String collapseKey;
    @PrimaryKey
    private String data;
    private int retries, maxRetries;
    private long startProcessingAt, validUntil;
    private boolean surviveRestarts, processing;


    public Sendable() {
    }

    private Sendable(String data, String collapseKey,
                     int maxRetries,
                     long startProcessingAt,
                     long validUntil,
                     boolean surviveRestarts) {
        GenericUtils.ensureNotEmpty(data, collapseKey);
        GenericUtils.ensureConditionTrue(maxRetries == RETRY_FOREVER || maxRetries >= 0, "max retries < 0");
        GenericUtils.ensureConditionTrue(startProcessingAt <= validUntil, "startProcessingAt is in the future");

        this.index = INVALID_INDEX;
        this.validUntil = validUntil;
        this.startProcessingAt = startProcessingAt;
        this.collapseKey = collapseKey;
        this.surviveRestarts = surviveRestarts;
        this.retries = 0;
        this.maxRetries = maxRetries;
        this.data = data;
    }

    public long getIndex() {
        return index;
    }

    void setIndex(long index) {
        this.index = index;
    }

    public String getCollapseKey() {
        return collapseKey;
    }

    public String getData() {
        return data;
    }

    public int getRetries() {
        return retries;
    }

    void setRetries(int retries) {
        this.retries = retries;
    }

    void setValidUntil(long date) {
        this.validUntil = date;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getStartProcessingAt() {
        return startProcessingAt;
    }

    public long getValidUntil() {
        return validUntil;
    }

    public boolean surviveRestarts() {
        return surviveRestarts;
    }

    public boolean isProcessing() {
        return processing;
    }

    void setProcessing(boolean processing) {
        this.processing = processing;
    }

    public boolean isExpired() {
        return this.validUntil <= System.currentTimeMillis();
    }

    public boolean exceededRetries() {
        if (this.getMaxRetries() == RETRY_FOREVER) return false;
        return this.getRetries() >= this.getMaxRetries();
    }

    public static Realm Realm(File folder) {
        GenericUtils.ensureNotNull(folder);
        return Realm.getInstance(new RealmConfiguration.Builder().directory(folder)
                .schemaVersion(0)
                .encryptionKey(UserManager.getKey())
                .deleteRealmIfMigrationNeeded().build());
    }

    public static class Builder {

        private long startProcessingAt;
        private long validUntil;
        private int maxRetries;
        private boolean surviveRestarts;
        private String data, collapseKey;

        public Builder() {
            this.startProcessingAt = System.currentTimeMillis();
            this.validUntil = System.currentTimeMillis() + DEFAULT_EXPIRY_DATE;
            this.maxRetries = DEFAULT_MAX_RETRIES;
            this.surviveRestarts = true;
        }

        public Builder maxRetries(int maxRetries) {
            GenericUtils.ensureConditionTrue(maxRetries == RETRY_FOREVER || maxRetries >= 0, "invalid maxRetries");
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder startProcessingAt(long startProcessingAt) {
            GenericUtils.ensureConditionTrue(startProcessingAt >= 0L, "invalid date");
            this.startProcessingAt = startProcessingAt;
            return this;
        }

        public Builder validUntil(long validUntil) {
            GenericUtils.ensureConditionTrue(validUntil >= System.currentTimeMillis() - 1000, "valid until is in the past");
            this.validUntil = validUntil;
            return this;
        }

        public Builder data(String data) {
            GenericUtils.ensureNotEmpty(data);
            this.data = data;
            return this;
        }

        public Builder surviveRestarts(boolean survivesRestarts) {
            this.surviveRestarts = survivesRestarts;
            return this;
        }

        public Builder collapseKey(String collapseKey) {
            GenericUtils.ensureNotEmpty(collapseKey);
            this.collapseKey = collapseKey;
            return this;
        }

        public Sendable build() {
            GenericUtils.ensureNotEmpty(data, collapseKey);

            return new Sendable(data, collapseKey,
                    maxRetries, startProcessingAt,
                    validUntil, surviveRestarts);
        }

    }
}
