package com.pairapp.messenger;

import com.pairapp.util.EventBus;
import com.pairapp.util.GenericUtils;

/**
 * @author aminu on 7/1/2016.
 */
public class MessengerBus {

    public static final String PAIRAPP_CLIENT_IN_BUS = "pairappclienin", PAIRAPP_CLIENT_OUT_BUS = "pairappclientout";

    public static final String SEND_MESSAGE = "sendMessage";
    public static final String CANCEL_MESSAGE_DISPATCH = "cancelMessageDispatch";
    public static final String REGISTER_NOTIFIER = "registerNotifier";
    public static final String DE_REGISTER_NOTIFIER = "deRegisterNotifier";
    public static final String MESSAGE_SEEN = "messageSeen";
    public static final String MESSAGE_RECEIVED = "messageReceived";
    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";
    public static final String TYPING = "typing";
    public static final String NOT_TYPING = "notTyping";
    public static final String ANNOUNCE_ONLINE = "announceOnline";
    public static final String ANNOUNCE_TYPING = "announceTyping";
    public static final String ON_USER_ONLINE = "onUserOnline";
    public static final String ON_USER_OFFLINE = "onUserOffline";
    public static final String ON_USER_STOP_TYPING = "onUserStopTyping";
    public static final String ON_USER_TYPING = "onUserTyping";
    public static final String ON_MESSAGE_DELIVERED = "onMessageDelivered";
    public static final String ON_MESSAGE_SEEN = "onMessageSeen";
    public static final String INCOMING_MESSAGE = "incomingMessage";
    public static final String START_MONITORING_USER = "startMonitoringUser";
    public static final String STOP_MONITORING_USER = "stopMonitoringUser";

    public static EventBus get(String bus) {
        GenericUtils.ensureNotNull(bus);
        switch (bus) {
            case PAIRAPP_CLIENT_OUT_BUS:
//                return PairAppClient.getOutBuz();
            case PAIRAPP_CLIENT_IN_BUS:
//                return PairAppClient.getInBuz();
            default:
                throw new AssertionError();
        }
    }
}
