package com.pair.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pair.data.User;

import java.util.Collection;

/**
 * @author Null-Pointer on 5/27/2015.
 */
public class UserJsonAdapter implements BaseJsonAdapter<User> {
    @Override
    public JsonObject toJson(User user) {
        JsonObject obj = new JsonObject();
        obj.addProperty(User.FIELD_ID, user.get_id());
        obj.addProperty(User.FIELD_NAME, user.getName());
        obj.addProperty(User.FIELD_GCM_REG_ID, user.getGcmRegId());
        obj.addProperty(User.FIELD_PASSWORD, user.getPassword());
        obj.addProperty(User.FIELD_STATUS, user.getStatus());
        obj.addProperty(User.FIELD_LAST_ACTIVITY, user.getLastActivity());
        obj.addProperty(User.FIELD_ACCOUNT_CREATED, user.getAccountCreated());
        return obj;
    }

    @Override
    public JsonArray toJson(Collection<User> users) {
        JsonArray array = new JsonArray();
        for (User user : users) {
            array.add(toJson(user));
        }
        return array;
    }
}
