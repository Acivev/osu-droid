package com.reco1l.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class PropertyBadgeView extends View implements BaseView {


    public PropertyBadgeView(Context context) {
        this(context, null);
    }

    public PropertyBadgeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        onCreate(attrs);
    }

    public PropertyBadgeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        onCreate(attrs);
    }

    @Override
    public void onCreate(AttributeSet attrs) {

    }

    @Override
    public View getView() {
        return BaseView.super.getView();
    }

    @Override
    public int[] getStyleable() {
        return BaseView.super.getStyleable();
    }

    @Override
    public void onManageAttributes(TypedArray a) {
        BaseView.super.onManageAttributes(a);
    }
}
