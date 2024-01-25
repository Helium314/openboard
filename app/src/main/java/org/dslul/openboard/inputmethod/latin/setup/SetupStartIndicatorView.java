/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.setup;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

public final class SetupStartIndicatorView extends LinearLayout {
    public SetupStartIndicatorView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.setup_start_indicator_label, this);

        final LabelView labelView = findViewById(R.id.setup_start_label);
        labelView.setIndicatorView(findViewById(R.id.setup_start_indicator));
    }

    public static final class LabelView extends AppCompatTextView {
        private View mIndicatorView;

        public LabelView(final Context context, final AttributeSet attrs) {
            super(context, attrs);
            final boolean isNight = ResourceUtils.isNight(context.getResources());
            final int activatedColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? ContextCompat.getColor(context, android.R.color.system_accent1_500)
                    : !isNight
                        ? Color.parseColor("#5E9CED")
                        : Color.parseColor("#72A4F3");

            final int deactivatedColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? !isNight
                        ? ContextCompat.getColor(context, android.R.color.system_accent1_700)
                        : ContextCompat.getColor(context, android.R.color.system_accent1_200)
                    : !isNight
                        ? Color.parseColor("#1971E3")
                        : Color.parseColor("#5C94F1");

            setTextColor(new ColorStateList(new int[][] { { android.R.attr.state_focused }, { android.R.attr.state_pressed }, {} },
                    new int[] { activatedColor, activatedColor, deactivatedColor }));
        }

        public void setIndicatorView(final View indicatorView) {
            mIndicatorView = indicatorView;
        }

        @Override
        public void setPressed(final boolean pressed) {
            super.setPressed(pressed);
            updateIndicatorView(pressed);
        }

        private void updateIndicatorView(final boolean pressed) {
            if (mIndicatorView != null) {
                mIndicatorView.setPressed(pressed);
                mIndicatorView.invalidate();
            }
        }
    }

    public static final class IndicatorView extends View {
        private final Path mIndicatorPath = new Path();
        private final Paint mIndicatorPaint = new Paint();
        private final ColorStateList mIndicatorColor;

        public IndicatorView(final Context context, final AttributeSet attrs) {
            super(context, attrs);
            final boolean isNight = ResourceUtils.isNight(context.getResources());
            mIndicatorColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? !isNight
                        ? new ColorStateList(new int[][] { { android.R.attr.state_focused }, { android.R.attr.state_pressed }, {} },
                                new int[] { getResources().getColor(android.R.color.system_accent1_100, null), getResources().getColor(android.R.color.system_accent1_100, null),
                                        getResources().getColor(android.R.color.system_accent1_50, null) })
                        : new ColorStateList(new int[][] { { android.R.attr.state_focused }, { android.R.attr.state_pressed }, {} },
                                new int[] { getResources().getColor(android.R.color.system_accent1_700, null), getResources().getColor(android.R.color.system_accent1_700, null),
                                        getResources().getColor(android.R.color.system_accent1_800, null) })

                    : AppCompatResources.getColorStateList(context, R.color.setup_step_action_background);

            mIndicatorPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(@NonNull final Canvas canvas) {
            super.onDraw(canvas);
            final int layoutDirection = ViewCompat.getLayoutDirection(this);
            final int width = getWidth();
            final int height = getHeight();
            final float halfHeight = height / 2.0f;
            final Path path = mIndicatorPath;
            path.rewind();
            if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) {
                // Left arrow
                path.moveTo(width, 0.0f);
                path.lineTo(0.0f, halfHeight);
                path.lineTo(width, height);
            } else { // LAYOUT_DIRECTION_LTR
                // Right arrow
                path.moveTo(0.0f, 0.0f);
                path.lineTo(width, halfHeight);
                path.lineTo(0.0f, height);
            }
            path.close();
            final int[] stateSet = getDrawableState();
            final int color = mIndicatorColor.getColorForState(stateSet, 0);
            mIndicatorPaint.setColor(color);
            canvas.drawPath(path, mIndicatorPaint);
        }
    }
}
