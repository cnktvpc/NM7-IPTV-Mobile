package vn.phuong.iptvplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

final class QuickChannelAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private List<Channel> channels = new ArrayList<>();
    private String currentId = "";

    QuickChannelAdapter(Context context) { inflater = LayoutInflater.from(context); }

    void submit(List<Channel> values, String currentId) {
        channels = values == null ? java.util.Collections.emptyList() : values;
        this.currentId = currentId;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return channels.size(); }
    @Override public Channel getItem(int position) { return channels.get(position); }
    @Override public long getItemId(int position) { return AppPreferences.id(getItem(position)).hashCode(); }
    @Override public boolean hasStableIds() { return true; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_quick_channel, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else holder = (Holder) convertView.getTag();
        Channel channel = getItem(position);
        holder.number.setText(String.valueOf(position + 1));
        holder.name.setText(channel.name());
        holder.group.setText(channel.group());
        convertView.setSelected(AppPreferences.id(channel).equals(currentId));
        return convertView;
    }

    private static final class Holder {
        final TextView number, name, group;
        Holder(View view) {
            number = view.findViewById(R.id.txtQuickNumber);
            name = view.findViewById(R.id.txtQuickName);
            group = view.findViewById(R.id.txtQuickGroup);
        }
    }
}
