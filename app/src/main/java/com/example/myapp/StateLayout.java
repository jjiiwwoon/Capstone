package com.example.myapp;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

public class StateLayout extends FrameLayout {

    public enum State { LOADING, CONTENT, EMPTY }

    private @LayoutRes int loadingLayoutRes = R.layout.view_state_loading;
    private @LayoutRes int emptyLayoutRes   = R.layout.view_state_empty;

    private View loadingView;
    private View emptyView;
    private View contentView; // 자식 중 "콘텐츠"로 간주할 하나

    private State current = State.CONTENT;

    public StateLayout(Context context) {
        super(context);
        init(null);
    }

    public StateLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public StateLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.StateLayout);
            int ll = a.getResourceId(R.styleable.StateLayout_loadingLayout, 0);
            int el = a.getResourceId(R.styleable.StateLayout_emptyLayout, 0);
            if (ll != 0) loadingLayoutRes = ll;
            if (el != 0) emptyLayoutRes = el;
            a.recycle();
        }
        // onFinishInflate에서 contentView를 찾아서 상태 반영
        post(this::ensureInflated);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ensureInflated();
    }

    private void ensureInflated() {
        if (contentView == null) {
            // 첫 번째 자식(혹은 id=contentGroup)을 콘텐츠로 간주
            if (getChildCount() > 0) {
                View first = getChildAt(0);
                contentView = first;
            }
        }
        if (loadingView == null) {
            loadingView = LayoutInflater.from(getContext()).inflate(loadingLayoutRes, this, false);
            addView(loadingView);
        }
        if (emptyView == null) {
            emptyView = LayoutInflater.from(getContext()).inflate(emptyLayoutRes, this, false);
            addView(emptyView);
        }
        applyState(false);
    }

    public void showLoading() { current = State.LOADING; applyState(true); }

    public void showContent() { current = State.CONTENT; applyState(true); }

    public void showEmpty() { current = State.EMPTY; applyState(true); }

    private void applyState(boolean animate) {
        if (loadingView == null || emptyView == null || contentView == null) return;

        View toShow, toHide1, toHide2;
        switch (current) {
            case LOADING:
                toShow = loadingView;  toHide1 = contentView; toHide2 = emptyView; break;
            case EMPTY:
                toShow = emptyView;    toHide1 = contentView; toHide2 = loadingView; break;
            default:
                toShow = contentView;  toHide1 = loadingView; toHide2 = emptyView; break;
        }

        if (animate) {
            toShow.setAlpha(0f);
            toShow.setVisibility(VISIBLE);
            toShow.animate().alpha(1f).setDuration(150).start();
            toHide1.setVisibility(GONE);
            toHide2.setVisibility(GONE);
        } else {
            toShow.setVisibility(VISIBLE);
            toHide1.setVisibility(GONE);
            toHide2.setVisibility(GONE);
        }
    }

    /** 빈 화면 문구 바꾸기 (view_state_empty.xml 기준) */
    public void setEmptyMessage(CharSequence msg) {
        if (emptyView != null) {
            View tv = emptyView.findViewById(R.id.txtEmptyMessage);
            if (tv instanceof android.widget.TextView) {
                ((android.widget.TextView) tv).setText(msg);
            }
        }
    }
}
