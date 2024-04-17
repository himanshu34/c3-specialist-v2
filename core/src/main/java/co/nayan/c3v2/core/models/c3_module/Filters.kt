package co.nayan.c3v2.core.models.c3_module

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PresentedTransactionFilters(
    val isProcessedPresent: Boolean,
    val isFailedPresent: Boolean,
    val isInitiatedPresent: Boolean,
    val isCreatedPresent: Boolean
) : Parcelable
