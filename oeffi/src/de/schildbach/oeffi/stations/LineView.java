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

package de.schildbach.oeffi.stations;

import java.util.Arrays;
import java.util.Collection;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.CheatSheet;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Line.Attr;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.Style.Shape;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.widget.TextView;

public class LineView extends TextView {
    private Collection<Line> lines = null;
    private boolean ghosted = false;

    private final float strokeWidth;
    private final int colorTextGhosted;

    private static final Style DEFAULT_STYLE = new Style(Shape.ROUNDED, Color.BLACK, Color.WHITE, Color.BLACK);

    public LineView(final Context context) {
        this(context, null, 0);
    }

    public LineView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LineView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        setSingleLine();
        setTypeface(Typeface.DEFAULT_BOLD);
        setLineSpacing(0, 1.1f);

        final Resources res = getResources();
        strokeWidth = res.getDimension(R.dimen.line_style_border_stroke);
        colorTextGhosted = res.getColor(R.color.text_ghosted);
    }

    public void setLine(final Line line) {
        setLines(Arrays.asList(line));
    }

    public void setLines(final Collection<Line> lines) {
        this.lines = lines;
        update();
    }

    public void setGhosted(final boolean ghosted) {
        this.ghosted = ghosted;
        update();
    }

    private void update() {
        if (lines != null && !lines.isEmpty()) {
            final SpannableStringBuilder text = new SpannableStringBuilder();
            for (final Line line : lines) {
                if (text.length() > 0)
                    text.append(' ');
                final int begin = text.length();

                final Style lineStyle = line.style;
                final Style style;
                if (ghosted)
                    style = new Style(lineStyle != null ? lineStyle.shape : DEFAULT_STYLE.shape, colorTextGhosted,
                            DEFAULT_STYLE.foregroundColor, DEFAULT_STYLE.borderColor);
                else if (lineStyle != null)
                    style = lineStyle;
                else
                    style = DEFAULT_STYLE;

                if (line.label != null)
                    text.append(line.label);
                else
                    text.append(line.productCode());

                if (line.hasAttr(Attr.CIRCLE_CLOCKWISE))
                    text.append('\u21bb'); // clockwise arrow symbol
                if (line.hasAttr(Attr.CIRCLE_ANTICLOCKWISE))
                    text.append('\u21ba'); // anticlockwise arrow symbol

                final int end = text.length();
                text.append('\ufeff'); // Workaround

                text.setSpan(new Span(style, strokeWidth), begin, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            setText(text);
            if (lines.size() == 1) {
                final String name = lines.iterator().next().name;
                if (name != null)
                    CheatSheet.setup(this, name);
                else
                    CheatSheet.remove(this);
            } else {
                CheatSheet.remove(this);
            }
        } else {
            setText(null);
            CheatSheet.remove(this);
        }
    }

    private static class Span extends ReplacementSpan {
        private final Style style;
        private final float strokeWidth;

        private final RectF box = new RectF(), boxRotated = new RectF();
        private final Matrix matrix = new Matrix();
        private final int[] gradientColors = new int[2];

        private static final float[] GRADIENT_POSITIONS = new float[] { 0.495f, 0.505f };

        private Span(final Style style, final float strokeWidth) {
            this.style = style;
            this.strokeWidth = strokeWidth;
        }

        @Override
        public void draw(final Canvas canvas, final CharSequence text, final int start, final int end, final float x,
                final int top, final int y, final int bottom, final Paint paint) {
            final FontMetrics fontMetrics = paint.getFontMetrics();
            final float height = fontMetrics.bottom - fontMetrics.top;
            final float radius = radius(height);
            final float padding = padding(paint, height);
            box.set(x, y + fontMetrics.top, x + Math.round(paint.measureText(text, start, end) + padding * 2),
                    y + fontMetrics.bottom);

            // Background
            paint.setStyle(Paint.Style.FILL);
            if (style.backgroundColor2 == 0) {
                paint.setColor(style.backgroundColor);
                paint.setShader(null);
            } else {
                matrix.reset();
                matrix.postRotate(90, box.centerX(), box.centerY());
                matrix.mapRect(boxRotated, box);
                gradientColors[0] = style.backgroundColor;
                gradientColors[1] = style.backgroundColor2;
                paint.setShader(new LinearGradient(boxRotated.left, boxRotated.top, boxRotated.right, boxRotated.bottom,
                        gradientColors, GRADIENT_POSITIONS, Shader.TileMode.CLAMP));
            }
            paint.setColor(style.backgroundColor);
            canvas.drawRoundRect(box, radius, radius, paint);

            // Border
            if (style.hasBorder() && style.borderColor != Color.BLACK) {
                final float halfStroke = strokeWidth / 2;
                box.set(box.left + halfStroke, box.top + halfStroke, box.right - halfStroke, box.bottom - halfStroke);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(strokeWidth);
                paint.setColor(style.borderColor);
                paint.setShader(null);
                canvas.drawRoundRect(box, radius, radius, paint);
            }

            // Foreground
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(style.foregroundColor);
            paint.setShader(null);
            canvas.drawText(text, start, end, x + Math.round(padding), y, paint);
        }

        @Override
        public int getSize(final Paint paint, final CharSequence text, final int start, final int end,
                final Paint.FontMetricsInt fm) {
            final FontMetrics fontMetrics = paint.getFontMetrics();
            final float height = fontMetrics.bottom - fontMetrics.top;
            final float padding = padding(paint, height);
            return Math.round(paint.measureText(text, start, end) + padding * 2);
        }

        private float radius(final float height) {
            if (style.shape == Shape.RECT)
                return 0;
            else if (style.shape == Shape.CIRCLE)
                return height / 2;
            else
                return height / 4;
        }

        private float padding(final Paint paint, final float height) {
            if (style.shape == Shape.RECT)
                return paint.measureText("i");
            else if (style.shape == Shape.CIRCLE)
                return height / 3;
            else
                return height / 5;
        }
    }
}
