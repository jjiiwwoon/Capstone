package com.example.myapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ⚠️ Firestore/Auth 호출 제거!
 *  - 모든 데이터는 Chat.java에서 미리 세팅된 ChatRoomItem의 필드를 그대로 표시.
 *  - onBindViewHolder()에서는 네트워크 작업 하지 않음.
 */
public class ChatRoomItemAdapter extends RecyclerView.Adapter<ChatRoomItemAdapter.ViewHolder> {

    private final List<ChatRoomItem> chatRooms;
    private final Context context;
    private final OnChatClickListener listener;

    public interface OnChatClickListener { void onClick(ChatRoomItem item); }

    public ChatRoomItemAdapter(List<ChatRoomItem> chatRooms, Context context, OnChatClickListener listener) {
        this.chatRooms = chatRooms;
        this.context = context;
        this.listener = listener;
        setHasStableIds(true); // 스크롤 중 깜빡임/재바인딩 최소화
    }

    @Override public long getItemId(int position) {
        ChatRoomItem item = chatRooms.get(position);
        // roomId 기반 stable id
        return item.getRoomId() != null ? item.getRoomId().hashCode() : RecyclerView.NO_ID;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.chat_item, parent, false);
        return new ViewHolder(view);
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatRoomItem item = chatRooms.get(position);

        // 닉네임 / 마지막 메시지 / 시간
        holder.nickname.setText(
                isEmpty(item.getPeerNickname()) ? "(알 수 없음)" : item.getPeerNickname()
        );
        holder.lastMessage.setText(
                isEmpty(item.getLastMessage()) ? "" : item.getLastMessage()
        );
        holder.time.setText(formatTime(item.getLastTimestamp()));

        // 프로필 이미지 (Glide만 사용, 추가 네트워크 조회 X)
        String imageUrl = item.getPeerProfileImage();
        if (!isEmpty(imageUrl)) {
            Glide.with(context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_foreground) // 네가 쓰는 기본 아이콘/플레이스홀더로 교체 가능
                    .error(R.drawable.ic_launcher_foreground)
                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(dp(holder.itemView, 12))))
                    .into(holder.profile);
        } else {
            holder.profile.setImageResource(R.drawable.ic_launcher_foreground);
        }

        // 클릭
        holder.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override public int getItemCount() { return chatRooms.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView profile;
        TextView nickname, lastMessage, time;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            profile = itemView.findViewById(R.id.imageProfile);
            nickname = itemView.findViewById(R.id.textNickname);
            lastMessage = itemView.findViewById(R.id.textLastMessage);
            time = itemView.findViewById(R.id.textTime);
        }
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        Date date = new Date(timestamp);
        // 로컬 사용자가 한국이므로 Locale.KOREA 사용
        SimpleDateFormat sdf = new SimpleDateFormat("a h:mm", Locale.KOREA);
        return sdf.format(date);
    }

    private static int dp(View view, int v) {
        return Math.round(view.getResources().getDisplayMetrics().density * v);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
