package com.example.myapp;

import java.util.Map;

public class ChatMessage {
    private String senderId;
    private String content;
    private String messageType;
    private long timestamp;
    private String teamId;

    private boolean responded;   // 수락/거절 여부
    private String messageId;    // Firestore 메시지 문서 ID

    // ✅ 추가: 구조화 메타 (예: match_accept_notice)
    private Map<String, Object> meta;

    public ChatMessage() {}

    public String getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public String getMessageType() {
        return messageType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTeamId() {
        return teamId;
    }

    public boolean isResponded() {
        return responded;
    }

    public void setResponded(boolean responded) {
        this.responded = responded;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    // ✅ meta getter/setter
    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }
}
