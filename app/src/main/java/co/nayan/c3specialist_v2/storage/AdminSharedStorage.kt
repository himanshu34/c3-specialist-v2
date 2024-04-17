package co.nayan.c3specialist_v2.storage

import android.content.Context
import co.nayan.c3v2.core.fromPrettyJsonList
import co.nayan.c3v2.core.models.RecordReview
import co.nayan.c3v2.core.toPrettyJson
import javax.inject.Inject

class AdminSharedStorage @Inject constructor(
    context: Context
) : SharedStorage(context) {

    override fun clearReviews(toRemove: List<RecordReview>) {
        val unSynced = getUnsyncedReviews().toMutableList()
        unSynced.removeAll(toRemove)
        syncReviews(unSynced)
    }

    override fun addAllReviews(toSync: List<RecordReview>) {
        val unSynced = mutableListOf<RecordReview>()
        unSynced.addAll(getUnsyncedReviews())
        unSynced.addAll(toSync)
        syncReviews(unSynced)
    }

    override fun addReview(review: RecordReview) {
        val unSynced = mutableListOf<RecordReview>()
        unSynced.addAll(getUnsyncedReviews())
        unSynced.add(review)
        syncReviews(unSynced)
    }

    override fun getUnsyncedReviews(): List<RecordReview> {
        val stringValue = sharedPreferences.getString(UNSYNCED_REVIEWS, "[]")
        return stringValue?.fromPrettyJsonList() ?: run { listOf() }
    }

    override fun syncReviews(toSync: List<RecordReview>) {
        sharedPreferences.edit().putString(UNSYNCED_REVIEWS, toSync.toPrettyJson()).apply()
    }

    companion object {
        const val UNSYNCED_REVIEWS = "admin_reviews"
    }
}