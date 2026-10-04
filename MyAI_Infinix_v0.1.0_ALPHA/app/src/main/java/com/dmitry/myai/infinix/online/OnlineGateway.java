package com.dmitry.myai.infinix.online;

public interface OnlineGateway {
    boolean isAvailable();

    void send(String sessionId, String userText, Callback callback);

    interface Callback {
        void onSuccess(String assistantText, String toolCallJson);
        void onFailure(String message);
    }
}
