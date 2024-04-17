package co.nayan.canvas.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment

fun Fragment.hideKeyBoard() {
    val imm = requireContext().invokeSystemService<InputMethodManager>(Context.INPUT_METHOD_SERVICE)
    imm.hideSoftInputFromWindow((requireActivity().currentFocus ?: View(context)).windowToken, 0)
}

inline fun <reified T> Context.invokeSystemService(service: String): T =
    this.getSystemService(service) as T
