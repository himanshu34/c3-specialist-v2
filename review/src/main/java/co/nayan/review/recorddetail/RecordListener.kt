package co.nayan.review.recorddetail

import co.nayan.review.recordsgallery.RecordItem

interface RecordListener {
    fun onApproveClicked(recordItem: RecordItem)
    fun onResetClicked(recordItem: RecordItem)
}