// src/main/java/com/example/myapp/Chat.java
package com.example.myapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class Chat extends Fragment {

    private RecyclerView recyclerChatList;
    private ChatRoomItemAdapter adapter;
    private final List<ChatRoomItem> chatRoomList = new ArrayList<>();

    private FirebaseFirestore db;
    private String currentUid;
    private ListenerRegistration roomsReg;  // 스냅샷 리스너 해제용

    // StateLayout
    private StateLayout state;
    private boolean firstResultHandled = false; // 첫 결과 전환 여부

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.chat, container, false);

        // StateLayout
        state = view.findViewById(R.id.stateLayout);
        state.showLoading(); // ✅ 진입 시 로딩

        recyclerChatList = view.findViewById(R.id.recyclerChatList);
        recyclerChatList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatRoomItemAdapter(chatRoomList, getContext(), this::onChatRoomClick);
        recyclerChatList.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        currentUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;

        if (currentUid == null) {
            CustomToast.error(requireContext(), "로그인 정보를 확인할 수 없어요.");
            state.showEmpty();
        } else {
            listenChatRooms();
        }
        return view;
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (roomsReg != null) { roomsReg.remove(); roomsReg = null; }
    }

    private void listenChatRooms() {
        if (roomsReg != null) roomsReg.remove();

        roomsReg = db.collection("chatRooms")
                .whereArrayContains("participants", currentUid)
                .orderBy("lastTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded()) return; // Fragment 분리 시 안전가드
                    if (e != null) {
                        Log.e("Chat", "채팅방 로딩 실패: " + e.getMessage());
                        if (!firstResultHandled) state.showEmpty();
                        return;
                    }
                    if (snap == null) {
                        if (!firstResultHandled) state.showEmpty();
                        return;
                    }

                    chatRoomList.clear();

                    // ✅ 모든 보조 쿼리(안읽음, 상대 프로필)를 한 바구니에 모아놓고
                    //    전부 끝난 뒤에만 CONTENT로 전환한다.
                    List<Task<?>> pendingTasks = new ArrayList<>();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ChatRoomItem item = doc.toObject(ChatRoomItem.class);
                        if (item == null) continue;
                        item.setRoomId(doc.getId());

                        // --- 상대(상대방 1명 기준) 프로필 정보 가져오기 ---
                        List<String> participants = (List<String>) doc.get("participants");
                        String peerUid = null;
                        if (participants != null) {
                            for (String p : participants) {
                                if (p != null && !p.equals(currentUid)) { peerUid = p; break; }
                            }
                        }
                        if (peerUid != null) {
                            Task<DocumentSnapshot> metaT = db.collection("profiles")
                                    .document(peerUid)
                                    .get()
                                    .addOnSuccessListener(p -> {
                                        if (p.exists()) {
                                            String nn = p.getString("nickname");
                                            String img = p.getString("profileImageUrl");
                                            // ChatRoomItem에 대응 필드/세터가 있어야 함 (없다면 추가)
                                            item.setPeerNickname(nn);
                                            item.setPeerProfileImage(img);
                                        }
                                    })
                                    .addOnFailureListener(ex -> {
                                        // 실패해도 크래시 막기
                                    });
                            pendingTasks.add(metaT);
                        }

                        // --- lastRead 기반 unread 계산 ---
                        long lastReadTs = 0L;
                        Map<String, Object> lastRead = (Map<String, Object>) doc.get("lastRead");
                        if (lastRead != null && lastRead.get(currentUid) instanceof Number) {
                            lastReadTs = ((Number) lastRead.get(currentUid)).longValue();
                        }

                        Task<QuerySnapshot> unreadT = db.collection("chatRooms").document(doc.getId())
                                .collection("messages")
                                .whereGreaterThan("timestamp", lastReadTs)
                                .orderBy("timestamp", Query.Direction.ASCENDING)
                                .get()
                                .addOnSuccessListener(qs -> {
                                    int cnt = 0;
                                    for (DocumentSnapshot m : qs.getDocuments()) {
                                        String sender = m.getString("senderId");
                                        if (sender != null && !sender.equals(currentUid)) cnt++;
                                    }
                                    item.setUnreadCount(cnt);
                                })
                                .addOnFailureListener(ex -> item.setUnreadCount(0));
                        pendingTasks.add(unreadT);

                        chatRoomList.add(item);
                    }

                    // 방이 아예 없으면 즉시 EMPTY
                    if (chatRoomList.isEmpty()) {
                        adapter.notifyDataSetChanged();
                        firstResultHandled = true;
                        state.showEmpty();
                        return;
                    }

                    // ✅ unread + 상대 프로필 등 모든 추가 작업이 끝난 뒤에만 표시
                    Tasks.whenAllComplete(pendingTasks).addOnCompleteListener(done -> {
                        if (!isAdded()) return;
                        adapter.notifyDataSetChanged();
                        if (!firstResultHandled) {
                            firstResultHandled = true;
                            state.showContent();
                        }
                    });
                });
    }

    private void onChatRoomClick(ChatRoomItem item) {
        Intent intent = new Intent(getContext(), ChatRoom.class);
        intent.putExtra("roomId", item.getRoomId());
        startActivity(intent);
    }
}
