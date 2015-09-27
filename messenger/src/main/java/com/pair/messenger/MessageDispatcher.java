package com.pair.messenger;

import android.app.AlarmManager;
import android.os.Handler;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.gson.JsonObject;
import com.pair.data.BaseJsonAdapter;
import com.pair.data.Message;
import com.pair.data.User;
import com.pair.data.net.FileApi;
import com.pair.data.net.HttpResponse;
import com.pair.net.MessageApi;
import com.pair.util.PLog;
import com.pair.util.Config;
import com.pair.util.ConnectionUtils;

import org.apache.http.HttpStatus;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import io.realm.Realm;
import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

/**
 * @author by Null-Pointer on 5/26/2015.
 */
@Deprecated
class MessageDispatcher implements Dispatcher<Message> {
    private static final String TAG = MessageDispatcher.class.getSimpleName();
    private static final RequestInterceptor INTERCEPTOR = new RequestInterceptor() {
        @Override
        public void intercept(RequestFacade requestFacade) {
            requestFacade.addHeader("Authorization", "kiiboda+=s3cr3te");
            requestFacade.addHeader("User-Agent", Config.APP_USER_AGENT);
        }
    };
    private static MessageDispatcher INSTANCE;
    private final int MAX_RETRY_TIMES;
    private final MessageApi MESSAGE_API;
    private final Object dispatcherMonitorLock = new Object();
    private final BaseJsonAdapter<Message> jsonAdapter;
    private final Sender sender;
    private final Set<DispatcherMonitor> monitors;
    private final Handler RETRY_HANDLER;
    private volatile int NUM_OF_TASKS = 0;

    private MessageDispatcher(BaseJsonAdapter<Message> jsonAdapter, DispatcherMonitor errorHandler, int retryTimes) {
        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint(Config.PAIRAPP_ENDPOINT)
                .setRequestInterceptor(INTERCEPTOR)
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setLog(new AndroidLog(TAG))
                .build();

        this.MESSAGE_API = adapter.create(MessageApi.class);
        this.sender = new Sender();
        this.monitors = new HashSet<>(1);
        this.monitors.add(errorHandler);
        this.MAX_RETRY_TIMES = (retryTimes < 0) ? 0 : retryTimes;
        this.jsonAdapter = jsonAdapter;
        this.RETRY_HANDLER = new Handler();
    }

    static MessageDispatcher getInstance(BaseJsonAdapter<Message> adapter, DispatcherMonitor dispatcherMonitor, int retryTimes) {
        MessageDispatcher localInstance = INSTANCE;
        if (localInstance == null) {
            synchronized (MessageDispatcher.class) {
                localInstance = INSTANCE;
                if (localInstance == null) {
                    INSTANCE = localInstance = new MessageDispatcher(adapter, dispatcherMonitor, retryTimes);
                }
            }
        }
        return localInstance;
    }

    private boolean failedScreening(Message message) {
        if (!ConnectionUtils.isConnectedOrConnecting()) {
            PLog.w(TAG, "no internet connection, message will not be sent");
            reportError(message.getId(), "no internet connection");
            return true;
        }
        if (message.getType() == Message.TYPE_DATE_MESSAGE) {
            PLog.w(TAG, "attempted to send a date message,but will not be sent");
            reportError(message.getId(), "date messages cannot be sent");
            return true;
        }
        if (message.getType() == Message.TYPE_TYPING_MESSAGE) {
            PLog.w(TAG, "attempted to send a typing message,but will not be sent");
            reportError(message.getId(), "\'typing\' messages cannot be sent");
            return true;
        }
        if (message.getType() != Message.TYPE_TEXT_MESSAGE) { //is it a binary message?
            if (!new File(message.getMessageBody()).exists()) {
                PLog.w(TAG, "error: " + message.getMessageBody() + " is not a valid file path");
                reportError(message.getId(), "file does not exist");
                return true;
            }
        }
        if ((message.getState() != Message.STATE_PENDING) && (message.getState() != Message.STATE_SEND_FAILED)) {
            PLog.w(TAG, "attempted to send a sent message, but will not be sent");
            reportError(message.getId(), "message already sent");
            return true;
        }
        return false;
    }

    @Override
    public void dispatch(Message message) {
        Realm realm = Realm.getInstance(Config.getApplicationContext());
        User recipient = realm.where(User.class).equalTo(User.FIELD_ID, message.getTo()).findFirst();
        if (recipient == null) {
            realm.close();
            Log.wtf(TAG, "recipient of message could not be found,exiting");
            throw new IllegalArgumentException("recipient of message could not be found");
        }
        int isGroup = (recipient.getType() == User.TYPE_GROUP) ? 1 : 0;
        realm.close();
        doDispatch(message, isGroup);
    }

    @Override
    public void dispatch(Collection<Message> messages) {
        // TODO: 6/16/2015 change this once our backend supports batch sending
        for (Message message : messages) {
            dispatch(message);
        }
    }

    @Override
    public void dispatch(Message message, FileApi.ProgressListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dispatch(Collection<Message> t, FileApi.ProgressListener listener) {
        throw new UnsupportedOperationException();
    }

    private void doDispatch(Message message, int isGroupMessage) {
        incrementNumOfTasks();
        if (failedScreening(message)) {
            return;
        }
        JsonObject data = jsonAdapter.toJson(message);
        SenderJob job = new SenderJob(message.getId(), data, message.getType(), message.getMessageBody(), isGroupMessage);
        sender.enqueue(job);
    }

    @Override
    public void addMonitor(DispatcherMonitor newErrorHandler) {
        synchronized (dispatcherMonitorLock) {
            this.monitors.add(newErrorHandler);
        }
    }

    @Override
    public void removeMonitor(DispatcherMonitor monitor) {
        synchronized (dispatcherMonitorLock) {
            this.monitors.remove(monitor);
        }
    }

    @Override
    public boolean cancelDispatchMayPossiblyFail(Message message) {
        return false;
    }

    @Override
    public void close() {
        //do nothing
    }

    private void reportSuccess(SenderJob job) {
        decrementNumOfTasks();
        synchronized (dispatcherMonitorLock) {
            for (DispatcherMonitor dispatcherMonitor : monitors) {
                if (dispatcherMonitor != null) {
                    dispatcherMonitor.onDispatchSucceed(job.id);
                } else {
                    warnAndThrowIfInDevelopment();
                }
            }
        }
    }

    private void reportError(String jobId, String reason) {
        decrementNumOfTasks();
        synchronized (dispatcherMonitorLock) {
            for (DispatcherMonitor monitor : monitors) {
                if (monitor != null) {
                    monitor.onDispatchFailed(reason, jobId);
                } else {
                    warnAndThrowIfInDevelopment();
                }
            }
        }
    }

    private void warnAndThrowIfInDevelopment() {
        PLog.w(TAG, "null reference added as monitors");
        if (BuildConfig.DEBUG) {
            throw new IllegalArgumentException("cannot passe a null reference as a monitor");
        }
    }

    private void decrementNumOfTasks() {
        synchronized (this) {
            NUM_OF_TASKS--;
        }
        if (NUM_OF_TASKS == 0) {
            allTaskComplete();
        }
    }

    private void incrementNumOfTasks() {
        synchronized (this) {
            NUM_OF_TASKS++;
        }
    }

    private void allTaskComplete() {
        synchronized (dispatcherMonitorLock) {
            for (DispatcherMonitor monitor : monitors) {
                if (monitor != null) {
                    monitor.onAllDispatched();
                } else {
                    warnAndThrowIfInDevelopment();
                }
            }
        }
    }

    private static class SenderJob {
        public static final long MAX_DELAY = AlarmManager.INTERVAL_HOUR;
        // TODO: 6/15/2015 sender job is to hardcoded, got to do something
        final static long MIN_DELAY = 5000; // 5 seconds
        final JsonObject data;
        final String id;
        final int jobType;
        final String binPath;
        final int toGroup;
        int retries;
        long backOff;

        SenderJob(String id, JsonObject data, int jobType, String binPath, int toGroup) {
            this.retries = 0;
            this.data = data;
            this.id = id;
            this.backOff = MIN_DELAY;
            this.jobType = jobType;
            this.binPath = binPath;
            this.toGroup = toGroup;
        }

    }

    private class Sender {

        void enqueue(SenderJob job) {
            // TODO: 6/15/2015 implement an internal queue where some of the messages will be queued if there are too many to send
            doSend(job);
        }

        void doSend(final SenderJob job) {
            PLog.i(TAG, "about to send message: " + job.data.toString());
            if (job.jobType == Message.TYPE_TEXT_MESSAGE) {
                sendTextMessage(job);
            } else if (job.jobType == Message.TYPE_DATE_MESSAGE) { // FIXME: 6/25/2015 take this off in production
                throw new AssertionError("impossible"); //should have been filtered off in dispatch().
            } else {
                sendBinaryMessage(job);
            }

        }

        void sendBinaryMessage(final SenderJob job) {
            final Callback<HttpResponse> responseCallback = new Callback<HttpResponse>() {
                @Override
                public void success(HttpResponse httpResponse, Response response) {
                    reportSuccess(job);
                }

                @Override
                public void failure(RetrofitError retrofitError) {
                    handleError(retrofitError, job);
                }
            };
            String mimeType = MimeTypeMap.getFileExtensionFromUrl(job.binPath);
            TypedFile file = new TypedFile(mimeType, new File((job.binPath)));
            MESSAGE_API.sendMessage(job.data, file, job.toGroup, responseCallback);
        }

        private void sendTextMessage(final SenderJob job) {
            final Callback<HttpResponse> responseCallback = new Callback<HttpResponse>() {
                @Override
                public void success(HttpResponse httpResponse, Response response) {
                    reportSuccess(job);
                }

                @Override
                public void failure(RetrofitError retrofitError) {
                    handleError(retrofitError, job);
                }
            };
            MESSAGE_API.sendMessage(job.data, job.toGroup, responseCallback);
        }

        private void handleError(RetrofitError retrofitError, SenderJob job) {
            if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
                PLog.w(TAG, "unexpected error, trying to send message again");
                tryAgain(job);
            } else if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)) {
                int statusCode = retrofitError.getResponse().getStatus();
                if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                    PLog.w(TAG, "internal server error, trying to send again");
                    tryAgain(job);
                } else {//crash early
                    // as far as we know, our backend will only return other status code if its is our fault and that normally should not happen
                    Log.wtf(TAG, "internal error, exiting");
                    throw new RuntimeException("An unknown internal error occurred");
                }
            } else if (retrofitError.getKind().equals(RetrofitError.Kind.CONVERSION)) { //crash early
                Log.wtf(TAG, "internal error, conversion error ");
                throw new RuntimeException("poorly encoded json data", retrofitError.getCause());
            } else if (retrofitError.getKind().equals(RetrofitError.Kind.NETWORK)) {
                if (ConnectionUtils.isConnectedOrConnecting()) {
                    tryAgain(job);
                } else {
                    //bubble up error and empty send queue let callers re-dispatch messages again;
                    PLog.w(TAG, "no network connection, message will not be sent");
                    reportError(job.id, "Error in network connection");
                }
            }
        }

        private void tryAgain(final SenderJob job) {
            // TODO: 6/15/2015 if there is no network we wont try again but will rather wait till we get connected
            if (!ConnectionUtils.isConnectedOrConnecting()) {
                PLog.w(TAG, "no network connection, message will not be sent");
                reportError(job.id, "no internet connection");
            } else if (job.retries < MAX_RETRY_TIMES) {
                job.retries++;
                job.backOff *= job.retries; //backoff exponentially
                if (job.backOff > SenderJob.MAX_DELAY) {
                    job.backOff = SenderJob.MAX_DELAY;
                }
                PLog.i(TAG, "retrying in: " + job.backOff + " milliseconds");
                RETRY_HANDLER.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doSend(job); //async
                    }
                }, job.backOff);
            } else {
                PLog.w(TAG, "unable to send message after: " + job.backOff + "seconds");
                reportError("an unknown error occurred made " + job.retries + " attempts made", job.id);
            }

        }

    }

}
