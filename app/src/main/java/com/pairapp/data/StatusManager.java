package com.pairapp.data;

import com.pairapp.messenger.MessagePacker;
import com.pairapp.net.sockets.PairappSocket;

/**
 * @author aminu on 6/29/2016.
 */
public class StatusManager {

    private final PairappSocket pairappSocket;
    private volatile boolean isOnline = false;

    private final String currentUser;
    private final MessagePacker packer;

    private StatusManager(String currentUser, MessagePacker packer, PairappSocket pairappSocket) {
        this.currentUser = currentUser;
        this.packer = packer;
        this.pairappSocket = pairappSocket;
    }

    public static StatusManager create(String currentUser, MessagePacker packer, PairappSocket socket) {
        return new StatusManager(currentUser, packer, socket);
    }

    public void annouceStatusChange(boolean online) {
        if (this.isOnline == online) return;
        isOnline = online;

        byte[] payload = packer.createStatusMessage(isOnline);
        pairappSocket.send(payload);
    }
}
