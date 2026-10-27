package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;

/** One metadata row: it scrolls away, while the separate video surface stays pinned. */
public final class Nm7WatchHeaderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final View header;
    public Nm7WatchHeaderAdapter(View header) { this.header = header; }
    @Override public int getItemCount() { return 1; }
    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
        if (header.getParent() != null) ((ViewGroup) header.getParent()).removeView(header);
        header.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerView.ViewHolder(header) { };
    }
    @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) { }
}
