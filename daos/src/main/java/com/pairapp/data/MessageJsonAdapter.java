package com.pairapp.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Date;

import static com.pairapp.data.Message.FIELD_DATE_COMPOSED;
import static com.pairapp.data.Message.FIELD_FROM;
import static com.pairapp.data.Message.FIELD_ID;
import static com.pairapp.data.Message.FIELD_MESSAGE_BODY;
import static com.pairapp.data.Message.FIELD_STATE;
import static com.pairapp.data.Message.FIELD_TO;
import static com.pairapp.data.Message.FIELD_TYPE;

/**
 * @author by Null-Pointer on 5/27/2015.
 */
class MessageJsonAdapter implements BaseJsonAdapter<Message> {
    @Override
    public JsonObject toJson(Message message) {
        JsonObject obj = new JsonObject();

        obj.addProperty(FIELD_FROM, message.getFrom());
        obj.addProperty(FIELD_TO, message.getTo());
        obj.addProperty(FIELD_STATE, message.getState());
        obj.addProperty(FIELD_ID, message.getId());
        obj.addProperty(FIELD_MESSAGE_BODY, message.getMessageBody());
        obj.addProperty(FIELD_DATE_COMPOSED, message.getDateComposed().getTime());
        obj.addProperty(FIELD_TYPE, message.getType());
        return obj;
    }

    @SuppressWarnings("unused")
    public JSONObject toJSON(Message message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(FIELD_FROM, message.getFrom());
            obj.put(FIELD_TO, message.getTo());
            obj.put(FIELD_STATE, message.getState());
            obj.put(FIELD_ID, message.getId());
            obj.put(FIELD_MESSAGE_BODY, message.getMessageBody());
            obj.put(FIELD_DATE_COMPOSED, message.getDateComposed().getTime());
            obj.put(FIELD_TYPE, message.getType());
            return obj;
        } catch (JSONException impossible) {
            throw new RuntimeException("impossible");
        }
    }

    @Override
    public JsonArray toJson(Collection<Message> messages) {
        JsonArray array = new JsonArray();
        for (Message message : messages) {
            array.add(toJson(message));
        }
        return array;
    }

    public Message fromJson(String currentUserId, String json) {
        try {
            JSONObject obj = new JSONObject(json);
            Message message = new Message();
            message.setFrom(obj.getString(FIELD_FROM));
            message.setState(obj.optInt(FIELD_STATE, Message.STATE_PENDING));
            message.setId(obj.getString(FIELD_ID));
            message.setTo(obj.optString(FIELD_TO, currentUserId));
            message.setDateComposed(new Date(obj.optLong(FIELD_DATE_COMPOSED, System.currentTimeMillis())));
            message.setType(obj.optInt(FIELD_TYPE, Message.TYPE_TEXT_MESSAGE));
            message.setMessageBody(obj.getString(FIELD_MESSAGE_BODY));
            return message;
        } catch (JSONException e) {
            throw new IllegalArgumentException(e.getCause());
        }
    }

    static final MessageJsonAdapter INSTANCE = new MessageJsonAdapter();
}
