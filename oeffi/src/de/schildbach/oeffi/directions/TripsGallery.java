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

package de.schildbach.oeffi.directions;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import com.google.common.math.LongMath;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.pte.dto.Trip;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Gallery;

public class TripsGallery extends Gallery {
    private OnScrollListener onScrollListener;

    private final Paint gridPaint = new Paint();
    private final Paint gridLabelPaint = new Paint();
    private final Paint currenttimePaint = new Paint();
    private final Paint currenttimeLabelBackgroundPaint = new Paint();
    private final Paint currenttimeLabelTextPaint = new Paint();

    private final Context context;
    private final float density;
    private final java.text.DateFormat timeFormat;

    private final TripsGalleryAdapter adapter;

    private final Handler handler = new Handler();

    public TripsGallery(final Context context) {
        this(context, null, 0);
    }

    public TripsGallery(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TripsGallery(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        this.context = context;

        final Resources res = getResources();
        density = res.getDisplayMetrics().density;
        final float strokeWidth = res.getDimension(R.dimen.trips_overview_stroke_width);
        final int textColor = res.getColor(R.color.text_dark);

        gridPaint.setColor(Color.GRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(strokeWidth);
        gridPaint.setPathEffect(new DashPathEffect(new float[] { 4f * density, 4f * density }, 0));
        gridPaint.setAntiAlias(false);

        gridLabelPaint.setColor(textColor);
        gridLabelPaint.setAntiAlias(true);
        gridLabelPaint.setTextSize(res.getDimension(R.dimen.font_size_normal));
        gridLabelPaint.setTextAlign(Align.CENTER);

        currenttimePaint.setColor(Color.YELLOW);
        currenttimePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        currenttimePaint.setStrokeWidth(strokeWidth);
        currenttimePaint.setShadowLayer(2, 0, 0, Color.BLACK);
        currenttimePaint.setAntiAlias(false);

        currenttimeLabelBackgroundPaint.setColor(Color.YELLOW);
        currenttimeLabelBackgroundPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        currenttimeLabelBackgroundPaint.setStrokeWidth(strokeWidth);
        currenttimeLabelBackgroundPaint.setShadowLayer(2, 0, 0, Color.BLACK);
        currenttimeLabelBackgroundPaint.setAntiAlias(true);

        currenttimeLabelTextPaint.setColor(Color.BLACK);
        currenttimeLabelTextPaint.setAntiAlias(true);
        currenttimeLabelTextPaint.setTextSize(res.getDimension(R.dimen.font_size_small));
        currenttimeLabelTextPaint.setTextAlign(Align.CENTER);

        timeFormat = DateFormat.getTimeFormat(context);

        setHorizontalFadingEdgeEnabled(false);

        adapter = new TripsGalleryAdapter(context);
        setAdapter(adapter);

        setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
            public void onChildViewRemoved(final View parent, final View child) {
                handler.removeCallbacksAndMessages(null);
                handler.post(onChildViewChangedRunnable);
            }

            public void onChildViewAdded(final View parent, final View child) {
                handler.removeCallbacksAndMessages(null);
                handler.post(onChildViewChangedRunnable);
            }
        });
    }

    public void setTrips(final List<Trip> trips, final boolean canScrollLater, final boolean canScrollEarlier) {
        adapter.setTrips(trips, canScrollLater, canScrollEarlier);
    }

    public void setOnScrollListener(final OnScrollListener onScrollListener) {
        this.onScrollListener = onScrollListener;
    }

    private final Runnable onChildViewChangedRunnable = new Runnable() {
        public void run() {
            final long currentTime = System.currentTimeMillis();
            final int first = getFirstVisiblePosition();
            final int last = getLastVisiblePosition();

            // determine min/max time
            long minTime = Long.MAX_VALUE;
            long maxTime = 0;

            for (int i = first; i <= last; i++) {
                final Trip trip = (Trip) adapter.getItem(i);
                if (trip != null) {
                    final Date tripMinTime = trip.getMinTime();
                    if (tripMinTime != null && tripMinTime.getTime() < minTime)
                        minTime = tripMinTime.getTime();

                    final Date tripMaxTime = trip.getMaxTime();
                    if (tripMaxTime != null && tripMaxTime.getTime() > maxTime)
                        maxTime = tripMaxTime.getTime();
                }
            }

            // snap to current time
            if (minTime == 0 || (currentTime > minTime - DateUtils.MINUTE_IN_MILLIS * 30 && currentTime < minTime))
                minTime = currentTime;
            else if (maxTime == 0 || (currentTime < maxTime + DateUtils.MINUTE_IN_MILLIS * 30 && currentTime > maxTime))
                maxTime = currentTime;

            // padding
            final long timeDiff = LongMath.checkedSubtract(maxTime, minTime);
            long timePadding = timeDiff / 15;
            if (timeDiff < DateUtils.MINUTE_IN_MILLIS * 30) // zoom limit
                timePadding = (DateUtils.MINUTE_IN_MILLIS * 30 - timeDiff) / 2;
            if (timePadding < DateUtils.MINUTE_IN_MILLIS) // minimum padding
                timePadding = DateUtils.MINUTE_IN_MILLIS;
            minTime = LongMath.checkedSubtract(minTime, timePadding);
            maxTime = LongMath.checkedAdd(maxTime, timePadding);

            // animate
            final long currentMinTime = adapter.getMinTime();
            final long currentMaxTime = adapter.getMaxTime();

            if (currentMinTime != 0 || currentMaxTime != 0) {
                final long diffMin = minTime - currentMinTime;
                final long diffMax = maxTime - currentMaxTime;

                if (Math.abs(diffMin) > DateUtils.SECOND_IN_MILLIS * 10
                        || Math.abs(diffMax) > DateUtils.SECOND_IN_MILLIS * 10) {
                    minTime = currentMinTime + diffMin / 3;
                    maxTime = currentMaxTime + diffMax / 3;

                    handler.postDelayed(this, 25); // 40 fps
                }
            }

            adapter.setMinMaxTimes(minTime, maxTime);

            // refresh views
            invalidate();
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++)
                getChildAt(i).invalidate();

            // notify listener
            if (onScrollListener != null)
                onScrollListener.onScroll();
        }
    };

    private final Calendar gridPtr = new GregorianCalendar();
    private final Rect bounds = new Rect();
    private final RectF boundsF = new RectF();
    private final StringBuilder labelTime = new StringBuilder();
    private final Path path = new Path();

    @Override
    protected void onDraw(final Canvas canvas) {
        final long now = System.currentTimeMillis();
        final int width = getWidth();
        final int height = getHeight();

        final long minTime = adapter.getMinTime();
        final long maxTime = adapter.getMaxTime();
        final long timeDiff = maxTime - minTime;

        // prepare grid
        gridPtr.setTimeInMillis(minTime);
        gridPtr.set(Calendar.MILLISECOND, 0);
        gridPtr.set(Calendar.SECOND, 0);
        gridPtr.set(Calendar.MINUTE, 0);

        final int gridValue, gridField;
        if (timeDiff < DateUtils.MINUTE_IN_MILLIS * 30) {
            // 5 minute grid
            gridValue = 5;
            gridField = Calendar.MINUTE;
        } else if (timeDiff < DateUtils.HOUR_IN_MILLIS) {
            // 10 minute grid
            gridValue = 10;
            gridField = Calendar.MINUTE;
        } else if (timeDiff < DateUtils.HOUR_IN_MILLIS * 3) {
            // half hour grid
            gridValue = 30;
            gridField = Calendar.MINUTE;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        } else if (timeDiff < DateUtils.HOUR_IN_MILLIS * 6) {
            // hour grid
            gridValue = 1;
            gridField = Calendar.HOUR_OF_DAY;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        } else if (timeDiff < DateUtils.HOUR_IN_MILLIS * 12) {
            // 2 hour grid
            gridValue = 2;
            gridField = Calendar.HOUR_OF_DAY;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        } else if (timeDiff < DateUtils.DAY_IN_MILLIS) {
            // 4 hour grid
            gridValue = 4;
            gridField = Calendar.HOUR_OF_DAY;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        } else {
            // 12 hour grid
            gridValue = 12;
            gridField = Calendar.HOUR_OF_DAY;
            gridPtr.set(Calendar.HOUR_OF_DAY, 0);
        }

        // draw grid
        long firstGrid = 0;
        boolean hasDateBorder = false;

        while (gridPtr.getTimeInMillis() < maxTime) {
            final long t = gridPtr.getTimeInMillis();

            if (t >= minTime) {
                // safe first grid line
                if (firstGrid == 0)
                    firstGrid = t;

                final boolean isDateBorder = gridPtr.get(Calendar.HOUR_OF_DAY) == 0
                        && gridPtr.get(Calendar.MINUTE) == 0;
                final float y = adapter.timeToCoord(t, height);

                labelTime.setLength(0);
                labelTime.append(timeFormat.format(t));
                if (isDateBorder) {
                    labelTime.append(", ").append(Formats.formatDate(context, now, t));
                    hasDateBorder = true;
                }

                gridLabelPaint.getTextBounds(labelTime.toString(), 0, labelTime.length(), bounds);
                bounds.offsetTo(0, Math.round(y) - bounds.height());

                path.reset();
                path.moveTo(bounds.right, y);
                path.lineTo(width, y);
                // can't use drawLine here because of
                // https://code.google.com/p/android/issues/detail?id=29944
                canvas.drawPath(path, gridPaint);
                canvas.drawText(labelTime, 0, labelTime.length(), bounds.centerX(), bounds.bottom, gridLabelPaint);
            }

            gridPtr.add(gridField, gridValue);
        }

        // retroactively add date to first grid line
        if (!hasDateBorder && firstGrid > 0) {
            labelTime.setLength(0);
            labelTime.append(timeFormat.format(firstGrid)).append(", ")
                    .append(Formats.formatDate(context, now, firstGrid));

            gridLabelPaint.getTextBounds(labelTime.toString(), 0, labelTime.length(), bounds);
            bounds.offsetTo(0, Math.round(adapter.timeToCoord(firstGrid, height)) - bounds.height());

            canvas.drawText(labelTime, 0, labelTime.length(), bounds.centerX(), bounds.bottom, gridLabelPaint);
        }

        // draw current time
        final float y = adapter.timeToCoord(now, height);

        final String label = timeFormat.format(now);

        currenttimeLabelTextPaint.getTextBounds(label, 0, label.length(), bounds);
        final int inset = Math.round(2 * density);
        bounds.inset(-inset, -inset);
        bounds.offsetTo(0, Math.round(y) - bounds.height());

        canvas.drawLine(bounds.right, y, width, y, currenttimePaint);
        final float roundRadius = 3 * density;
        boundsF.set(bounds);
        canvas.drawRoundRect(boundsF, roundRadius, roundRadius, currenttimeLabelBackgroundPaint);
        canvas.drawText(label, bounds.centerX(), bounds.bottom - inset, currenttimeLabelTextPaint);
    }

    public interface OnScrollListener {
        void onScroll();
    }
}
