package com.yennkasa.data;

import android.content.Context;

import com.yennkasa.Errors.ErrorCenter;
import com.yennkasa.util.Config;
import com.yennkasa.util.PLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.RealmClass;
import io.realm.exceptions.RealmException;

/**
 * @author _2am on 9/27/2015.
 */
@SuppressWarnings("unused")
@RealmClass
public class PersistedSetting extends RealmObject {

    public static final int TYPE_CATEGORY = 1,
            TYPE_TRUE_FALSE = 2,
            TYPE_LIST_STRING = 4, TYPE_INTEGER = 8;
    public static final String FIELD_KEY = "key",
            FIELD_STANDALONE = "standAlone",
            FIELD_ORDER = "order",
            FIELD_INT_VALUE = "intValue",
            FIELD_BOOL_VALUE = "boolValue",
            FIELD_STRING_VALUE = "stringValue",
            FIELD_SUMMARY = "summary",
            FIELD_TITLE = "title",
            FIELD_TYPE = "type";
    private static final String PART_1 = "part1";
    private static final String PART_2 = "part2";
    @PrimaryKey
    private String key;

    private String title;
    private boolean standAlone;

    private String summary;

    private int order, type;

    private int intValue;

    private boolean boolValue;

    private String stringValue;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean getBoolValue() {
        return boolValue;
    }

    public void setBoolValue(boolean boolValue) {
        this.boolValue = boolValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }


    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getIntValue() {
        return intValue;
    }

    public void setIntValue(int intValue) {
        this.intValue = intValue;
    }


    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isStandAlone() {
        return standAlone;
    }

    public void setStandAlone(boolean standAlone) {
        this.standAlone = standAlone;
    }

    public static Realm REALM(File writableFolder) {
        if (writableFolder == null) {
            throw new IllegalArgumentException("null!");
        }
        if (!writableFolder.isDirectory() && !writableFolder.mkdirs()) {
            throw new IllegalArgumentException("writable folder is not a directory");
        }
        try {
            return Realm.getInstance(config/*, UserManager.getKey()*/);
        } catch (RealmException e) {
            ErrorCenter.reportError("realmSecureError", Config.getApplicationContext().getString(R.string.encryptionNotAvailable), null);
            return Realm.getInstance(config);
        } catch (Exception e) {
            PLog.d("error", e.getMessage());
            throw new RuntimeException(e.getCause());
        }
    }

    // FIXME: 1/14/2016 add key
    private static final RealmConfiguration config;

    static {
        File file = Config.getApplicationContext().getDir("data", Context.MODE_PRIVATE);
        config = new RealmConfiguration.Builder().directory(file)
                .name("settings.realm")
                .schemaVersion(0)
                .encryptionKey(UserManager.getKey())
                .deleteRealmIfMigrationNeeded().build();
    }

    public static void put(Realm realm, PersistedSetting persistedSetting, Object newValue) {
        if (newValue == null) {
            throw new IllegalArgumentException("null");
        }
        realm.beginTransaction();

        if (!put(persistedSetting, newValue)) {
            realm.cancelTransaction();
            throw new IllegalArgumentException("unknown type");
        }
        realm.commitTransaction();
    }

    public static boolean put(PersistedSetting persistedSetting, Object newValue) {
        if (persistedSetting.getType() == TYPE_CATEGORY) {
            return true;
        }
        if (newValue == null) {
            return false;
        }
        if (newValue instanceof Boolean) {
            persistedSetting.setBoolValue((Boolean) newValue);
        } else if (newValue instanceof Integer) {
            persistedSetting.setIntValue((Integer) newValue);
        } else if (newValue instanceof String) {
            persistedSetting.setStringValue(((String) newValue));
        } else {
            return false;
        }
        return true;
    }

    public static PersistedSetting copy(PersistedSetting other) {
        return copy(other, false);
    }

    public static PersistedSetting copy(PersistedSetting other, boolean forceCopy) {
        if (other == null) {
            throw new IllegalArgumentException("can't make a copy");
        }
        if (!other.isValid() && !forceCopy) {
            return other;
        }
        PersistedSetting copy = new PersistedSetting();
        copy.setBoolValue(other.getBoolValue());
        copy.setStringValue(other.getStringValue());
        copy.setIntValue(other.getIntValue());
        copy.setOrder(other.getOrder());
        copy.setKey(other.getKey());
        copy.setType(other.getType());
        copy.setSummary(other.getSummary());
        copy.setTitle(other.getTitle());
        return copy;
    }

    public static List<PersistedSetting> copy(Collection<PersistedSetting> persistedSettings) {
        if (persistedSettings == null || persistedSettings.isEmpty()) {
            return Collections.emptyList();
        }
        List<PersistedSetting> copy = new ArrayList<>(persistedSettings.size());

        for (PersistedSetting persistedSetting : persistedSettings) {
            copy.add(copy(persistedSetting));
        }
        return copy;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }


    public static class Builder {
        private final PersistedSetting building;
        int fieldCount = 4;
        int fieldSet = 0;

        public Builder(String key) {
            ensureNotEmpty(key);
            building = new PersistedSetting();
            building.setKey(key);
            building.setStandAlone(false);
            fieldSet++;
        }

        public Builder title(String title) {
            building.setTitle(title);
            return this;
        }

        public Builder summary(String summary) {
            if (summary == null || "".equals(summary.trim())) {
                throw new IllegalArgumentException("empty or null!");
            }
            this.building.setSummary(summary);
            return this;
        }

        public Builder value(Object value) {
            if (!put(this.building, value)) {
                throw new IllegalArgumentException("unknown value type");
            }
            fieldSet++;
            return this;
        }


        public Builder type(int type) {
            ensureValidType(type);
            this.building.setType(type);
            fieldSet++;
            return this;
        }

        public Builder order(int order) {
            if (order < 10) throw new IllegalArgumentException("order cannot be less than 10");
            this.building.setOrder(order);
            fieldSet++;
            return this;
        }

        private void ensureValidType(int type) {
            if (type != TYPE_CATEGORY && type != TYPE_LIST_STRING && type != TYPE_TRUE_FALSE && type != TYPE_INTEGER) {
                throw new IllegalArgumentException("unknown type");
            }
        }


        private void ensureNotEmpty(String value) {
            if (value == null || "".equals(value.trim())) {
                throw new IllegalArgumentException("key cannot be empty");
            }
        }

        public PersistedSetting build() {
            if (fieldSet < fieldCount) {
                throw new IllegalStateException(fieldCount - fieldSet + 1 + " required fields are missing");
            }
            return this.building;
        }
    }
}
