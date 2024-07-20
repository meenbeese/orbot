package org.torproject.android.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo

import com.google.android.material.textfield.TextInputEditText

class NoPersonalizedLearningEditText(
    context: Context,
    attrs: AttributeSet?
) : TextInputEditText(context, attrs) {
    init {
        imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }
}