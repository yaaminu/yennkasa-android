package com.pair.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pair.data.User;

import java.util.Collection;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public class UserJsonAdapter implements BaseJsonAdapter<User> {
    @Override
    public JsonObject toJson(User user) {
        JsonObject obj = new JsonObject();
        obj.addProperty("_id", user.get_id());
        obj.addProperty("name", user.getName());
        obj.addProperty("password", user.getPassword());
        obj.addProperty("gcmRegId", user.getGcmRegId());
        obj.addProperty("DP", user.getDP());
        obj.addProperty("status", user.getStatus());
        obj.addProperty("lastActivity", user.getLastActivity());
        obj.addProperty("accountCreatedAt", user.getAccountCreated());
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
