package com.pair.net.sockets;

import android.util.Log;
import android.util.Pair;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.io.Closeable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.nkzawa.socketio.client.Socket.EVENT_CONNECT;
import static com.github.nkzawa.socketio.client.Socket.EVENT_CONNECT_ERROR;
import static com.github.nkzawa.socketio.client.Socket.EVENT_CONNECT_TIMEOUT;
import static com.github.nkzawa.socketio.client.Socket.EVENT_DISCONNECT;
import static com.github.nkzawa.socketio.client.Socket.EVENT_ERROR;
import static com.github.nkzawa.socketio.client.Socket.EVENT_RECONNECT;
import static com.github.nkzawa.socketio.client.Socket.EVENT_RECONNECTING;
import static com.github.nkzawa.socketio.client.Socket.EVENT_RECONNECT_ERROR;
import static com.github.nkzawa.socketio.client.Socket.EVENT_RECONNECT_FAILED;
import static com.github.nkzawa.socketio.client.Socket.Listener;

public class SocketIoClient implements Closeable {

    public static final String TAG = "SocketsIOClient";
    public static final int PING_DELAY = 60000;
    public static final String PING = "ping";
    public static final String PONG = "pong";
    public static final String RECONNECT_AND_PING_TIMER_TIMER = "reconnectAndPingTimer timer";
    private final AtomicBoolean initialised = new AtomicBoolean(false),
            ready = new AtomicBoolean(false),
            pongedBack = new AtomicBoolean(false);
    private final AtomicInteger referenceCount = new AtomicInteger(0), retryAttempts = new AtomicInteger(0);
    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_MSG_STATUS = "msgStatus";

    private final List<Pair<String, Object>> waitingBroadcasts = new ArrayList<>();

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final String userId;

    private Socket CLIENT;
    private static final WeakHashMap<String, SocketIoClient> instances = new WeakHashMap<>();
    private Timer reconnect_OR_PingTimer;
    private String endPoint;


    private final Listener ON_PONG = new Listener() {
        @Override
        public void call(Object... args) {
            Log.i(TAG, "pong");
            ready.set(true);
            pongedBack.set(true);
            retryAttempts.set(0);
        }
    };
    private final Listener ON_RECONNECTING = new Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
        }
    };

    private final Listener ON_ERROR = new Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            Log.e(TAG, "client error!: ");
            if (args.length > 0) {
                Log.e(TAG, "message: " + args[0]);
            }
        }
    };
    private final Listener ON_RECONNECT = new Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            Log.i(TAG, "preparing to reconnect...");
            attemptReconnect();
        }
    };


    private final Emitter.Listener ON_CONNECTED = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.i(TAG, "connected");
            ready.set(true);
            pongedBack.set(true);
            retryAttempts.set(0);
            sendQueuedMessages();
        }
    };

    private void sendQueuedMessages() {
        if (CLIENT.connected()) {
            synchronized (waitingBroadcasts) {
                for (Pair<String, Object> waitingBroadcast : waitingBroadcasts) {
                    CLIENT.emit(waitingBroadcast.first, waitingBroadcast.second);
                }
                waitingBroadcasts.clear();
            }
        }
    }

    private final Emitter.Listener ON_DISCONNECTED = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            if (initialised.get() && referenceCount.get() > 0) {
                Log.i(TAG, "socket disconnected unexpectedly, retrying");
                Log.i(TAG, args[0] + "");
                attemptReconnect();
            } else {
                Log.i(TAG, "disconnected");
            }
        }
    };

    private final Emitter.Listener ON_CONNECT_TIMEOUT = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            Log.i(TAG, "connection timed out");
        }
    };

    private final Emitter.Listener ON_CONNECT_ERROR = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            ready.set(false);
            Log.i(TAG, "connection error");
        }
    };

    private SocketIoClient(String endPoint, String userId) {
        //if we are in a testing environment we cannot use handlers
        //more so if we are initialised from a thread with no looper
        initialise(endPoint, userId);
        this.endPoint = endPoint;
        this.userId = userId;
    }

    private void ping() {
        if (initialised.get()) {
            if (pongedBack.get()) {
                CLIENT.emit(PING);
                pongedBack.set(false);
            } else {
                attemptReconnect();
            }
        }
    }

    private void initialise(String endPoint, String userId) {
        if (!initialised.get()) {
            try {
                //noinspection unused
                URL url = new URL(endPoint); //this is just to ensure urls passed are valid.
                IO.Options options = new IO.Options();
                options.query = "userId=" + userId;
                options.forceNew = true;
                CLIENT = IO.socket(URI.create(endPoint), options);
                CLIENT.on(EVENT_CONNECT, ON_CONNECTED);
                CLIENT.on(EVENT_DISCONNECT, ON_DISCONNECTED);
                CLIENT.on(EVENT_CONNECT_ERROR, ON_CONNECT_ERROR);
                CLIENT.on(EVENT_CONNECT_TIMEOUT, ON_CONNECT_TIMEOUT);
                CLIENT.on(EVENT_RECONNECT, ON_RECONNECT);
                CLIENT.on(EVENT_RECONNECT_ERROR, ON_CONNECT_ERROR);
                CLIENT.on(EVENT_ERROR, ON_ERROR);
                CLIENT.on(EVENT_RECONNECT_FAILED, ON_CONNECT_ERROR);
                CLIENT.on(EVENT_RECONNECTING, ON_RECONNECTING);
                CLIENT.on(PONG, ON_PONG);
                reconnect_OR_PingTimer = new Timer(RECONNECT_AND_PING_TIMER_TIMER);
                TimerTask pingTask = new TimerTask() {
                    @Override
                    public void run() {
                        ping();
                    }
                };
                long delay = getDelay();
                reconnect_OR_PingTimer.scheduleAtFixedRate(pingTask, delay, delay);
                CLIENT.connect();
                initialised.set(true);
            } catch (URISyntaxException impossible) {
                throw new IllegalArgumentException("url passed is invalid");
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("invalid url passed");
            }
        }
    }

    private int getDelay() {
        SecureRandom random = new SecureRandom();
        int deviation = (int) Math.abs(random.nextDouble() * (30000 - 1000) + 1000);
        return PING_DELAY + deviation;
    }

    private long getNextDelay(long currentDelay) {
        SecureRandom random = new SecureRandom();
        int ONE_MINUTE = 60 * 1000;
        int deviation = (int) Math.abs(random.nextDouble() * (ONE_MINUTE * 5 - ONE_MINUTE) + ONE_MINUTE);
        //EXPONENTIAL
        return currentDelay * 2 + deviation;
    }

    public static SocketIoClient getInstance(String endPoint, String userId) {
        synchronized (instances) {
            SocketIoClient client = instances.get(endPoint);
            if (client == null) {
                client = new SocketIoClient(endPoint, userId);
                instances.put(endPoint, client);
                client.referenceCount.set(1);
            } else {
                client.referenceCount.incrementAndGet();
            }
            return client;
        }
    }

    public boolean registerForEvent(String eventName, Listener eventReceiver) {
        if (!initialised.get()) {
            Log.w(TAG, "can't register for an event. client yet to start");
            return false;
        }
        CLIENT.on(eventName, eventReceiver);
        return true;
    }

    public boolean unRegisterEvent(String eventName, Listener eventReceiver) {
        if (!initialised.get()) {
            Log.w(TAG, "can't unregister  an event. client yet to start");
            return false;
        }
        CLIENT.off(eventName, eventReceiver);
        return true;
    }

    @SuppressWarnings("unused")
    public boolean registerForEventOnce(String eventName, Listener eventReceiver) {
        if (!initialised.get()) {
            return false;
        }
        CLIENT.once(eventName, eventReceiver);
        return true;
    }

    public void broadcast(String eventName, Object jsonData) {
        Log.d(TAG, "emitting: " + eventName + " with data" + jsonData);
        if (ready.get() && CLIENT.connected()) {
            CLIENT.emit(eventName, jsonData);
        } else {
            ready.set(false);
            synchronized (waitingBroadcasts) {
                //queue the message.
                waitingBroadcasts.add(new Pair<>(eventName, jsonData));
                Log.i(TAG, "message queued for sending later client yet to start");
            }
            attemptReconnect();
        }
    }

    public void send(Object jsonData) {
        broadcast(EVENT_MESSAGE, jsonData);
    }

    @Override
    public synchronized void close() {
        synchronized (instances) {
            if (initialised.get()) {
                if (referenceCount.decrementAndGet() == 0) {
                    instances.remove(getEndPoint());
                    if (CLIENT.connected()) {
                        CLIENT.disconnect();
                    }
                    if (reconnect_OR_PingTimer != null) {
                        reconnect_OR_PingTimer.cancel();
                        reconnect_OR_PingTimer = null;
                    }
                    CLIENT.close();
                    initialised.set(false);
                    ready.set(false);
                }
            }
        }
    }

    private synchronized void attemptReconnect() {
        if (reconnecting.getAndSet(true)) {
            return;
        }
        if (retryAttempts.incrementAndGet() >= 5) {
            if (reconnect_OR_PingTimer != null) {
                reconnect_OR_PingTimer.cancel();
                reconnect_OR_PingTimer = null;
            }
            Log.d(TAG, "failed to establish connection after " + retryAttempts.get() + " attempts giving up");
            return;
        }
        if (reconnect_OR_PingTimer == null) {
            reconnect_OR_PingTimer = new Timer("reconnect or ping timer", true);
        }
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                reconnecting.set(false);
                if (!CLIENT.connected()) {
                    CLIENT.connect();
                }
            }
        };
        long delay = reconnectionDelay.addAndGet(getNextDelay(reconnectionDelay.get()));
        Log.d(TAG, retryAttempts.get() + " attempt(s). retrying after " + delay + "ms");
        reconnect_OR_PingTimer.schedule(task, delay);
    }

    private AtomicLong reconnectionDelay = new AtomicLong(30 * 1000); //30 seconds
    private AtomicBoolean reconnecting = new AtomicBoolean(false);

    public String getEndPoint() {
        return endPoint;
    }
}