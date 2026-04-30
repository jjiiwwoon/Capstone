package com.example.myapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRoom extends AppCompatActivity {

    private RecyclerView recyclerChat;
    private View chatRoot;
    private View inputBar;
    private EditText editMessage;
    private View btnSend;

    private ChatMessageAdapter adapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private String roomId;
    private String currentUid;
    private String myTeamId; // ✅ 내 팀 ID
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.chat_room);

        chatRoot     = findViewById(R.id.chatRoot);
        recyclerChat = findViewById(R.id.recyclerChat);
        inputBar     = findViewById(R.id.inputBar);
        editMessage  = findViewById(R.id.editMessage);
        btnSend      = findViewById(R.id.btnSend);

        WindowInsetsControllerCompat c = new WindowInsetsControllerCompat(getWindow(), chatRoot);
        c.setAppearanceLightStatusBars(true);
        c.setAppearanceLightNavigationBars(true);

        roomId     = getIntent().getStringExtra("roomId");
        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db         = FirebaseFirestore.getInstance();

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recyclerChat.setLayoutManager(lm);
        adapter = new ChatMessageAdapter(messageList, currentUid, roomId, this);
        recyclerChat.setAdapter(adapter);

        // ✅ 내 팀 ID 로드 → 어댑터에 전달
        db.collection("profiles").document(currentUid).get()
                .addOnSuccessListener(p -> {
                    myTeamId = p.getString("myTeam");
                    if (myTeamId != null) adapter.setMyTeamId(myTeamId);
                });

        ViewCompat.setOnApplyWindowInsetsListener(chatRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(inputBar, (v, insets) -> {
            Insets ime  = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomInset = Math.max(ime.bottom, bars.bottom);

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (lp.bottomMargin != bottomInset) {
                lp.bottomMargin = bottomInset;
                v.setLayoutParams(lp);
            }

            int baseBottom = dp(8);
            recyclerChat.setPadding(
                    recyclerChat.getPaddingLeft(),
                    recyclerChat.getPaddingTop(),
                    recyclerChat.getPaddingRight(),
                    baseBottom
            );
            return insets;
        });

        recyclerChat.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (b < ob) recyclerChat.post(this::scrollToBottom);
        });

        btnSend.setOnClickListener(v -> {
            String content = editMessage.getText().toString().trim();
            if (content.isEmpty()) return;
            blink(btnSend);
            sendMessage(content);
            editMessage.setText("");
        });

        editMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String content = editMessage.getText().toString().trim();
                if (content.isEmpty()) return true;
                blink(btnSend);
                sendMessage(content);
                editMessage.setText("");
                return true;
            }
            return false;
        });

        listenForMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        markRoomReadToLastMessage();
    }

    @Override
    protected void onPause() {
        super.onPause();
        markRoomReadToLastMessage();
    }

    private void listenForMessages() {
        db.collection("chatRooms").document(roomId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    boolean added = false;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            ChatMessage msg = dc.getDocument().toObject(ChatMessage.class);
                            msg.setMessageId(dc.getDocument().getId());

                            // ❌ meta 수동 추출/주입 금지(POJO 매핑으로 자동 주입됨)
                            // Map<String, Object> meta = dc.getDocument().get("meta", Map.class); // 크래시 원인
                            // if (meta != null) msg.setMeta(meta);

                            messageList.add(msg);
                            adapter.notifyItemInserted(messageList.size() - 1);
                            added = true;
                        }
                    }
                    if (added) {
                        scrollToBottom();
                        markRoomReadToLastMessage();
                    }
                });
    }

    private void sendMessage(String content) {
        long now = System.currentTimeMillis();

        Map<String, Object> message = new HashMap<>();
        message.put("senderId",    currentUid);
        message.put("content",     content);
        message.put("messageType", "text");
        message.put("timestamp",   now);

        db.collection("chatRooms").document(roomId)
                .collection("messages")
                .add(message)
                .addOnSuccessListener(d -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastMessage", content);
                    updates.put("lastTimestamp", now);
                    updates.put("lastRead." + currentUid, now);

                    db.collection("chatRooms").document(roomId)
                            .set(updates, SetOptions.merge());
                });
    }

    private void markRoomReadToLastMessage() {
        if (roomId == null || currentUid == null) return;

        long lastTs = getLastMessageTimestamp();
        if (lastTs <= 0L) {
            lastTs = System.currentTimeMillis();
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastRead." + currentUid, lastTs);

        db.collection("chatRooms").document(roomId)
                .set(updates, SetOptions.merge());
    }

    private long getLastMessageTimestamp() {
        if (messageList.isEmpty()) return 0L;
        ChatMessage last = messageList.get(messageList.size() - 1);
        return last != null ? last.getTimestamp() : 0L;
    }

    private void scrollToBottom() {
        recyclerChat.scrollToPosition(Math.max(0, messageList.size() - 1));
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void blink(View v) {
        v.animate()
                .alpha(0.5f)
                .setDuration(90)
                .withEndAction(() ->
                        v.animate()
                                .alpha(1f)
                                .setDuration(110)
                                .setListener(null)
                                .start()
                )
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) { /* no-op */ }
                })
                .start();
    }
}
