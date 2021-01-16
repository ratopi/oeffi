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

package de.schildbach.oeffi;

import de.schildbach.oeffi.util.CheatSheet;
import de.schildbach.oeffi.util.ToggleImageButton;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

public class MyActionBar extends LinearLayout {
    private final Context context;
    private final Resources res;
    private final LayoutInflater inflater;

    private View backButtonView;
    private View menuButtonView;
    private ViewGroup titlesGroup;
    private TextView primaryTitleView;
    private TextView secondaryTitleView;
    private View progressView;
    private ImageButton progressButton;
    private ImageView progressImage;

    private boolean progressAlwaysVisible = false;
    private int progressCount = 0;
    private Animation progressAnimation = null;
    private Handler handler = new Handler();

    private static final int BUTTON_INSERT_INDEX = 3;

    public MyActionBar(final Context context) {
        this(context, null);
    }

    public MyActionBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        this.context = context;
        this.res = getResources();
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        backButtonView = findViewById(R.id.action_bar_back_button);
        menuButtonView = findViewById(R.id.action_bar_menu_button);
        titlesGroup = (ViewGroup) findViewById(R.id.action_bar_titles);
        primaryTitleView = (TextView) findViewById(R.id.action_bar_primary_title);
        secondaryTitleView = (TextView) findViewById(R.id.action_bar_secondary_title);
        progressView = findViewById(R.id.action_bar_progress);
        progressButton = (ImageButton) findViewById(R.id.action_bar_progress_button);
        progressImage = (ImageView) findViewById(R.id.action_bar_progress_image);

        // Make sure action bar isn't stuck under a transparent status bar.
        final int statusHeight = res
                .getDimensionPixelSize(res.getIdentifier("status_bar_height", "dimen", "android"));
        setPadding(getPaddingLeft(), getPaddingTop() + statusHeight, getPaddingRight(), getPaddingBottom());
    }

    public void setDrawer(final OnClickListener onClickListener) {
        menuButtonView.setOnClickListener(onClickListener);
        menuButtonView.setVisibility(View.VISIBLE);
    }

    public void setBack(final OnClickListener onClickListener) {
        backButtonView.setOnClickListener(onClickListener);
        backButtonView.setVisibility(View.VISIBLE);
    }

    public void setPrimaryTitle(final CharSequence title) {
        primaryTitleView.setText(title);
    }

    public void setPrimaryTitle(final int titleRes) {
        primaryTitleView.setText(titleRes);
    }

    public void setSecondaryTitle(final CharSequence title) {
        secondaryTitleView.setText(title);
        secondaryTitleView.setVisibility(title != null ? View.VISIBLE : View.GONE);
    }

    public void setSecondaryTitle(final int titleRes) {
        secondaryTitleView.setText(titleRes);
        secondaryTitleView.setVisibility(titleRes != 0 ? View.VISIBLE : View.GONE);
    }

    public void setTitlesOnClickListener(final OnClickListener listener) {
        titlesGroup.setOnClickListener(listener);
        titlesGroup.setFocusable(true);
    }

    public void swapTitles() {
        final View view = titlesGroup.getChildAt(0);
        titlesGroup.removeViewAt(0);
        titlesGroup.addView(view, 1);
    }

    public void setCustomTitles(final int layoutRes) {
        final View view = inflater.inflate(layoutRes, titlesGroup, false);
        titlesGroup.removeViewAt(0);
        titlesGroup.addView(view, 0);
    }

    public ImageButton addButton(final int drawableRes, final int descriptionRes) {
        final LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                res.getDimensionPixelSize(R.dimen.action_bar_button_width), LayoutParams.MATCH_PARENT, 0f);
        buttonParams.gravity = Gravity.CENTER_VERTICAL;

        final ImageButton button = new ImageButton(context);
        button.setImageResource(drawableRes);
        button.setScaleType(ScaleType.CENTER);
        button.setMinimumHeight(res.getDimensionPixelSize(R.dimen.action_bar_height));
        final int padding = res.getDimensionPixelSize(R.dimen.action_bar_padding);
        button.setPadding(padding, padding, padding, padding);
        if (descriptionRes != 0) {
            final String description = context.getString(descriptionRes);
            button.setContentDescription(description);
            CheatSheet.setup(button, description);
        }
        addView(button, BUTTON_INSERT_INDEX, buttonParams);

        return button;
    }

    public ToggleImageButton addToggleButton(final int drawableRes, final int descriptionRes) {
        final LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                res.getDimensionPixelSize(R.dimen.action_bar_button_width), LayoutParams.MATCH_PARENT, 0f);
        buttonParams.gravity = Gravity.CENTER_VERTICAL;

        final ToggleImageButton button = new ToggleImageButton(context);
        button.setImageResource(drawableRes);
        button.setScaleType(ScaleType.CENTER);
        button.setMinimumHeight(res.getDimensionPixelSize(R.dimen.action_bar_height));
        if (descriptionRes != 0) {
            final String description = context.getString(descriptionRes);
            button.setContentDescription(description);
            CheatSheet.setup(button, description);
        }
        addView(button, BUTTON_INSERT_INDEX, buttonParams);

        return button;
    }

    public View addProgressButton() {
        progressAlwaysVisible = true;
        progressView.setVisibility(View.VISIBLE);
        CheatSheet.setup(progressButton);
        return getProgressButton();
    }

    public View getProgressButton() {
        return progressButton;
    }

    public void startProgress() {
        if (progressCount++ == 0) {
            handler.removeCallbacksAndMessages(null);
            handler.post(() -> {
                progressView.setVisibility(View.VISIBLE);
                if (progressAnimation == null) {
                    progressAnimation = AnimationUtils.loadAnimation(context, R.anim.rotate);
                    progressImage.startAnimation(progressAnimation);
                }
            });
        }
    }

    public void stopProgress() {
        if (--progressCount <= 0) {
            handler.postDelayed(() -> {
                if (progressAnimation != null) {
                    progressImage.clearAnimation();
                    progressAnimation = null;
                }
                if (!progressAlwaysVisible)
                    progressView.setVisibility(View.GONE);
            }, 200);
        }
    }

    public int getProgressCount() {
        return progressCount;
    }

    public void overflow(final int menuResId, final PopupMenu.OnMenuItemClickListener menuItemClickListener) {
        final View overflowButton = findViewById(R.id.action_bar_overflow_button);
        overflowButton.setVisibility(View.VISIBLE);
        overflowButton.setOnClickListener(v -> {
            final PopupMenu overflowMenu = new PopupMenu(context, v);
            overflowMenu.inflate(menuResId);
            overflowMenu.setOnMenuItemClickListener(menuItemClickListener);
            overflowMenu.show();
        });
    }
}
