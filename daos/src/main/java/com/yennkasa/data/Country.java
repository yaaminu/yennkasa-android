package com.yennkasa.data;

import android.content.Context;

import com.yennkasa.util.Config;

import java.io.File;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmObject;
import io.realm.annotations.RealmClass;

/**
 * @author Null-Pointer on 7/29/2015.
 */
@RealmClass
public class Country extends RealmObject {
    public static final String FIELD_NAME = "name", FIELD_CCC = "ccc", FIELD_ISO_2_LETTER_CODE = "iso2letterCode";
    private String name;
    private String ccc;
    private String iso2letterCode;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCcc() {
        return ccc;
    }

    public void setCcc(String ccc) {
        this.ccc = ccc;
    }

    public String getIso2letterCode() {
        return iso2letterCode;
    }

    public void setIso2letterCode(String iso2letterCode) {
        this.iso2letterCode = iso2letterCode;
    }

    public static Realm REALM(Context context) {
        return Realm.getInstance(config);
    }

    // FIXME: 1/14/2016 add key
    private static final RealmConfiguration config;

    static {
        File file = Config.getApplicationContext().getDir("data", Context.MODE_PRIVATE);
        config = new RealmConfiguration.Builder().directory(file)
                .name("countries.realm")
                .schemaVersion(0)
                .deleteRealmIfMigrationNeeded().build();
    }
}
