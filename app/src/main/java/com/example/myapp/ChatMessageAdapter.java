package com.example.myapp;

import android.content.Context;
import android.os.Build;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> messages;
    private final String myUid;
    private final String roomId;
    private final Context context;

    private static final int TYPE_MY_TEXT = 1;
    private static final int TYPE_OTHER_TEXT = 0;

    // ✅ 내 팀 ID (ChatRoom에서 세팅)
    private String myTeamId = null;
    public void setMyTeamId(String myTeamId) { this.myTeamId = myTeamId; }

    // 간단 캐시(중복 조회 방지)
    private final Map<String, ProfileLite> profileCache = new HashMap<>();

    private static class ProfileLite {
        String nickname;
        String imageUrl;
        ProfileLite(String n, String u) { nickname = n; imageUrl = u; }
    }

    public ChatMessageAdapter(List<ChatMessage> messages, String myUid, String roomId, Context context) {
        this.messages = messages;
        this.myUid = myUid;
        this.roomId = roomId;
        this.context = context;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        return msg.getSenderId().equals(myUid) ? TYPE_MY_TEXT : TYPE_OTHER_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MY_TEXT) {
            View v = inflater.inflate(R.layout.chat_item_right, parent, false);
            return new TextViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.chat_item_left, parent, false);
            return new TextViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        TextViewHolder h = (TextViewHolder) holder;

        // ✅ 메타가 있는 수락 알림이면 ‘내팀 vs 상대팀’ 관점으로 재조립
        String displayText = tryFormatByMeta(msg);
        if (displayText == null || displayText.trim().isEmpty()) {
            displayText = msg.getContent();
        }

        h.message.setText(displayText);
        h.time.setText(formatTime(msg.getTimestamp()));

        // 말풍선 최대폭
        int screenPx = holder.itemView.getResources().getDisplayMetrics().widthPixels;
        float ratio = (getItemViewType(position) == TYPE_OTHER_TEXT) ? 0.68f : 0.72f;
        h.message.setMaxWidth((int) (screenPx * ratio));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            h.message.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        }

        // 상대 메시지면 프로필/닉네임 세팅
        if (getItemViewType(position) == TYPE_OTHER_TEXT) {
            bindProfileFor(h, msg.getSenderId());
        } else {
            if (h.nickname != null) h.nickname.setVisibility(View.GONE);
            if (h.profile != null)  h.profile.setVisibility(View.GONE);
        }

        // 초대 버튼(옵션)
        setVisible(h.inviteButtons, false);
        setVisible(h.btnAccept, false);
        setVisible(h.btnReject, false);

        boolean canShowInvite = "invite".equals(msg.getMessageType())
                && !msg.getSenderId().equals(myUid)
                && !msg.isResponded();

        if (canShowInvite && h.inviteButtons != null && h.btnAccept != null && h.btnReject != null) {
            setVisible(h.inviteButtons, true);
            setVisible(h.btnAccept, true);
            setVisible(h.btnReject, true);

            h.btnAccept.setOnClickListener(v -> {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                String teamId = msg.getTeamId();

                if (teamId == null || teamId.isEmpty()) {
                    Toast.makeText(context, "팀 정보가 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                db.collection("profiles").document(uid).get().addOnSuccessListener(profile -> {
                    if (profile.contains("myTeam") &&
                            profile.getString("myTeam") != null &&
                            !profile.getString("myTeam").isEmpty()) {
                        sendSystemMessage("⚠️ 이미 팀에 가입되어 있어 초대를 수락할 수 없습니다.");
                    } else {
                        db.collection("teams").document(teamId)
                                .update("members", com.google.firebase.firestore.FieldValue.arrayUnion(uid));
                        db.collection("profiles").document(uid)
                                .update("myTeam", teamId);
                        sendSystemMessage("초대를 수락했습니다.");
                    }
                    db.collection("chatRooms").document(roomId)
                            .collection("messages").document(msg.getMessageId())
                            .update("responded", true);

                    setVisible(h.inviteButtons, false);
                    setVisible(h.btnAccept, false);
                    setVisible(h.btnReject, false);
                });
            });

            h.btnReject.setOnClickListener(v -> {
                sendSystemMessage("초대를 거절했습니다.");
                FirebaseFirestore.getInstance()
                        .collection("chatRooms").document(roomId)
                        .collection("messages").document(msg.getMessageId())
                        .update("responded", true);

                setVisible(h.inviteButtons, false);
                setVisible(h.btnAccept, false);
                setVisible(h.btnReject, false);
            });
        }
    }

    // ✨ 메타 기반 포맷터: 내 팀 기준으로 ‘내팀 vs 상대팀’ + 각 줄 •로 구분
    private String tryFormatByMeta(ChatMessage msg) {
        Map<String, Object> meta = msg.getMeta();
        if (meta == null) return null;

        Object type = meta.get("type");
        if (!(type instanceof String)) return null;
        if (!"match_accept_notice".equals(type)) return null;

        String teamAId   = safe(meta.get("teamAId"));
        String teamAName = safe(meta.get("teamAName"));
        String teamBId   = safe(meta.get("teamBId"));
        String teamBName = safe(meta.get("teamBName"));
        String date      = safe(meta.get("date"));
        String time      = safe(meta.get("time"));
        String place     = safe(meta.get("place"));

        // 내 시점에서 왼쪽=내팀, 오른쪽=상대팀
        String left, right;
        if (myTeamId != null && myTeamId.equals(teamBId)) {
            left = teamBName; right = teamAName;
        } else {
            left = teamAName; right = teamBName;
        }

        return "✅ 매치가 수락되었습니다.\n" +
                "• 경기일시: " + date + " " + time + "\n" +
                "• 장소: " + place + "\n" +
                "• 매치업: " + left + " vs " + right;
    }

    private String safe(Object o){ return o == null ? "" : String.valueOf(o); }

    private void bindProfileFor(@NonNull TextViewHolder h, @NonNull String senderId) {
        if (h.nickname == null && h.profile == null) return;

        ProfileLite cached = profileCache.get(senderId);
        if (cached != null) {
            if (h.nickname != null) {
                h.nickname.setText(cached.nickname != null ? cached.nickname : "(알 수 없음)");
                h.nickname.setVisibility(View.VISIBLE);
            }
            if (h.profile != null) {
                h.profile.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(cached.imageUrl)
                        .circleCrop()
                        .placeholder(R.drawable.bg_circle_light)
                        .error(R.drawable.bg_circle_light)
                        .into(h.profile);
            }
            return;
        }

        FirebaseFirestore.getInstance().collection("profiles")
                .document(senderId)
                .get()
                .addOnSuccessListener(doc -> {
                    String nick = doc.getString("nickname");
                    String url  = doc.getString("profileImageUrl");
                    profileCache.put(senderId, new ProfileLite(nick, url));

                    if (h.nickname != null) {
                        h.nickname.setText(nick != null ? nick : "(알 수 없음)");
                        h.nickname.setVisibility(View.VISIBLE);
                    }
                    if (h.profile != null) {
                        h.profile.setVisibility(View.VISIBLE);
                        Glide.with(context)
                                .load(url)
                                .circleCrop()
                                .placeholder(R.drawable.bg_circle_light)
                                .error(R.drawable.bg_circle_light)
                                .into(h.profile);
                    }
                })
                .addOnFailureListener(e -> {
                    if (h.nickname != null) {
                        h.nickname.setText("(알 수 없음)");
                        h.nickname.setVisibility(View.VISIBLE);
                    }
                    if (h.profile != null) {
                        h.profile.setVisibility(View.VISIBLE);
                        h.profile.setImageResource(R.drawable.bg_circle_light);
                    }
                });
    }

    private void sendSystemMessage(String content) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> m = new HashMap<>();
        m.put("senderId", myUid);
        m.put("content", content);
        m.put("messageType", "text");
        m.put("timestamp", System.currentTimeMillis());

        db.collection("chatRooms").document(roomId)
                .collection("messages").add(m);
        db.collection("chatRooms").document(roomId)
                .update("lastMessage", content, "lastTimestamp", System.currentTimeMillis());
    }

    private void setVisible(View v, boolean visible) {
        if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class TextViewHolder extends RecyclerView.ViewHolder {
        TextView message, time;

        // 옵션(상대 메시지에만 존재)
        TextView nickname;
        ImageView profile;

        // 옵션(초대 버튼이 있는 레이아웃에서만 존재)
        Button btnAccept, btnReject;
        LinearLayout inviteButtons;

        TextViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.textMessage);
            time    = itemView.findViewById(R.id.textTime);

            nickname = itemView.findViewById(R.id.textNickname);
            profile  = itemView.findViewById(R.id.imageProfile);

            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
            inviteButtons = itemView.findViewById(R.id.inviteButtons);
        }
    }

    private String formatTime(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("a h:mm", Locale.KOREA);
        return sdf.format(date);
    }
}
