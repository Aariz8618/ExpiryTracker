package com.aariz.expirytracker

import android.text.Editable
import android.text.TextWatcher

/**
 * TextWatcher that automatically capitalizes the first letter of the text
 */
class TextCapitalizationWatcher : TextWatcher {
    private var isFormatting = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // No action needed
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // No action needed
    }

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting || s.isNullOrEmpty()) {
            return
        }

        isFormatting = true

        // Get the first character
        val firstChar = s[0]

        // Check if it's a lowercase letter
        if (firstChar.isLowerCase()) {
            // Replace with uppercase
            s.replace(0, 1, firstChar.uppercase())
        }

        isFormatting = false
    }
}

// INSTRUCTIONS FOR IMPLEMENTATION:
// 1. In screen_add_item.xml, change the EditText inputType to:
//    android:inputType="textCapSentences"
//
// 2. In AddItemActivity.kt, add after initViews():
//    inputName.addTextChangedListener(TextCapitalizationWatcher())
//
// 3. In screen_edit_item.xml, change the EditText inputType to:
//    android:inputType="textCapSentences"
//
// 4. In EditItemActivity.kt, add after initViews():
//    inputName.addTextChangedListener(TextCapitalizationWatcher())