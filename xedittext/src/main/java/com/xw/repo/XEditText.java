package com.xw.repo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatDrawableManager;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * XEditText
 * <p>
 * GitHub: https://github.com/woxingxiao/XEditText
 * <p>
 * 带删除功能的EditText；显示或者隐藏密码；可设置自动添加分隔符分割电话号码、银行卡号等；支持禁止Emoji表情符号输入。
 * 可以设置最大值的输入
 * Created by woxingxiao on 2017-03-22.
 */
public class XEditText extends AppCompatEditText {

    private String mSeparator; //mSeparator，default is "".
    private boolean disableClear; // disable clear drawable.
    private Drawable mClearDrawable;
    private Drawable mTogglePwdDrawable;
    private boolean disableEmoji; // disable emoji and some special symbol input.
    private int mShowPwdResId;
    private int mHidePwdResId;

    private OnXTextChangeListener mXTextChangeListener;
    private TextWatcher mTextWatcher;
    private int mPreLength;
    private boolean hasFocused;
    private int[] pattern; // pattern to separate. e.g.: mSeparator = "-", pattern = [3,4,4] -> xxx-xxxx-xxxx
    private int[] intervals; // indexes of separators.
    /* When you set pattern, it will automatically compute the max length of characters and separators,
     so you don't need to set 'maxLength' attr in your xml any more(it won't work).*/
    private int mMaxLength = Integer.MAX_VALUE;
    private boolean hasNoSeparator; // true, the same as EditText.
    private boolean isPwdInputType;
    private boolean isPwdShow;
    private Bitmap mBitmap;

    // 保存设置的所有输入限制
    private int maxmum;//允许的最大值
    private int numOfDecimal;//允许的小数位
    private List<InputFilter> inputFilters;

    public XEditText(Context context) {
        this(context, null);
    }

    public XEditText(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextStyle); // Attention here !
    }

    public XEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        initAttrs(context, attrs, defStyleAttr);

        if (disableEmoji) {
            setFilters(new InputFilter[]{new EmojiExcludeFilter()});
        }

        mTextWatcher = new MyTextWatcher();
        this.addTextChangedListener(mTextWatcher);

        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                hasFocused = hasFocus;
                markerFocusChangeLogic();
            }
        });
        init();
        if (maxmum != Integer.MAX_VALUE) {
            setMaxmumFilter(maxmum,numOfDecimal);
        }
    }

    private void init() {
        inputFilters = new ArrayList<>();
    }

    private void initAttrs(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.XEditText, defStyleAttr, 0);

        mSeparator = a.getString(R.styleable.XEditText_x_separator);
        if (mSeparator == null) {
            mSeparator = "";
        }
        if (mSeparator.length() > 0) {
            int inputType = getInputType();
            if (inputType == 2 || inputType == 8194 || inputType == 4098) { // if inputType is number, it can't insert mSeparator.
                setInputType(InputType.TYPE_CLASS_PHONE);
            }
        }

        disableClear = a.getBoolean(R.styleable.XEditText_x_disableClear, false);
        boolean disableTogglePwdDrawable = a.getBoolean(R.styleable.XEditText_x_disableTogglePwdDrawable, false);

        if (!disableClear) {
            int cdId = a.getResourceId(R.styleable.XEditText_x_clearDrawable, -1);
            if (cdId == -1)
                cdId = R.drawable.x_et_svg_ic_clear_24dp;
            mClearDrawable = ContextCompat.getDrawable(context, cdId);
            mClearDrawable.setBounds(0, 0, mClearDrawable.getIntrinsicWidth(),
                    mClearDrawable.getIntrinsicHeight());
            if (cdId == R.drawable.x_et_svg_ic_clear_24dp)
                DrawableCompat.setTint(mClearDrawable, getCurrentHintTextColor());
        }

        int inputType = getInputType();
        if (!disableTogglePwdDrawable && (inputType == 129 || inputType == 145 || inputType == 18 || inputType == 225)) {
            isPwdInputType = true;
            isPwdShow = inputType == 145;
            mMaxLength = 20;

            mShowPwdResId = a.getResourceId(R.styleable.XEditText_x_showPwdDrawable, -1);
            mHidePwdResId = a.getResourceId(R.styleable.XEditText_x_hidePwdDrawable, -1);
            if (mShowPwdResId == -1)
                mShowPwdResId = R.drawable.x_et_svg_ic_show_password_24dp;
            if (mHidePwdResId == -1)
                mHidePwdResId = R.drawable.x_et_svg_ic_hide_password_24dp;

            int tId = isPwdShow ? mShowPwdResId : mHidePwdResId;
            mTogglePwdDrawable = ContextCompat.getDrawable(context, tId);
            if (mShowPwdResId == R.drawable.x_et_svg_ic_show_password_24dp ||
                    mHidePwdResId == R.drawable.x_et_svg_ic_hide_password_24dp) {
                DrawableCompat.setTint(mTogglePwdDrawable, getCurrentHintTextColor());
            }
            mTogglePwdDrawable.setBounds(0, 0, mTogglePwdDrawable.getIntrinsicWidth(),
                    mTogglePwdDrawable.getIntrinsicHeight());

            int cdId = a.getResourceId(R.styleable.XEditText_x_clearDrawable, -1);
            if (cdId == -1)
                cdId = R.drawable.x_et_svg_ic_clear_24dp;
            if (!disableClear) {
                mBitmap = getBitmapFromVectorDrawable(context, cdId,
                        cdId == R.drawable.x_et_svg_ic_clear_24dp); // clearDrawable
            }
        }

        disableEmoji = a.getBoolean(R.styleable.XEditText_x_disableEmoji, false);
        maxmum = a.getInt(R.styleable.XEditText_x_maxNum, Integer.MAX_VALUE);
        numOfDecimal = a.getInt(R.styleable.XEditText_x_numOfDecimal, 0);

        a.recycle();
    }

    private Bitmap getBitmapFromVectorDrawable(Context context, int drawableId, boolean tint) {
        Drawable drawable = AppCompatDrawableManager.get().getDrawable(context, drawableId);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = (DrawableCompat.wrap(drawable)).mutate();
        }
        if (tint)
            DrawableCompat.setTint(drawable, getCurrentHintTextColor());

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (hasFocused && mBitmap != null && isPwdInputType && !isTextEmpty()) {
            int left = getMeasuredWidth() - getPaddingRight() -
                    mTogglePwdDrawable.getIntrinsicWidth() - mBitmap.getWidth() - dp2px(4);
            int top = (getMeasuredHeight() - mBitmap.getHeight()) >> 1;
            canvas.drawBitmap(mBitmap, left, top, null);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (hasFocused && isPwdInputType && event.getAction() == MotionEvent.ACTION_UP) {
            int w = mTogglePwdDrawable.getIntrinsicWidth();
            int h = mTogglePwdDrawable.getIntrinsicHeight();
            int top = (getMeasuredHeight() - h) >> 1;
            int right = getMeasuredWidth() - getPaddingRight();
            boolean isAreaX = event.getX() <= right && event.getX() >= right - w;
            boolean isAreaY = event.getY() >= top && event.getY() <= top + h;
            if (isAreaX && isAreaY) {
                isPwdShow = !isPwdShow;
                if (isPwdShow) {
                    setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                } else {
                    setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
                setSelection(getSelectionStart(), getSelectionEnd());
                mTogglePwdDrawable = ContextCompat.getDrawable(getContext(), isPwdShow ?
                        mShowPwdResId : mHidePwdResId);
                if (mShowPwdResId == R.drawable.x_et_svg_ic_show_password_24dp ||
                        mHidePwdResId == R.drawable.x_et_svg_ic_hide_password_24dp) {
                    DrawableCompat.setTint(mTogglePwdDrawable, getCurrentHintTextColor());
                }
                mTogglePwdDrawable.setBounds(0, 0, mTogglePwdDrawable.getIntrinsicWidth(),
                        mTogglePwdDrawable.getIntrinsicHeight());

                setCompoundDrawables(getCompoundDrawables()[0], getCompoundDrawables()[1],
                        mTogglePwdDrawable, getCompoundDrawables()[3]);

                invalidate();
            }

            if (!disableClear) {
                right -= w + dp2px(4);
                isAreaX = event.getX() <= right && event.getX() >= right - mBitmap.getWidth();
                if (isAreaX && isAreaY) {
                    setError(null);
                    setText("");
                }
            }
        }

        if (hasFocused && !disableClear && !isPwdInputType && event.getAction() == MotionEvent.ACTION_UP) {
            Rect rect = mClearDrawable.getBounds();
            int rectW = rect.width();
            int rectH = rect.height();
            int top = (getMeasuredHeight() - rectH) >> 1;
            int right = getMeasuredWidth() - getPaddingRight();
            boolean isAreaX = event.getX() <= right && event.getX() >= right - rectW;
            boolean isAreaY = event.getY() >= top && event.getY() <= (top + rectH);
            if (isAreaX && isAreaY) {
                setError(null);
                setText("");
            }
        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        ClipboardManager clipboardManager = (ClipboardManager) getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);

        if (id == 16908320 || id == 16908321) { // catch CUT or COPY ops
            super.onTextContextMenuItem(id);

            ClipData clip = clipboardManager.getPrimaryClip();
            ClipData.Item item = clip.getItemAt(0);
            if (item != null && item.getText() != null) {
                String s = item.getText().toString().replace(mSeparator, "");
                clipboardManager.setPrimaryClip(ClipData.newPlainText(null, s));

                return true;
            }

        } else if (id == 16908322) { // catch PASTE ops
            ClipData clip = clipboardManager.getPrimaryClip();
            ClipData.Item item = clip.getItemAt(0);
            if (item != null && item.getText() != null) {
                setTextToSeparate((getText().toString() + item.getText().toString()).replace(mSeparator, ""));

                return true;
            }
        }

        return super.onTextContextMenuItem(id);
    }

    // =========================== MyTextWatcher ================================
    private class MyTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            mPreLength = s.length();
            if (mXTextChangeListener != null) {
                mXTextChangeListener.beforeTextChanged(s, start, count, after);
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (mXTextChangeListener != null) {
                mXTextChangeListener.onTextChanged(s, start, before, count);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (mXTextChangeListener != null) {
                mXTextChangeListener.afterTextChanged(s);
            }

            int currLength = s.length();
            if (hasNoSeparator) {
                mMaxLength = currLength;
            }

            markerFocusChangeLogic();

            if (currLength > mMaxLength) {
                getText().delete(currLength - 1, currLength);
                return;
            }
            if (pattern == null) {
                return;
            }

            for (int i = 0; i < pattern.length; i++) {
                if (currLength - 1 == intervals[i]) {
                    if (currLength > mPreLength) { // inputting
                        if (currLength < mMaxLength) {
                            removeTextChangedListener(mTextWatcher);
                            getText().insert(currLength - 1, mSeparator);
                        }
                    } else if (mPreLength <= mMaxLength) { // deleting
                        removeTextChangedListener(mTextWatcher);
                        getText().delete(currLength - 1, currLength);
                    }

                    addTextChangedListener(mTextWatcher);

                    break;
                }
            }
        }
    }

    private void markerFocusChangeLogic() {
        if (!hasFocused || (isTextEmpty() && !isPwdInputType)) {
            setCompoundDrawables(getCompoundDrawables()[0], getCompoundDrawables()[1],
                    null, getCompoundDrawables()[3]);

            if (!isTextEmpty() && isPwdInputType) {
                invalidate();
            }
        } else {
            if (isPwdInputType) {
                if (mShowPwdResId == R.drawable.x_et_svg_ic_show_password_24dp ||
                        mHidePwdResId == R.drawable.x_et_svg_ic_hide_password_24dp) {
                    DrawableCompat.setTint(mTogglePwdDrawable, getCurrentHintTextColor());
                }
                setCompoundDrawables(getCompoundDrawables()[0], getCompoundDrawables()[1],
                        mTogglePwdDrawable, getCompoundDrawables()[3]);
            } else if (!isTextEmpty() && !disableClear) {
                setCompoundDrawables(getCompoundDrawables()[0], getCompoundDrawables()[1],
                        mClearDrawable, getCompoundDrawables()[3]);
            }
        }
    }

    private boolean isTextEmpty() {
        return getText().toString().trim().length() == 0;
    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                Resources.getSystem().getDisplayMetrics());
    }

    /**
     * set customize separator
     */
    public void setSeparator(@NonNull String separator) {
        this.mSeparator = separator;

        if (mSeparator.length() > 0) {
            int inputType = getInputType();
            if (inputType == 2 || inputType == 8194 || inputType == 4098) { // if inputType is number, it can't insert mSeparator.
                setInputType(InputType.TYPE_CLASS_PHONE);
            }
        }
    }

    /**
     * set customize pattern
     *
     * @param pattern   e.g. pattern:{4,4,4,4}, separator:"-" to xxxx-xxxx-xxxx-xxxx
     * @param separator separator
     */
    public void setPattern(@NonNull int[] pattern, @NonNull String separator) {
        setSeparator(separator);
        setPattern(pattern);
    }

    /**
     * set customize pattern
     *
     * @param pattern e.g. pattern:{4,4,4,4}, separator:"-" to xxxx-xxxx-xxxx-xxxx
     */
    public void setPattern(@NonNull int[] pattern) {
        this.pattern = pattern;

        intervals = new int[pattern.length];
        int count = 0;
        int sum = 0;
        for (int i = 0; i < pattern.length; i++) {
            sum += pattern[i];
            intervals[i] = sum + count;
            if (i < pattern.length - 1) {
                count += mSeparator.length();
            }
        }
        mMaxLength = intervals[intervals.length - 1];

        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(mMaxLength);
        setFilters(filters);
    }

    /**
     * set CharSequence to separate
     */
    public void setTextToSeparate(@NonNull CharSequence c) {
        if (c.length() == 0) {
            return;
        }

        setText("");
        for (int i = 0; i < c.length(); i++) {
            append(c.subSequence(i, i + 1));
        }
    }

    /**
     * Get text without separators.
     * <p>
     * Deprecated, use {@link #getTrimmedString()} instead.
     */
    @Deprecated
    public String getNonSeparatorText() {
        return getText().toString().replaceAll(mSeparator, "");
    }

    /**
     * Get text String having been trimmed.
     */
    public String getTrimmedString() {
        if (hasNoSeparator) {
            return getText().toString().trim();
        } else {
            return getText().toString().replaceAll(mSeparator, "").trim();
        }
    }

    /**
     * @return has separator or not
     */
    public boolean hasNoSeparator() {
        return hasNoSeparator;
    }

    /**
     * @param hasNoSeparator true, has no separator, the same as EditText
     */
    public void setHasNoSeparator(boolean hasNoSeparator) {
        this.hasNoSeparator = hasNoSeparator;
        if (hasNoSeparator) {
            mSeparator = "";
        }
    }

    /**
     * set true to disable Emoji and special symbol
     *
     * @param disableEmoji true: disable emoji;
     *                     false: enable emoji
     */
    public void setDisableEmoji(boolean disableEmoji) {
        this.disableEmoji = disableEmoji;
        if (disableEmoji) {
            setFilters(new InputFilter[]{new EmojiExcludeFilter()});
        } else {
            setFilters(new InputFilter[0]);
        }
    }

    /**
     * the same as EditText.addOnTextChangeListener(TextWatcher textWatcher)
     */
    public void setOnXTextChangeListener(OnXTextChangeListener listener) {
        this.mXTextChangeListener = listener;
    }

    public interface OnXTextChangeListener {

        void beforeTextChanged(CharSequence s, int start, int count, int after);

        void onTextChanged(CharSequence s, int start, int before, int count);

        void afterTextChanged(Editable s);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("save_instance", super.onSaveInstanceState());
        bundle.putString("separator", mSeparator);
        bundle.putIntArray("pattern", pattern);
        bundle.putBoolean("hasNoSeparator", hasNoSeparator);

        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            mSeparator = bundle.getString("separator");
            pattern = bundle.getIntArray("pattern");
            hasNoSeparator = bundle.getBoolean("hasNoSeparator");

            if (pattern != null) {
                setPattern(pattern);
            }
            super.onRestoreInstanceState(bundle.getParcelable("save_instance"));

            return;
        }

        super.onRestoreInstanceState(state);
    }

    /**
     * disable emoji and special symbol input
     */
    private class EmojiExcludeFilter implements InputFilter {

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            for (int i = start; i < end; i++) {
                int type = Character.getType(source.charAt(i));
                if (type == Character.SURROGATE || type == Character.OTHER_SYMBOL) {
                    return "";
                }
            }
            return null;
        }
    }
    /**
     * 限制输入的方法
     */

    /**
     * 设置允许输入的最大字符数
     */
    public void setMaxLengthFilter(int maxLength) {
        inputFilters.add(new InputFilter.LengthFilter(maxLength));
        setFilters(inputFilters.toArray(new InputFilter[inputFilters.size()]));
    }

    /**
     * 设置可输入小数位
     */
    public void setDecimalFilter(int num) {
        inputFilters.add(new SignedDecimalFilter(0, num));
        setFilters(inputFilters.toArray(new InputFilter[inputFilters.size()]));
    }

    /**
     * 设置最大值
     *
     * @param maxmum       允许的最大值
     * @param numOfDecimal 允许的小数位
     */
    public void setMaxmumFilter(double maxmum, int numOfDecimal) {
        inputFilters.add(new MaxmumFilter(0, maxmum, numOfDecimal));
        setFilters(inputFilters.toArray(new InputFilter[inputFilters.size()]));
    }

    /**
     * 设置只能输入整数
     *
     * @param min 输入整数的最小值
     */
    public void setIntergerFilter(int min) {
        inputFilters.add(new SignedIntegerFilter(min));
        setFilters(inputFilters.toArray(new InputFilter[inputFilters.size()]));
    }

    /**
     * 只能够输入手机号码
     */
    public void setTelFilter() {
        inputFilters.add(new TelephoneNumberInputFilter());
        setFilters(inputFilters.toArray(new InputFilter[inputFilters.size()]));
    }

    /**
     * 小数位数限制
     */
    private static final class SignedDecimalFilter implements InputFilter {

        private final Pattern pattern;

        SignedDecimalFilter(int min, int numOfDecimals) {
            pattern = Pattern.compile("^" + (min < 0 ? "-?" : "")
                    + "[0-9]*\\.?[0-9]" + (numOfDecimals > 0 ? ("{0," + numOfDecimals + "}$") : "*"));
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (source.equals(".")) {
                if (dstart == 0 || !(dest.charAt(dstart - 1) >= '0' && dest.charAt(dstart - 1) <= '9') || dest.charAt(0) == '0') {
                    return "";
                }
            }
            if (source.equals("0") && (dest.toString()).contains(".") && dstart == 0) { //防止在369.369的最前面输入0变成0369.369这种不合法的形式
                return "";
            }
            StringBuilder builder = new StringBuilder(dest);
            builder.delete(dstart, dend);
            builder.insert(dstart, source);
            if (!pattern.matcher(builder.toString()).matches()) {
                return "";
            }

            return source;
        }
    }

    /**
     * 限制输入最大值
     */
    private static final class MaxmumFilter implements InputFilter {

        private final Pattern pattern;
        private final double maxNum;

        MaxmumFilter(int min, double maxNum, int numOfDecimals) {
            pattern = Pattern.compile("^" + (min < 0 ? "-?" : "")
                    + "[0-9]*\\.?[0-9]" + (numOfDecimals > 0 ? ("{0," + numOfDecimals + "}$") : "*"));
            this.maxNum = maxNum;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (source.equals(".")) {
                if (dstart == 0 || !(dest.charAt(dstart - 1) >= '0' && dest.charAt(dstart - 1) <= '9') || dest.charAt(0) == '0') {
                    return "";
                }
            }
            if (source.equals("0") && (dest.toString()).contains(".") && dstart == 0) {
                return "";
            }

            StringBuilder builder = new StringBuilder(dest);
            builder.delete(dstart, dend);
            builder.insert(dstart, source);
            if (!pattern.matcher(builder.toString()).matches()) {
                return "";
            }

            if (!TextUtils.isEmpty(builder)) {
                double num = Double.parseDouble(builder.toString());
                if (num > maxNum) {
                    return "";
                }
            }
            return source;
        }
    }

    /**
     * 限制整数
     */
    private static final class SignedIntegerFilter implements InputFilter {
        private final Pattern pattern;

        SignedIntegerFilter(int min) {
            pattern = Pattern.compile("^" + (min < 0 ? "-?" : "") + "[0-9]*$");
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            StringBuilder builder = new StringBuilder(dest);
            builder.insert(dstart, source);
            if (!pattern.matcher(builder.toString()).matches()) {
                return "";
            }
            return source;
        }
    }

    /**
     * 限制电话号码
     */
    private static class TelephoneNumberInputFilter implements InputFilter {

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            StringBuilder builder = new StringBuilder(dest);
            builder.insert(dstart, source);
            int length = builder.length();
            if (length == 1) {
                if (builder.charAt(0) == '1') {
                    return source;
                } else {
                    return "";
                }
            }

            if (length > 0 && length <= 11) {
                int suffixSize = length - 2;
                Pattern pattern = Pattern.compile("^1[3-9]\\d{" + suffixSize + "}$");
                if (pattern.matcher(builder.toString()).matches()) {
                    return source;
                } else {
                    return "";
                }
            }

            return "";
        }
    }
}