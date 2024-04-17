package co.nayan.c3v2.core

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

fun FragmentManager.showDialogFragment(
    fragment: DialogFragment,
    tag: String = "dialog"
): DialogFragment {
    fragment.show(this, tag)
    return fragment
}