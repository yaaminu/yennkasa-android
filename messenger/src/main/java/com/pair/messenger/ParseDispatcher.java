package com.pair.messenger;

import android.app.AlarmManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pair.data.Message;
import com.pair.data.util.MessageUtils;
import com.pair.parse_client.PARSE_CONSTANTS;

import java.util.List;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.client.Response;

/**
 * @author Null-Pointer on 8/29/2015.
 */
class ParseDispatcher extends AbstractMessageDispatcher {

    private static final String TAG = ParseDispatcher.class.getSimpleName();
    private static final RequestInterceptor INTERCEPTOR = new RequestInterceptor() {
        @Override
        public void intercept(RequestFacade requestFacade) {
            requestFacade.addHeader("X-Parse-Application-Id", "RcCxnXwO1mpkSNrU9u4zMtxQac4uabLNIFa662ZY");
            requestFacade.addHeader("X-Parse-REST-API-Key", "uFqdKVqA2l4D8uVsU3Vi7X5HLSVHjHe59md22ieB");
            requestFacade.addHeader("Content-Type", "application/json");
        }
    };
    public static final String PUSH_DATA = "data";
    public static final String PUSH_EXPIRATION_INTERVAL = "expiration_interval";
    public static final String PUSH_QUERY_WHERE = "where";
    public static final String PUSH_QUERY_CONTAINED_IN = "$in";

    private final ParsePushApi api = new RestAdapter.Builder().setEndpoint("https://api.parse.com/1")
            .setLog(new AndroidLog(TAG))
            .setLogLevel(RestAdapter.LogLevel.FULL)
            .setRequestInterceptor(INTERCEPTOR)
            .build().create(ParsePushApi.class);

    private static final Dispatcher<Message> INSTANCE = new ParseDispatcher();

    static synchronized Dispatcher<Message> getInstance() {
        return INSTANCE;
    }

    private ParseDispatcher() {
    }

    @Override
    public void dispatchToGroup(final Message message, List<String> members) {
        Gson gson = new GsonBuilder().create();
        JsonObject pushData = new JsonObject(), containedIn = new JsonObject();
        pushData.addProperty(PUSH_DATA, gson.toJson(message));
        JsonArray array = new JsonArray();
        for (String member : members) {
            array.add(gson.toJsonTree(member));
        }
        containedIn.add(PUSH_QUERY_CONTAINED_IN, array);
        JsonObject user = new JsonObject();
        long next30days = System.currentTimeMillis() + (AlarmManager.INTERVAL_DAY * 24);
        user.add(PARSE_CONSTANTS.FIELD_ID, containedIn);
        user.addProperty(PUSH_EXPIRATION_INTERVAL, next30days);
        pushData.add(PUSH_QUERY_WHERE, user);
        finallyDispatch(message, pushData);
    }

    private String dirtyJsonEncode(List<String> entries) {
        StringBuilder builder = new StringBuilder("[ \"");
        for (int i = 0; i < entries.size(); i++) {
            builder.append(entries.get(i));
            if (entries.size() != i + 1) { //are we at the end
                builder.append("\",\"");
            }
        }
        builder.append("\"]");
        return builder.toString();
    }

    @Override
    public void dispatchToUser(final Message message) {
        Gson gson = new GsonBuilder().create();
        JsonObject pushData = new JsonObject();
        pushData.addProperty(PUSH_DATA, gson.toJson(message));
        JsonObject query = new JsonObject();
        query.addProperty(PARSE_CONSTANTS.FIELD_ID, message.getTo());
        long next30days = System.currentTimeMillis() + (AlarmManager.INTERVAL_DAY * 24);
        query.addProperty(PUSH_EXPIRATION_INTERVAL, next30days);
        pushData.add(PUSH_QUERY_WHERE, query);
        finallyDispatch(message, pushData);
    }

    private void finallyDispatch(final Message message, final JsonObject pushData) {
        api.sendPush(pushData, new Callback<Response>() {
            @Override
            public void success(Response response, Response response2) {
                onSent(message.getId());
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                RetrofitError.Kind kind = retrofitError.getKind();
                switch (kind) {
                    case Kind.CONVERSION:
                        onFailed(message, MessageUtils.ERROR_INVALID_MESSAGE);
                        break;
                    case Kind.NETWORK:
                        onFailed(message, MessageUtils.ERROR_NOT_CONNECTED);
                        break;
                    case Kind.HTTP:
                        onFailed(message, MessageUtils.ERROR_UNKNOWN);
                        break;
                    case Kind.UNEXPECTED:
                        onFailed(message, MessageUtils.ERROR_UNKNOWN);
                        break;
                    default:
                        throw new AssertionError("unknown retrofit error");
                }
            }
        });
    }
}
