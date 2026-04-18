package com.upo.batteryassistant.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.upo.batteryassistant.R;

public class StrokeTextView extends AppCompatTextView {
    private boolean strokeEnabled;
    private int strokeColor;
    private float strokeWidth;

    public StrokeTextView(@NonNull Context context) {
        super(context);
        init();
    }

    public StrokeTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StrokeTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        TypedValue typedValue = new TypedValue();
        if (getContext().getTheme().resolveAttribute(R.attr.baTextStrokeEnabled, typedValue, true)) {
            strokeEnabled = typedValue.data != 0;
        }
        if (getContext().getTheme().resolveAttribute(R.attr.baTextStrokeColor, typedValue, true)) {
            strokeColor = typedValue.resourceId != 0 ? getContext().getColor(typedValue.resourceId) : typedValue.data;
        }
        if (getContext().getTheme().resolveAttribute(R.attr.baTextStrokeWidth, typedValue, true)) {
            if (typedValue.type == TypedValue.TYPE_FLOAT) {
                strokeWidth = typedValue.getFloat();
            } else {
                strokeWidth = TypedValue.complexToFloat(typedValue.data);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!strokeEnabled || strokeWidth <= 0f) {
            super.onDraw(canvas);
            return;
        }

        int originalTextColor = getCurrentTextColor();
        Paint paint = getPaint();
        Paint.Style originalStyle = paint.getStyle();
        float originalStrokeWidth = paint.getStrokeWidth();

        setTextColor(strokeColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        super.onDraw(canvas);

        setTextColor(originalTextColor);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(originalStrokeWidth);
        super.onDraw(canvas);

        paint.setStyle(originalStyle);
    }
}
