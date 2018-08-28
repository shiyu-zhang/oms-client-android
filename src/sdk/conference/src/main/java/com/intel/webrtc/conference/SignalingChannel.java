/*
 * Intel License Header Holder
 */
package com.intel.webrtc.conference;

import static com.intel.webrtc.base.CheckCondition.DCHECK;

import android.util.Base64;

import com.intel.webrtc.base.IcsConst;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter.Listener;

final class SignalingChannel {

    private SignalingChannelObserver observer;
    // Base64 encoded token.
    private final String token;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final int MAX_RECONNECT_ATTEMPTS = 5;
    private String reconnectionTicket;
    private int reconnectAttempts = 0;
    // No lock is guarding loggedIn so void access and modify it on threads other than |executor|.
    private boolean loggedIn = false;
    private Socket socketClient;
    // [{'name': name, 'msg': message, 'ack': ack}]
    private final ArrayList<HashMap<String, Object>> cache = new ArrayList<>();

    // Socket.IO events.
    private final Listener connectedCallback = args -> executor.execute(() -> {
        if (loggedIn) {
            relogin();
        } else {
            try {
                login();
            } catch (JSONException e) {
                observer.onRoomConnectFailed(e.getMessage());
            }
        }
    });
    private final Listener connectErrorCallback = (Object... args) -> executor.execute(() -> {
        String msg = extractMsg(0, args);
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            if (loggedIn) {
                triggerDisconnected();
            } else {
                observer.onRoomConnectFailed("Socket.IO connected failed: " + msg);
            }
        }
    });
    private final Listener reconnectingCallback = args -> executor.execute(() -> {
        reconnectAttempts++;
        // trigger onReconnecting, ONLY when already logged in and first time to reconnect.
        if (loggedIn && reconnectAttempts == 1) {
            observer.onReconnecting();
        }
    });
    // disconnectCallback will be bound ONLY when disconnect() is called actively.
    private final Listener disconnectCallback = args -> triggerDisconnected();

    // MCU events.
    private final Listener progressCallback = (Object... args) -> executor.execute(() -> {
        JSONObject msg = (JSONObject) args[0];
        observer.onProgressMessage(msg);
    });
    private final Listener participantCallback = (Object... args) -> executor.execute(() -> {
        JSONObject msg = (JSONObject) args[0];
        try {
            switch (msg.getString("action")) {
                case "join":
                    observer.onParticipantJoined(msg.getJSONObject("data"));
                    break;
                case "leave":
                    observer.onParticipantLeft(msg.getString("data"));
                    break;
                default:
                    DCHECK(false);
            }
        } catch (JSONException e) {
            DCHECK(e);
        }
    });
    private final Listener streamCallback = (Object... args) -> executor.execute(() -> {
        try {
            JSONObject msg = (JSONObject) args[0];
            String status = msg.getString("status");
            String streamId = msg.getString("id");
            switch (status) {
                case "add":
                    JSONObject data = msg.getJSONObject("data");
                    RemoteStream remoteStream = new RemoteStream(data);
                    observer.onStreamAdded(remoteStream);
                    break;
                case "remove":
                    observer.onStreamRemoved(streamId);
                    break;
                case "update":
                    observer.onStreamUpdated(streamId, msg.getJSONObject("data"));
                    break;
                default:
                    DCHECK(false);
            }

        } catch (JSONException e) {
            DCHECK(e);
        }
    });
    private final Listener textCallback = (Object... args) -> executor.execute(() -> {
        JSONObject data = (JSONObject) args[0];
        try {
            observer.onTextMessage(data.getString("from"), data.getString("message"));
        } catch (JSONException e) {
            DCHECK(false);
        }
    });
    private final Listener dropCallback = args -> {
        // TODO: currently left empty.
    };

    SignalingChannel(String token, SignalingChannelObserver observer) {
        this.token = token;
        this.observer = observer;
    }

    void connect(final ConferenceClientConfiguration configuration) {
        DCHECK(executor);
        executor.execute(() -> {
            try {
                DCHECK(token);
                JSONObject jsonToken = new JSONObject(
                        new String(Base64.decode(token, Base64.DEFAULT)));

                boolean isSecure = jsonToken.getBoolean("secure");
                String host = jsonToken.getString("host");
                final String url = (isSecure ? "https" : "http") + "://" + host;

                IO.Options opt = new IO.Options();
                opt.forceNew = true;
                opt.reconnection = true;
                opt.reconnectionAttempts = MAX_RECONNECT_ATTEMPTS;
                opt.secure = isSecure;
                if (configuration.sslContext != null) {
                    opt.sslContext = configuration.sslContext;
                }
                if (configuration.hostnameVerifier != null) {
                    opt.hostnameVerifier = configuration.hostnameVerifier;
                }

                socketClient = IO.socket(url, opt);

                // Do not listen EVENT_DISCONNECT event on this phase.
                socketClient.on(Socket.EVENT_CONNECT, connectedCallback)
                        .on(Socket.EVENT_CONNECT_ERROR, connectErrorCallback)
                        .on(Socket.EVENT_RECONNECTING, reconnectingCallback)
                        .on("progress", progressCallback)
                        .on("participant", participantCallback)
                        .on("stream", streamCallback)
                        .on("text", textCallback)
                        .on("drop", dropCallback);
                socketClient.connect();

            } catch (JSONException e) {
                observer.onRoomConnectFailed(e.getMessage());
            } catch (URISyntaxException e) {
                observer.onRoomConnectFailed(e.getMessage());
            }
        });
    }

    void disconnect() {
        if (socketClient != null) {
            socketClient.on(Socket.EVENT_DISCONNECT, disconnectCallback);
            socketClient.disconnect();
        }
    }

    void sendMsg(String type, JSONObject msg, Ack ack) {
        if (!socketClient.connected()) {
            HashMap<String, Object> msg2cache = new HashMap<>();
            msg2cache.put("type", type);
            msg2cache.put("msg", msg);
            msg2cache.put("ack", ack);
            cache.add(msg2cache);
        } else {
            if (msg != null) {
                socketClient.emit(type, msg, ack);
            } else {
                socketClient.emit(type, ack);
            }
        }
    }

    private void login() throws JSONException {
        JSONObject loginInfo = new JSONObject();
        loginInfo.put("token", token);
        loginInfo.put("userAgent", new JSONObject(IcsConst.userAgent));
        loginInfo.put("protocol", IcsConst.PROTOCOL_VERSION);

        socketClient.emit("login", loginInfo, (Ack) (Object... args) -> executor.execute(() -> {
            if (extractMsg(0, args).equals("ok")) {
                loggedIn = true;
                try {
                    reconnectionTicket = ((JSONObject) args[1]).getString("reconnectionTicket");
                } catch (JSONException e) {
                    DCHECK(e);
                }
                observer.onRoomConnected((JSONObject) args[1]);
            } else {
                observer.onRoomConnectFailed(extractMsg(1, args));
            }

        }));
    }

    private void relogin() {
        DCHECK(reconnectionTicket);
        socketClient.emit("relogin", reconnectionTicket, (Ack) (Object... args) -> {
            if (extractMsg(0, args).equals("ok")) {
                try {
                    reconnectionTicket = ((JSONObject) args[1]).getString("reconnectionTicket");
                    reconnectAttempts = 0;
                } catch (JSONException e) {
                    DCHECK(e);
                }
                flushCachedMsg();
            } else {
                triggerDisconnected();
            }
        });
    }

    private void flushCachedMsg() {
        for (HashMap<String, Object> msg : cache) {
            try {
                sendMsg((String) msg.get("name"), (JSONObject) msg.get("msg"),
                        (Ack) msg.get("ack"));
            } catch (Exception exception) {
                DCHECK(exception);
            }

        }
        cache.clear();
    }

    private void triggerDisconnected() {
        loggedIn = false;
        reconnectAttempts = 0;
        cache.clear();
        observer.onRoomDisconnected();
    }

    private String extractMsg(int position, Object... args) {
        if (position < 0 || args == null || args.length < position + 1
                || args[position] == null) {
            DCHECK(false);
            return "";
        }
        return args[position].toString();
    }

    interface SignalingChannelObserver {

        void onRoomConnected(JSONObject info);

        void onRoomConnectFailed(String errorMsg);

        void onReconnecting();

        void onRoomDisconnected();

        void onProgressMessage(JSONObject message);

        void onTextMessage(String participantId, String message);

        void onStreamAdded(RemoteStream remoteStream);

        void onStreamRemoved(String streamId);

        void onStreamUpdated(String id, JSONObject updateInfo);

        void onParticipantJoined(JSONObject participantInfo);

        void onParticipantLeft(String participantId);
    }

}

