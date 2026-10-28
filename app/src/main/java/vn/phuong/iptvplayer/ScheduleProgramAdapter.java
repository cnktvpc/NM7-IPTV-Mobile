package vn.phuong.iptvplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public final class ScheduleProgramAdapter extends BaseAdapter {
    public interface Listener {
        void onCatchupClick(EpgStore.Programme programme);
        void onProgramClick(EpgStore.Programme programme);
    }

    private final LayoutInflater inflater;
    private final Listener listener;
    private Channel channel;
    private List<EpgStore.Programme> programmes = new ArrayList<>();
    private EpgStore.Programme activeCatchupProgramme;

    public ScheduleProgramAdapter(Context context, Listener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public void submit(List<EpgStore.Programme> list, EpgStore.Programme activeCatchup) {
        this.programmes = list == null ? new ArrayList<>() : new ArrayList<>(list);
        this.activeCatchupProgramme = activeCatchup;
        notifyDataSetChanged();
    }

    public void setActiveCatchupProgramme(EpgStore.Programme programme) {
        this.activeCatchupProgramme = programme;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return programmes.size(); }
    @Override public EpgStore.Programme getItem(int position) { return programmes.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_schedule_program, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        EpgStore.Programme p = getItem(position);
        holder.time.setText(p.timeRangeText());
        holder.duration.setText(p.durationText());
        holder.title.setText(p.title);

        if (!p.desc.isEmpty()) {
            holder.desc.setText(p.desc);
            holder.desc.setVisibility(View.VISIBLE);
        } else {
            holder.desc.setVisibility(View.GONE);
        }

        long now = System.currentTimeMillis();
        boolean isCurrentReplay = activeCatchupProgramme != null
                && activeCatchupProgramme.startMs == p.startMs;

        holder.badgeReplaying.setVisibility(View.GONE);
        holder.badgeLive.setVisibility(View.GONE);
        holder.btnCatchup.setVisibility(View.GONE);
        holder.status.setVisibility(View.GONE);

        if (isCurrentReplay) {
            holder.badgeReplaying.setVisibility(View.VISIBLE);
        } else if (p.isCurrent(now)) {
            holder.badgeLive.setVisibility(View.VISIBLE);
        } else if (p.isPast(now)) {
            boolean hasCatchup = channel != null && channel.hasCatchup();
            if (hasCatchup) {
                holder.btnCatchup.setVisibility(View.VISIBLE);
                holder.btnCatchup.setOnClickListener(v -> {
                    if (listener != null) listener.onCatchupClick(p);
                });
            } else {
                holder.status.setText("Đã phát");
                holder.status.setVisibility(View.VISIBLE);
            }
        } else {
            holder.status.setText("Chưa phát");
            holder.status.setVisibility(View.VISIBLE);
        }

        convertView.setOnClickListener(v -> {
            if (listener != null) listener.onProgramClick(p);
        });

        return convertView;
    }

    private static final class Holder {
        final TextView time, duration, title, desc, badgeLive, badgeReplaying, status;
        final Button btnCatchup;

        Holder(View v) {
            time = v.findViewById(R.id.txtProgramTime);
            duration = v.findViewById(R.id.txtProgramDuration);
            title = v.findViewById(R.id.txtProgramTitle);
            desc = v.findViewById(R.id.txtProgramDesc);
            badgeLive = v.findViewById(R.id.badgeLive);
            badgeReplaying = v.findViewById(R.id.badgeReplaying);
            btnCatchup = v.findViewById(R.id.btnCatchupPlay);
            status = v.findViewById(R.id.txtProgramStatus);
        }
    }
}
