package com.yennkasa.data;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.Required;

/**
 * @author by aminu on 11/13/2016.
 */

public class UnproccessedMessage extends RealmObject {

    public static final String FIELD_DATE_CREATED = "dateCreated";

    @Required
    private String payload;
    private long dateCreated;

    public UnproccessedMessage(String payload, long dateCreated) {
        this.payload = payload;
        this.dateCreated = dateCreated;
    }

    public UnproccessedMessage() {//required no-arg c'tor

    }

    public String getPayload() {
        return payload;
    }

    public long getDateCreated() {
        return dateCreated;
    }

    public static Realm REALM() {
        return Message.REALM();
    }
}
