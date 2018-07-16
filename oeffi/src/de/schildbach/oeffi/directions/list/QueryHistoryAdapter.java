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

package de.schildbach.oeffi.directions.list;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.QueryHistoryProvider;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

public class QueryHistoryAdapter extends RecyclerView.Adapter<QueryHistoryViewHolder> {
    private final Context context;
    private final ContentResolver contentResolver;
    private final LayoutInflater inflater;
    private final NetworkId network;
    private final QueryHistoryClickListener clickListener;
    private final QueryHistoryContextMenuItemListener contextMenuItemListener;

    private final Cursor cursor;
    private final ContentObserver contentObserver;
    private final int rowIdColumn;
    private final int fromTypeColumn;
    private final int fromIdColumn;
    private final int fromLatColumn;
    private final int fromLonColumn;
    private final int fromPlaceColumn;
    private final int fromNameColumn;
    private final int toTypeColumn;
    private final int toIdColumn;
    private final int toLatColumn;
    private final int toLonColumn;
    private final int toPlaceColumn;
    private final int toNameColumn;
    private final int favoriteColumn;
    private final int savedTripDepartureTimeColumn;
    private final int savedTripColumn;

    private long selectedRowId = RecyclerView.NO_ID;

    public QueryHistoryAdapter(final Context context, final NetworkId network,
            final QueryHistoryClickListener clickListener,
            final QueryHistoryContextMenuItemListener contextMenuItemListener) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        this.inflater = LayoutInflater.from(context);
        this.network = network;
        this.clickListener = clickListener;
        this.contextMenuItemListener = contextMenuItemListener;

        final Uri uri = QueryHistoryProvider.CONTENT_URI.buildUpon()
                .appendPath(network != null ? network.name() : "_NONE_").build();
        cursor = contentResolver.query(uri, null, null, null,
                QueryHistoryProvider.KEY_FAVORITE + " DESC, " + QueryHistoryProvider.KEY_LAST_QUERIED + " DESC");
        contentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(final boolean selfChange) {
                cursor.requery();
                notifyDataSetChanged();
            }
        };
        contentResolver.registerContentObserver(uri, true, contentObserver);
        rowIdColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID);
        fromTypeColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_TYPE);
        fromIdColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_ID);
        fromLatColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LAT);
        fromLonColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LON);
        fromPlaceColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_PLACE);
        fromNameColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_NAME);
        toTypeColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_TYPE);
        toIdColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_ID);
        toLatColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LAT);
        toLonColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LON);
        toPlaceColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_PLACE);
        toNameColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_NAME);
        favoriteColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FAVORITE);
        savedTripDepartureTimeColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_LAST_DEPARTURE_TIME);
        savedTripColumn = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_LAST_TRIP);

        setHasStableIds(true);
    }

    public void close() {
        contentResolver.unregisterContentObserver(contentObserver);
        cursor.close();
    }

    public Uri putEntry(final Location from, final Location to) {
        final Uri uri = QueryHistoryProvider.put(contentResolver, network, from, to, null, true);
        notifyDataSetChanged();
        cursor.requery();
        return uri;
    }

    public void removeEntry(final int position) {
        final Uri uri = QueryHistoryProvider.historyRowUri(network, getItemId(position));
        contentResolver.delete(uri, null, null);
        notifyItemRemoved(position);
        cursor.requery();
    }

    public void removeAllEntries() {
        final Uri uri = QueryHistoryProvider.CONTENT_URI.buildUpon().appendPath(network.name()).build();
        contentResolver.delete(uri, null, null);
        notifyItemRangeRemoved(0, getItemCount());
        cursor.requery();
    }

    public void setIsFavorite(final int position, final boolean isFavorite) {
        final Uri uri = QueryHistoryProvider.historyRowUri(network, getItemId(position));
        final ContentValues values = new ContentValues();
        values.put(QueryHistoryProvider.KEY_FAVORITE, isFavorite ? 1 : 0);
        contentResolver.update(uri, values, null, null);
        notifyDataSetChanged();
        cursor.requery();
    }

    public void setSavedTrip(final int position, final long departureTime, final long arrivalTime,
            final byte[] serializedTrip) {
        final Uri uri = QueryHistoryProvider.historyRowUri(network, getItemId(position));
        final ContentValues values = new ContentValues();
        values.put(QueryHistoryProvider.KEY_LAST_DEPARTURE_TIME, departureTime);
        values.put(QueryHistoryProvider.KEY_LAST_ARRIVAL_TIME, arrivalTime);
        values.put(QueryHistoryProvider.KEY_LAST_TRIP, serializedTrip);
        contentResolver.update(uri, values, null, null);
        notifyItemChanged(position);
        cursor.requery();
    }

    public void setSelectedEntry(final long rowId) {
        this.selectedRowId = rowId;
        notifyDataSetChanged();
    }

    public void clearSelectedEntry() {
        setSelectedEntry(RecyclerView.NO_ID);
    }

    @Override
    public int getItemCount() {
        return cursor.getCount();
    }

    @Override
    public long getItemId(final int position) {
        cursor.moveToPosition(position);
        return cursor.getLong(rowIdColumn);
    }

    @Override
    public QueryHistoryViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new QueryHistoryViewHolder(inflater.inflate(R.layout.directions_query_history_entry, parent, false),
                context, network);
    }

    @Override
    public void onBindViewHolder(final QueryHistoryViewHolder holder, final int position) {
        cursor.moveToPosition(position);
        final long rowId = cursor.getLong(rowIdColumn);
        final Location from = new Location(QueryHistoryProvider.convert(cursor.getInt(fromTypeColumn)),
                cursor.getString(fromIdColumn), cursor.getInt(fromLatColumn), cursor.getInt(fromLonColumn),
                cursor.getString(fromPlaceColumn), cursor.getString(fromNameColumn));
        final Location to = new Location(QueryHistoryProvider.convert(cursor.getInt(toTypeColumn)),
                cursor.getString(toIdColumn), cursor.getInt(toLatColumn), cursor.getInt(toLonColumn),
                cursor.getString(toPlaceColumn), cursor.getString(toNameColumn));
        final boolean isFavorite = cursor.getInt(favoriteColumn) == 1;
        final long savedTripDepartureTime = cursor.getLong(savedTripDepartureTimeColumn);
        final byte[] serializedSavedTrip = cursor.getBlob(savedTripColumn);
        final Integer fromFavState = FavoriteStationsProvider.favState(contentResolver, network, from);
        final Integer toFavState = FavoriteStationsProvider.favState(contentResolver, network, to);
        holder.bind(rowId, from, to, isFavorite, savedTripDepartureTime, serializedSavedTrip, fromFavState, toFavState,
                selectedRowId, clickListener, contextMenuItemListener);
    }
}
