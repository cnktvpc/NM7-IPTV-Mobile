package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import androidx.recyclerview.widget.ConcatAdapter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import java.util.ArrayList;
import java.util.List;

/** Full-width feed, retaining a separate continuation and delta stream for each group. */
public final class Nm7FeedAdapter {
    public final ConcatAdapter adapter = new ConcatAdapter();
    private final VideoGroupAdapter.Listener listener;
    private final List<Entry> entries = new ArrayList<>();

    public Nm7FeedAdapter(VideoGroupAdapter.Listener listener) {
        this.listener = listener;
    }

    public void update(VideoGroup group) {
        if (group == null) return;
        int action = group.getAction();
        Entry entry = find(group.getId());
        if (action == VideoGroup.ACTION_REPLACE) {
            if (group.getPosition() == -1) clear();
            else if (entry != null) remove(entry);
            entry = null;
        } else if (action == VideoGroup.ACTION_REMOVE_AUTHOR) {
            for (Entry e : new ArrayList<>(entries)) {
                e.adapter.update(group);
                if (e.adapter.isEmpty()) remove(e);
            }
            return;
        } else if (action == VideoGroup.ACTION_REMOVE || action == VideoGroup.ACTION_SYNC) {
            if (entry != null) {
                entry.adapter.update(group);
                if (entry.adapter.isEmpty()) remove(entry);
            }
            return;
        }
        if (group.isEmpty()) return;
        if (entry == null) {
            entry = new Entry(group.getId(), new VideoGroupAdapter(listener));
            entry.adapter.update(group);
            int position = group.getPosition();
            if (position < 0 || position > entries.size()) position = entries.size();
            entries.add(position, entry);
            adapter.addAdapter(position, entry.adapter);
        } else entry.adapter.update(group);
    }

    public void clear() {
        for (Entry entry : new ArrayList<>(entries)) remove(entry);
    }

    public boolean isEmpty() { return adapter.getItemCount() == 0; }

    private Entry find(int id) {
        for (Entry e : entries) if (e.id == id) return e;
        return null;
    }

    private void remove(Entry entry) {
        adapter.removeAdapter(entry.adapter);
        entries.remove(entry);
    }

    private static final class Entry {
        final int id;
        final VideoGroupAdapter adapter;
        Entry(int id, VideoGroupAdapter adapter) { this.id = id; this.adapter = adapter; }
    }
}
