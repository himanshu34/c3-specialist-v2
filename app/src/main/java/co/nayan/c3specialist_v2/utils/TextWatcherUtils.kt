package co.nayan.c3specialist_v2.utils

import android.text.Editable
import android.text.TextWatcher

open class TextChangedListener : TextWatcher {
    override fun afterTextChanged(p0: Editable?) {}
    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
    override fun onTextChanged(char: CharSequence?, start: Int, before: Int, count: Int) {}
}