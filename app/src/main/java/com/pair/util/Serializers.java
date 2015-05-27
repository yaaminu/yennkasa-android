package com.pair.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.pair.data.Message;

import java.lang.reflect.Type;

/**
 * Created by Null-Pointer on 5/27/2015.
 */
public class Serializers implements JsonSerializer<Message> {

    @Override
    public JsonElement serialize(Message src, Type type, JsonSerializationContext jsonSerializationContext) {
        JsonObject obj = new JsonObject();
        obj.addProperty("from", src.getFrom());
        obj.addProperty("to", src.getTo());
        obj.addProperty("state", src.getState());
        obj.addProperty("id", src.getId());
        obj.addProperty("messageBody", src.getMessageBody());
        obj.addProperty("dateComposed", src.getDateComposed().toString());
        return obj;
    }
}
