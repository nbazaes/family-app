package com.familyapp.ui.components

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView

/**
 * Intercepts back presses inside ModalBottomSheet or AlertDialog when shouldDismissOnBackPress = false.
 *
 * If the software keyboard (IME) is visible, it hides the keyboard and clears focus.
 * If the keyboard is already hidden (or not open), it calls onDismiss() to close the modal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModalImeBackHandler(onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.isImeVisible || WindowInsets.ime.getBottom(density) > 0
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val imm = remember(context) {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    }

    BackHandler(enabled = true) {
        if (isImeVisible) {
            focusManager.clearFocus()
            keyboardController?.hide()
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        } else {
            onDismiss()
        }
    }
}
