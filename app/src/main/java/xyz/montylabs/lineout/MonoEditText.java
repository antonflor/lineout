package xyz.montylabs.lineout;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

/** EditText in IBM Plex Mono, per the brand's "underline only / mono font" input rule. */
public class MonoEditText extends EditText {

    public MonoEditText(Context context) {
        this(context, null);
    }

    public MonoEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            setTypeface(Fonts.get(context, Fonts.PLEX));
        }
    }

    public MonoEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (!isInEditMode()) {
            setTypeface(Fonts.get(context, Fonts.PLEX));
        }
    }
}
