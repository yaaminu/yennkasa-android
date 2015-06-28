package com.pair.messenger;

import android.app.AlarmManager;
import android.os.Handler;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.google.gson.JsonObject;
import com.pair.adapter.BaseJsonAdapter;
import com.pair.data.Message;
import com.pair.net.Dispatcher;
import com.pair.net.HttpResponse;
import com.pair.net.api.MessageApi;
import com.pair.pairapp.BuildConfig;
import com.pair.util.Config;
import com.pair.util.ConnectionHelper;

import org.apache.http.HttpStatus;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.AndroidLog;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

/**
 * @author by Null-Pointer on 5/26/2015.
 */
class MessageDispatcher implements Dispatcher<Message> {
    private static final String TAG = MessageDispatcher.class.getSimpleName();
    private volatile int NUM_OF_TASKS = 0;
    private static MessageDispatcher INSTANCE;
    private final int MAX_RETRY_TIMES;
    private final MessageApi MESSAGE_API;
    private final Object dispatcherMonitorLock = new Object();
    private final BaseJsonAdapter<Message> jsonAdapter;
    private final Sender sender;
    private final Set<DispatcherMonitor> monitors;
    private final Handler RETRY_HANDLER;

    private MessageDispatcher(BaseJsonAdapter<Message> jsonAdapter, DispatcherMonitor errorHandler, int retryTimes) {
        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint(Config.PAIRAPP_ENDPOINT)
                .setRequestInterceptor(Config.INTERCEPTOR)
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

    @Override
    public void dispatch(Message message) {
        incrementNumOfTasks();
        if (!ConnectionHelper.isConnectedOrConnecting()) {
            Log.w(TAG, "no internet connection, message will not be sent");
            reportError(message.getId(), "no internet connection");
            return;
        }
        if (message.getType() == Message.TYPE_DATE_MESSAGE) {
            Log.w(TAG, "attempted to send a date message,but will not be sent");
            reportError(message.getId(), "date messages cannot be sent");
            return;
        }
        if (message.getType() == Message.TYPE_TYPING_MESSAGE) {
            Log.w(TAG, "attempted to send a typing message,but will not be sent");
            reportError(message.getId(), "\'typing\' messages cannot be sent");
            return;
        }
        if (message.getType() != Message.TYPE_TEXT_MESSAGE) { //is it a binary message?
            if (!new File(message.getMessageBody()).exists()) {
                Log.w(TAG, "error: " + message.getMessageBody() + " is not a valid file path");
                reportError(message.getId(), "file does not exist");
                return;
            }
        }
        if ((message.getState() == Message.STATE_PENDING) || (message.getState() == Message.STATE_SEND_FAILED)) {
            doDispatch(message);
        } else {
            Log.w(TAG, "attempted to send a sent message, but will not be sent");
            reportError(message.getId(), "message already sent");
        }
    }

    @Override
    public void dispatch(Collection<Message> messages) {
        // TODO: 6/16/2015 change this once our backend supports batch sending
        for (Message message : messages) {
            dispatch(message);
        }
    }

    private void doDispatch(Message message) {
        JsonObject data = jsonAdapter.toJson(message);
        SenderJob job = new SenderJob(message.getId(), data, message.getType(), message.getMessageBody());
        sender.enqueue(job);
    }

    @Override
    public void addMonitor(DispatcherMonitor newErrorHandler) {
        synchronized (dispatcherMonitorLock) {
            this.monitors.add(newErrorHandler);
        }
    }

    @Override
    public void unregisterMonitor(DispatcherMonitor monitor) {
        synchronized (dispatcherMonitorLock) {
            this.monitors.remove(monitor);
        }
    }

    @Override
    public boolean cancelDispatchMayPossiblyFail(Message message) {
        return false;
    }

    private static class SenderJob {
        // TODO: 6/15/2015 sender job is to hardcoded, got to do something
        final static long MIN_DELAY = 5000; // 5 seconds
        public static final long MAX_DELAY = AlarmManager.INTERVAL_HOUR;
        int retries;
        final JsonObject data;
        final String id;
        long backOff;
        final int jobType;
        final String binPath;

        SenderJob(String id, JsonObject data, int jobType, String binPath) {
            this.retries = 0;
            this.data = data;
            this.id = id;
            this.backOff = MIN_DELAY;
            this.jobType = jobType;
            this.binPath = binPath;
        }

    }

    private class Sender {

        void enqueue(SenderJob job) {
            // TODO: 6/15/2015 implement an internal queue where some of the messages will be queued if there are too many to send
            doSend(job);
        }

        void doSend(final SenderJob job) {
            Log.i(TAG, "about to send message: " + job.data.toString());
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
            MESSAGE_API.sendMessage(job.data, file, responseCallback);
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
            MESSAGE_API.sendMessage(job.data, responseCallback);
        }

        private void handleError(RetrofitError retrofitError, SenderJob job) {
            if (retrofitError.getKind().equals(RetrofitError.Kind.UNEXPECTED)) {
                Log.w(TAG, "unexpected error, trying to send message again");
                tryAgain(job);
            } else if (retrofitError.getKind().equals(RetrofitError.Kind.HTTP)) {
                int statusCode = retrofitError.getResponse().getStatus();
                if (statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                    Log.w(TAG, "internal server error, trying to send again");
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
                if (ConnectionHelper.isConnectedOrConnecting()) {
                    tryAgain(job);
                } else {
                    //bubble up error and empty send queue let callers re-dispatch messages again;
                    Log.w(TAG, "no network connection, message will not be sent");
                    reportError(job.id, "Error in network connection");
                }
            }
        }

        private void tryAgain(final SenderJob job) {
            // TODO: 6/15/2015 check network availability before trying
            // TODO: 6/15/2015 if there is no network we wont try again but will rather wait till we get connected
            if (!ConnectionHelper.isConnectedOrConnecting()) {
                Log.w(TAG, "no network connection, message will not be sent");
                reportError(job.id, "no internet connection");
            } else if (job.retries < MAX_RETRY_TIMES) {
                job.retries++;
                job.backOff *= job.retries; //backoff exponentially
                if (job.backOff > SenderJob.MAX_DELAY) {
                    job.backOff = SenderJob.MAX_DELAY;
                }
                Log.i(TAG, "retrying in: " + job.backOff + " milliseconds");
                RETRY_HANDLER.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doSend(job); //async
                    }
                }, job.backOff);
            } else {
                Log.w(TAG, "unable to send message after: " + job.backOff + "seconds");
                reportError("an unknown error occurred made " + job.retries + " attempts made", job.id);
            }

        }

    }

    private void reportSuccess(SenderJob job) {
        decrementNumOfTasks();
        for (DispatcherMonitor dispatcherMonitor : monitors) {
            if (dispatcherMonitor != null) {
                dispatcherMonitor.onSendSucceeded(job.id);
            } else {
                warnAndThrowIfInDevelopment();
            }
        }
    }

    private void reportError(String jobId, String reason) {
        decrementNumOfTasks();
        for (DispatcherMonitor monitor : monitors) {
            if (monitor != null) {
                monitor.onSendFailed(reason, jobId);
            } else {
                warnAndThrowIfInDevelopment();
            }
        }
    }

    private void warnAndThrowIfInDevelopment() {
        Log.w(TAG, "null reference added as monitors");
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
        for (DispatcherMonitor monitor : monitors) {
            if (monitor != null) {
                monitor.onAllDispatched();
            } else {
                warnAndThrowIfInDevelopment();
            }
        }
    }
}
