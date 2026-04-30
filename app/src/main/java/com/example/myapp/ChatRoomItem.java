package com.example.myapp;

import java.util.List;

public class ChatRoomItem {
    private String roomId;
    private List<String> participants;
    private String lastMessage;
    private long lastTimestamp;

    // 읽지 않은 메시지 수
    private int unreadCount;

    // 상대(대화 상대) 정보 — 추가됨
    private String peerNickname;
    private String peerProfileImage;

    public ChatRoomItem() {}

    // ===== Getter / Setter =====
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(long lastTimestamp) { this.lastTimestamp = lastTimestamp; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    // ===== 추가된 상대 정보 =====
    public String getPeerNickname() { return peerNickname; }
    public void setPeerNickname(String peerNickname) { this.peerNickname = peerNickname; }

    public String getPeerProfileImage() { return peerProfileImage; }
    public void setPeerProfileImage(String peerProfileImage) { this.peerProfileImage = peerProfileImage; }
}
