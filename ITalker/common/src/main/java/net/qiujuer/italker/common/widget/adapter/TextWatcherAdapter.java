package net.qiujuer.italker.common.widget.adapter;

import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.widget.EditText;

import java.util.ArrayList;

/**
 * @author qiujuer Email:qiujuer@live.cn
 * @version 1.0.0
 */
public abstract class TextWatcherAdapter implements TextWatcher {

    //待删除的表情列表
    private final ArrayList<ImageSpan> mEmoticonsToRemove = new ArrayList<ImageSpan>();
    private final EditText editText;
    public TextWatcherAdapter( EditText editText){
        this.editText = editText;
    }

    //看源码注释
    @Override
    public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

        if (start == 0) return;
        if (count > 0) {            //删除
            int end = start + count;
            Editable message = editText.getEditableText();
            ImageSpan[] spans = message.getSpans(start, end, ImageSpan.class);
            if (spans == null || spans.length == 0) return;
            for (ImageSpan span : spans) {
                int spanStart = message.getSpanStart(span);
                int spanEnd = message.getSpanEnd(span);
                if ((spanStart < end) && (spanEnd > start)) {
                    // Add to remove list
                    mEmoticonsToRemove.add(span);
                }

            }
        }
    }



    //看源码注释
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    //看源码注释
    @Override
    public void afterTextChanged(Editable s) {
        Editable message = this.editText.getEditableText();

        // Commit the emoticons to be removed.
        for (ImageSpan span : mEmoticonsToRemove) {
            int start = message.getSpanStart(span);
            int end = message.getSpanEnd(span);

            // Remove the span
            message.removeSpan(span);

            // Remove the remaining emoticon text.
            if (start != end) {
                message.delete(start, end);
            }
        }
        mEmoticonsToRemove.clear();

    }
}
