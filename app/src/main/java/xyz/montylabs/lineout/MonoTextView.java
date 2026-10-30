package xyz.montylabs.lineout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;

/** TextView that always renders in one of the Monty Labs brand fonts. */
public class MonoTextView extends TextView {

    public MonoTextView(Context context) {
        super(context);
        init(context, null);
    }

    public MonoTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MonoTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        int which = Fonts.PLEX;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MonoTextView);
            which = a.getInt(R.styleable.MonoTextView_mfont, Fonts.PLEX);
            a.recycle();
        }
        if (!isInEditMode()) {
            setTypeface(Fonts.get(context, which));
        }
    }
}
