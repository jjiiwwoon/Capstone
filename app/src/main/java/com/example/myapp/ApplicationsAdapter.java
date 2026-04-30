// src/main/java/com/example/myapp/ApplicationsAdapter.java
package com.example.myapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 신청 목록 어댑터
 * - 헤더(신청자목록 토글)에 NEW: 세션 기준(it.hasSessionNew)
 * - 신청자 행(카드) 우상단 NEW: 세션 동안만 유지(sessionNewApplicantKeys)
 * - 수락/거절 직후 즉시 UI 반영 + 번쩍 애니메이션
 */
public class ApplicationsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_MINE = 1;
    public static final int TYPE_APPLIED = 2;

    private static final int VT_MINE_MATCH = 10;
    private static final int VT_MINE_RECRUIT = 11;
    private static final int VT_APPLIED_MATCH = 12;
    private static final int VT_APPLIED_RECRUIT = 13;

    // 세션 동안 NEW 뱃지 대상(행) 키 세트 (postType:postId:applicantDocId)
    private java.util.Set<String> sessionNewApplicantKeys = java.util.Collections.emptySet();

    // 네트워크 성공 직후 “해당 행 번쩍” 표시용 큐
    private final java.util.Set<String> flashRows = new java.util.HashSet<>();

    // ===== 아이템/신청자 모델 =====
    public static class Item {
        public String postId;
        public String postType; // "match" | "recruit"
        public String teamLogoUrl, teamName, date, time, stadium;
        public int    skill = -1;

        public long   timestamp = 0L; // 작성시각(표시용)
        public long   matchTs   = 0L; // 정렬/보조용
        public String status;

        // 모집 추가 필드
        public List<Applicant> applicants = new ArrayList<>();
        public String recruitType;     // "regular" | "mercenary"
        public Integer skillMin;
        public Integer skillMax;
        public List<String> positions;

        // ✅ 세션 NEW(헤더 표시용)
        public boolean hasSessionNew = false;
    }

    public static class Applicant {
        public String applicantDocId;
        public String teamId;
        public String logoUrl;
        public String teamName;
        public String nickname;
        public int    skill = -1;
        public String applicantUserId;
        public String status;

        // 개인 프로필용
        public String profileImageUrl;
        public String position;

        // ✅ 신청 시각(밀리초) — 세션 NEW 계산용
        public long   timestamp = 0L;
    }

    // ===== 어댑터 상태 =====
    private final List<Item> items = new ArrayList<>();
    private int listMode = TYPE_MINE;

    public interface OnItemClickListener {
        default void onPostClicked(@NonNull Item item) {}
        default void onApplicantAccept(@NonNull Item post, @NonNull Applicant applicant) {}
        default void onApplicantReject(@NonNull Item post, @NonNull Applicant applicant) {}
        default void onApplicantChat(@NonNull Item post, @NonNull Applicant applicant) {}
    }
    private OnItemClickListener listener;
    public void setOnItemClickListener(OnItemClickListener cb){ this.listener = cb; }

    public void setItems(@NonNull List<Item> newItems, int mode) {
        items.clear();
        items.addAll(newItems);
        listMode = mode;
        notifyDataSetChanged();
    }

    /** 세션 NEW(행) 키 세트 주입 */
    public void setSessionNewApplicantKeys(@NonNull java.util.Set<String> keys) {
        this.sessionNewApplicantKeys = new HashSet<>(keys);
        notifyDataSetChanged();
    }

    /** 네트워크 성공 직후, 특정 신청자의 상태를 즉시 반영하고 재바인딩 */
    public void updateApplicantStatus(@NonNull String postId,
                                      @NonNull String applicantDocId,
                                      @NonNull String newStatus) {
        boolean changed = false;
        for (Item it : items) {
            if (!postId.equals(it.postId)) continue;
            if (it.applicants == null) break;
            for (Applicant a : it.applicants) {
                if (applicantDocId.equals(a.applicantDocId)) {
                    a.status = newStatus.toLowerCase(Locale.ROOT);
                    // 행 플래시 표시 등록
                    markFlash(it.postType, it.postId, a.applicantDocId);
                    changed = true;
                    break;
                }
            }
            break;
        }
        if (changed) notifyDataSetChanged();
    }

    private void markFlash(String postType, String postId, String applicantDocId) {
        String t = (postType == null) ? "" : postType.toLowerCase(Locale.ROOT);
        String p = (postId == null) ? "" : postId;
        String a = (applicantDocId == null) ? "" : applicantDocId;
        flashRows.add(t + ":" + p + ":" + a);
    }

    private static void flash(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0.35f);
        v.animate().alpha(1f).setDuration(140).start(); // 짧게 번쩍
    }

    // ===== 기본 어댑터 구현 =====
    @Override public int getItemCount(){ return items.size(); }

    @Override public int getItemViewType(int position) {
        Item it = items.get(position);
        boolean isRecruit = "recruit".equalsIgnoreCase(it.postType);
        if (listMode == TYPE_MINE) return isRecruit ? VT_MINE_RECRUIT : VT_MINE_MATCH;
        else                       return isRecruit ? VT_APPLIED_RECRUIT : VT_APPLIED_MATCH;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (vt == VT_MINE_MATCH) {
            return new MineVH(inf.inflate(R.layout.applications_item_mine, parent, false));
        } else if (vt == VT_MINE_RECRUIT) {
            return new MineVH(inf.inflate(R.layout.applications_item_mine_recruit, parent, false));
        } else if (vt == VT_APPLIED_MATCH) {
            return new AppliedVH(inf.inflate(R.layout.applications_item_applied, parent, false));
        } else { // VT_APPLIED_RECRUIT
            return new AppliedVH(inf.inflate(R.layout.applications_item_applied_recruit, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        Item it = items.get(pos);
        if (h instanceof MineVH) {
            ((MineVH) h).setSessionNewKeys(sessionNewApplicantKeys);
            ((MineVH) h).bind(it, listener);
        } else if (h instanceof AppliedVH) {
            ((AppliedVH) h).bind(it, listener);
        }
    }

    // ===== 공통 카드 바인딩 =====
    private static void bindCommonPost(@NonNull View includePost, @NonNull Item it, OnItemClickListener cb) {
        TextView tvTeamName  = includePost.findViewById(R.id.textTeamName);
        TextView tvDate      = includePost.findViewById(R.id.textDate);
        TextView tvTime      = includePost.findViewById(R.id.textTime);
        TextView tvStadium   = includePost.findViewById(R.id.textStadium);
        ImageView ivLogo     = includePost.findViewById(R.id.imageTeamLogo);
        TextView tvTimestamp = includePost.findViewById(R.id.textTimestamp);

        if (tvTeamName != null) tvTeamName.setText(ns(it.teamName));
        if (tvDate != null)     tvDate.setText(ns(it.date));
        if (tvTime != null)     tvTime.setText(ns(it.time));
        if (tvStadium != null)  tvStadium.setText("주소 | " + ns(it.stadium));

        if (ivLogo != null) {
            if (!isEmpty(it.teamLogoUrl)) Glide.with(ivLogo.getContext()).load(it.teamLogoUrl).into(ivLogo);
            else ivLogo.setImageResource(R.drawable.ic_launcher_foreground);
        }

        // 작성시각: 1) it.timestamp > 2) matchTs > 3) date+time 파싱
        if (tvTimestamp != null) {
            long ts = (it.timestamp > 0) ? it.timestamp
                    : (it.matchTs > 0 ? it.matchTs
                    : computeTsSafe(ns(it.date), ns(it.time)));
            tvTimestamp.setText(formatTimestamp(ts));
        }

        // 매치/모집 전용
        TextView tvSkill = includePost.findViewById(R.id.textSkill);
        TextView tvRecruitType = includePost.findViewById(R.id.textRecruitType);
        ChipGroup chipGroup = includePost.findViewById(R.id.chipGroupPositions);
        boolean isRecruit = "recruit".equalsIgnoreCase(it.postType);

        if (isRecruit) {
            if (tvRecruitType != null) {
                boolean isMercenary = "mercenary".equalsIgnoreCase(ns(it.recruitType)) || "용병".equals(ns(it.recruitType));
                String label = isMercenary ? "용병" : "회원";
                tvRecruitType.setText(label);

                tvRecruitType.setBackgroundResource(
                        isMercenary ? R.drawable.bg_badge_mercenary : R.drawable.bg_badge_member
                );
                tvRecruitType.setTextColor(0xFFFFFFFF); // 가독성 유지
            }

            if (chipGroup != null) {
                chipGroup.removeAllViews();
                if (it.positions != null) {
                    for (String p : it.positions) {
                        if (isEmpty(p)) continue;
                        Chip c = new Chip(includePost.getContext());
                        c.setText(p); c.setClickable(false); c.setCheckable(false);
                        chipGroup.addView(c);
                    }
                }
            }
            if (tvSkill != null) {
                String l = it.skillMin==null?"-":String.valueOf(it.skillMin);
                String r = it.skillMax==null?"-":String.valueOf(it.skillMax);
                tvSkill.setText(String.format(Locale.getDefault(), "실력 : %s ~ %s", l, r));
            }
        } else {
            if (tvSkill != null) tvSkill.setText(String.format(Locale.getDefault(), "실력 : %d", it.skill < 0 ? 0 : it.skill));
        }

        View rootClickable = includePost.findViewById(R.id.itemRoot);
        if (rootClickable == null) rootClickable = includePost;
        if (rootClickable != null && cb != null) rootClickable.setOnClickListener(v -> cb.onPostClicked(it));
    }

    private static String ns(String s){ return s==null?"":s; }
    private static boolean isEmpty(String s){ return s==null||s.trim().isEmpty(); }

    // ===== 시간 유틸 =====
    private static long computeTsSafe(String date, String time) {
        try {
            String startTime = extractStartTime(time);
            final String val = isEmpty(startTime) ? date : (date + " " + startTime);
            final String pattern = isEmpty(startTime) ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm";
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
            Date d = sdf.parse(val);
            return (d != null) ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
    private static String extractStartTime(String raw) {
        if (isEmpty(raw)) return "";
        String s = raw.trim();
        String firstPart = s.split("[~\\-–—]")[0].trim();

        if (firstPart.contains("오전") || firstPart.contains("오후")) {
            try {
                String normalized = firstPart.replaceAll("\\s+", " ")
                        .replace("오전", "AM")
                        .replace("오후", "PM");
                SimpleDateFormat ap = new SimpleDateFormat("a H:mm", Locale.getDefault());
                Date d = ap.parse(normalized);
                SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return hhmm.format(d);
            } catch (Exception ignored) { }
        }
        String upper = firstPart.toUpperCase(Locale.ROOT);
        if (upper.contains("AM") || upper.contains("PM")) {
            try {
                SimpleDateFormat ap = new SimpleDateFormat("h:mm a", Locale.US);
                Date d = ap.parse(upper);
                SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return hhmm.format(d);
            } catch (Exception ignored) { }
        }
        Matcher m = Pattern.compile("(\\d{1,2}:\\d{2})").matcher(firstPart);
        if (m.find()) {
            String hhmm = m.group(1);
            if (hhmm.length() == 4) hhmm = "0" + hhmm;
            return hhmm;
        }
        m = Pattern.compile("\\b(\\d{3,4})\\b").matcher(firstPart);
        if (m.find()) {
            String digits = m.group(1);
            if (digits.length() == 3) digits = "0" + digits;
            return digits.substring(0, 2) + ":" + digits.substring(2);
        }
        return "";
    }
    private static String formatTimestamp(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        if (diff < 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        if (minutes < 1) return "방금 전";
        else if (minutes < 60) return minutes + "분 전";
        else if (hours < 24)  return hours + "시간 전";
        else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    /** 행 키 생성: postType/postId/applicantDocId */
    private static String buildApplicantKey(String postType, String postId, String applicantDocId) {
        String t = (postType == null) ? "" : postType.toLowerCase(Locale.ROOT);
        String p = (postId == null) ? "" : postId;
        String a = (applicantDocId == null) ? "" : applicantDocId;
        return t + ":" + p + ":" + a;
    }

    // ===== Mine (신청자 토글) =====
    // ⚠️ Non-static 으로 변경해서 어댑터 인스턴스(flashRows 등)에 접근 가능
    public class MineVH extends RecyclerView.ViewHolder {
        final View includePost, toggleHeader;
        final LinearLayout applicantsContainer;
        final ImageView iconArrow;
        TextView txtCount;   // 신청자 수 배지
        TextView txtNew;     // 헤더 NEW(세션)
        boolean expanded = false;

        private java.util.Set<String> sessionNewKeys = java.util.Collections.emptySet();

        public MineVH(@NonNull View v) {
            super(v);
            includePost = v.findViewById(R.id.includePost);
            toggleHeader = v.findViewById(R.id.includeToggleHeader);
            applicantsContainer = v.findViewById(R.id.applicantsContainer);
            iconArrow = (toggleHeader != null) ? toggleHeader.findViewById(R.id.iconToggleArrow) : null;
            if (toggleHeader != null) {
                txtCount = toggleHeader.findViewById(R.id.textApplicantCount);
                txtNew   = toggleHeader.findViewById(R.id.textApplicantNew);
            }
        }

        void setSessionNewKeys(java.util.Set<String> keys) {
            this.sessionNewKeys = (keys == null) ? java.util.Collections.emptySet() : keys;
        }

        void bind(@NonNull Item it, OnItemClickListener cb) {
            if (includePost != null) bindCommonPost(includePost, it, cb);

            final int total = (it.applicants == null) ? 0 : it.applicants.size();
            if (txtCount != null) txtCount.setText(String.valueOf(total));

            if (txtNew != null) txtNew.setVisibility(it.hasSessionNew ? View.VISIBLE : View.GONE);

            if (toggleHeader != null) {
                toggleHeader.setOnClickListener(v -> {
                    expanded = !expanded;
                    if (iconArrow != null) iconArrow.setRotation(expanded ? 180f : 0f);
                    if (applicantsContainer != null) {
                        applicantsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
                        if (expanded) {
                            rebuildChildren(applicantsContainer, it, cb);
                        } else {
                            applicantsContainer.removeAllViews();
                        }
                    }
                });
            }

            // 최초 바인딩 시 펼쳐져 있으면 즉시 렌더링
            if (applicantsContainer != null) {
                applicantsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
                if (expanded) {
                    rebuildChildren(applicantsContainer, it, cb);
                } else {
                    applicantsContainer.removeAllViews();
                }
            }
        }

        /** 신청자 리스트 뷰를 현재 데이터로 다시 구성 */
        private void rebuildChildren(@NonNull LinearLayout container,
                                     @NonNull Item it,
                                     OnItemClickListener cb) {
            container.removeAllViews();
            LayoutInflater inf = LayoutInflater.from(container.getContext());
            boolean isRecruit = "recruit".equalsIgnoreCase(it.postType);

            for (Applicant a : it.applicants) {
                View row = inf.inflate(
                        isRecruit ? R.layout.applicant_person_item : R.layout.applicant_item,
                        container, false
                );

                String rowKey = buildApplicantKey(it.postType, it.postId, a.applicantDocId);
                row.setTag(rowKey);

                // NEW/시간 배지
                TextView badgeNewRow = row.findViewById(R.id.badgeNewRow);
                if (badgeNewRow != null) {
                    boolean rowIsNew = isRowNew(it.postType, it.postId, a.applicantDocId);
                    long ts = a.timestamp;
                    if (rowIsNew) {
                        badgeNewRow.setVisibility(View.VISIBLE);
                        badgeNewRow.setText("NEW");
                        badgeNewRow.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
                    } else if (ts > 0L) {
                        badgeNewRow.setVisibility(View.VISIBLE);
                        badgeNewRow.setText(formatTimestamp(ts));
                        badgeNewRow.setTextColor(android.graphics.Color.parseColor("#666666"));
                    } else {
                        badgeNewRow.setVisibility(View.GONE);
                    }
                }

                // 공통 컨트롤
                View btnAccept = row.findViewById(R.id.btnAccept);
                View btnReject = row.findViewById(R.id.btnReject);
                TextView txtStatus = row.findViewById(R.id.textApplicantStatus);

                // 상태 텍스트 & 버튼 가시성(수락됨/거절됨 즉시 반영)
                Runnable applyStatusUi = () -> {
                    String st = a.status == null ? "pending" : a.status.toLowerCase(Locale.ROOT);
                    boolean pending = !"accepted".equals(st) && !"rejected".equals(st);

                    if (txtStatus != null) {
                        if ("accepted".equals(st)) {
                            txtStatus.setVisibility(View.VISIBLE);
                            txtStatus.setText("수락됨");
                            txtStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32")); // win
                        } else if ("rejected".equals(st)) {
                            txtStatus.setVisibility(View.VISIBLE);
                            txtStatus.setText("거절됨");
                            txtStatus.setTextColor(android.graphics.Color.parseColor("#B71C1C")); // loss
                        } else {
                            txtStatus.setVisibility(View.GONE);
                        }
                    }
                    if (btnAccept != null) btnAccept.setVisibility(pending ? View.VISIBLE : View.GONE);
                    if (btnReject != null) btnReject.setVisibility(pending ? View.VISIBLE : View.GONE);
                };
                applyStatusUi.run();

                // 타입별 바인딩
                if (isRecruit) {
                    ImageView ivProfile = row.findViewById(R.id.imageProfile);
                    TextView tNick = row.findViewById(R.id.textNickname);
                    TextView tSkill= row.findViewById(R.id.textSkill);
                    TextView tPos  = row.findViewById(R.id.textPosition);
                    Button btnChat = row.findViewById(R.id.btnChat);

                    if (ivProfile != null) {
                        String src = (a.profileImageUrl != null && !a.profileImageUrl.trim().isEmpty())
                                ? a.profileImageUrl : a.logoUrl;
                        Glide.with(ivProfile.getContext())
                                .load(src)
                                .placeholder(R.mipmap.ic_launcher)
                                .centerCrop()
                                .into(ivProfile);
                    }
                    if (tNick != null) tNick.setText(ns(a.nickname));
                    if (tSkill != null) tSkill.setText("실력: " + (a.skill < 0 ? "-" : a.skill));
                    if (tPos != null) tPos.setText("포지션: " + (isEmpty(a.position) ? "-" : a.position));

                    // (용병/정식 구분 로직은 기존과 동일하게 사용)
                } else {
                    ImageView ivLogo = row.findViewById(R.id.imageTeamLogo);
                    TextView tTeam = row.findViewById(R.id.textApplicantTeam);
                    TextView tNick = row.findViewById(R.id.textApplicantNickname);
                    TextView tSkill= row.findViewById(R.id.textApplicantSkill);

                    if (tTeam != null) tTeam.setText(ns(a.teamName));
                    if (tNick != null) tNick.setText(ns(a.nickname));
                    if (tSkill != null) tSkill.setText("실력: " + (a.skill < 0 ? "-" : a.skill));
                    if (ivLogo != null) {
                        Glide.with(ivLogo.getContext())
                                .load((a.logoUrl == null || a.logoUrl.trim().isEmpty()) ? null : a.logoUrl)
                                .placeholder(R.mipmap.ic_launcher)
                                .centerCrop()
                                .into(ivLogo);
                    }
                }

                // 버튼 클릭: 즉시 처리중 UI + 번쩍 효과 + 콜백
                if (btnAccept != null) {
                    btnAccept.setOnClickListener(v -> {
                        v.setEnabled(false);
                        flash(v);                           // ✅ 버튼만 번쩍
                        if (listener != null) listener.onApplicantAccept(it, a);
                    });
                }
                if (btnReject != null) {
                    btnReject.setOnClickListener(v -> {
                        v.setEnabled(false);
                        flash(v);                           // ✅ 버튼만 번쩍
                        if (listener != null) listener.onApplicantReject(it, a);
                    });
                }

                // 방금 업데이트 대상으로 표시되었다면 바인딩 직후 번쩍
                if (ApplicationsAdapter.this.flashRows.remove(rowKey)) {
                    flash(row);
                }

                container.addView(row);
            }
        }

        private boolean isRowNew(String postType, String postId, String applicantDocId) {
            if (sessionNewKeys == null || sessionNewKeys.isEmpty()) return false;
            String key = buildApplicantKey(postType, postId, applicantDocId);
            return sessionNewKeys.contains(key);
        }
    }

    // ===== Applied (상태 칩) =====
    public static class AppliedVH extends RecyclerView.ViewHolder {
        final View includePost;
        final TextView textStatus;

        public AppliedVH(@NonNull View v) {
            super(v);
            includePost = v.findViewById(R.id.includePost);
            textStatus = v.findViewById(R.id.textApplicationStatus);
        }

        void bind(@NonNull Item it, OnItemClickListener cb) {
            if (includePost != null) bindCommonPost(includePost, it, cb);
            if (textStatus != null) {
                String st = it.status == null ? "pending" : it.status.toLowerCase(Locale.ROOT);
                switch (st) {
                    case "accepted":
                        textStatus.setText("수락됨");
                        textStatus.setBackgroundResource(R.drawable.bg_status_chip_accepted);
                        break;
                    case "rejected":
                        textStatus.setText("거절됨");
                        textStatus.setBackgroundResource(R.drawable.bg_status_chip_rejected);
                        break;
                    default:
                        textStatus.setText("대기중");
                        textStatus.setBackgroundResource(R.drawable.bg_status_chip_pending);
                        break;
                }
            }
        }
    }
}
