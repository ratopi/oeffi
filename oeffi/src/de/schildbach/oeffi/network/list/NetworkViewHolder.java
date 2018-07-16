/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi.network.list;

import javax.annotation.Nullable;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkResources;

import android.content.Context;
import android.content.res.Resources;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

public class NetworkViewHolder extends RecyclerView.ViewHolder {
    private final Context context;
    private final Resources res;
    private final int textColor;
    private final int grey600;

    private final ImageView iconView;
    private final TextView labelView;
    private final TextView stateView;
    private final TextView commentView;
    private final TextView usageView;
    private final ImageButton contextButton;
    private final View contextButtonSpace;

    private static final int MEGABYTE = 1024 * 1024;

    public NetworkViewHolder(final Context context, final View itemView) {
        super(itemView);
        this.context = context;
        this.res = context.getResources();
        this.textColor = res.getColor(R.color.text);
        this.grey600 = res.getColor(R.color.grey600);

        iconView = (ImageView) itemView.findViewById(R.id.network_picker_entry_icon);
        labelView = (TextView) itemView.findViewById(R.id.network_picker_entry_label);
        stateView = (TextView) itemView.findViewById(R.id.network_picker_entry_state);
        commentView = (TextView) itemView.findViewById(R.id.network_picker_entry_comment);
        usageView = (TextView) itemView.findViewById(R.id.network_picker_entry_usage);
        contextButton = (ImageButton) itemView.findViewById(R.id.network_picker_entry_context_button);
        contextButtonSpace = itemView.findViewById(R.id.network_picker_entry_context_button_space);
    }

    public void bind(final NetworkListEntry.Network entry, final boolean isEnabled, final long dbFileLength,
            @Nullable final NetworkClickListener clickListener,
            @Nullable final NetworkContextMenuItemListener contextMenuItemListener) {
        itemView.setFocusable(isEnabled);
        if (clickListener != null) {
            itemView.setOnClickListener(new View.OnClickListener() {
                public void onClick(final View v) {
                    clickListener.onNetworkClick(entry);
                }
            });
        } else {
            itemView.setOnClickListener(null);
        }

        final NetworkResources networkRes = NetworkResources.instance(context, entry.id);
        if (networkRes.icon != null) {
            iconView.setVisibility(View.VISIBLE);
            iconView.setImageDrawable(networkRes.icon);
        } else {
            iconView.setVisibility(View.INVISIBLE);
            iconView.setImageResource(R.drawable.space_48dp);
        }

        labelView.setText(networkRes.label);
        labelView.setTextColor(isEnabled ? textColor : grey600);

        if (entry.state != null && isEnabled)
            stateView.setText(
                    res.getIdentifier("network_picker_entry_state_" + entry.state, "string", context.getPackageName()));
        else
            stateView.setText(null);

        commentView.setText(networkRes.comment);
        commentView.setTextColor(isEnabled ? textColor : grey600);

        if (dbFileLength > 0) {
            usageView.setVisibility(View.VISIBLE);
            usageView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_beenhere_grey600_24dp, 0, 0, 0);
            final double usage = Math.max((double) dbFileLength / MEGABYTE, 0.1);
            usageView.setText(res.getString(R.string.network_picker_entry_usage, usage));
        } else if (networkRes.license != null) {
            usageView.setVisibility(View.VISIBLE);
            usageView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            usageView.setText(res.getString(R.string.network_picker_entry_license, networkRes.license));
        } else {
            usageView.setVisibility(View.GONE);
        }

        if (contextMenuItemListener != null) {
            contextButton.setVisibility(View.VISIBLE);
            contextButtonSpace.setVisibility(View.VISIBLE);
            contextButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(final View v) {
                    final PopupMenu contextMenu = new PopupMenu(context, v);
                    contextMenu.inflate(R.menu.network_picker_context);
                    contextMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(final MenuItem item) {
                            return contextMenuItemListener.onNetworkContextMenuItemClick(entry, item.getItemId());
                        }
                    });
                    contextMenu.show();
                }
            });
        } else {
            contextButton.setVisibility(View.GONE);
            contextButtonSpace.setVisibility(View.GONE);
        }
    }
}
