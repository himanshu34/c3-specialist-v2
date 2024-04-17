package co.nayan.c3specialist_v2.storage

import android.content.Context
import androidx.core.content.edit
import co.nayan.c3v2.core.fromPrettyJsonList
import co.nayan.c3v2.core.models.RecordAnnotation
import co.nayan.c3v2.core.models.RecordJudgment
import co.nayan.c3v2.core.models.RecordReview
import co.nayan.c3v2.core.toPrettyJson
import javax.inject.Inject

class ManagerSharedStorage @Inject constructor(
    context: Context
) : SharedStorage(context) {

    override fun addAnnotation(annotation: RecordAnnotation) {
        val unsynced = mutableListOf<RecordAnnotation>()
        unsynced.addAll(getUnsyncedAnnotations())
        unsynced.add(annotation)
        syncAnnotations(unsynced)
    }

    override fun clearAnnotations(toRemove: List<RecordAnnotation>) {
        val unSynced = getUnsyncedAnnotations().toMutableList()
        unSynced.removeAll(toRemove)
        syncAnnotations(unSynced)
    }

    override fun clearReviews(toRemove: List<RecordReview>) {
        val unSynced = getUnsyncedReviews().toMutableList()
        unSynced.removeAll(toRemove)
        syncReviews(unSynced)
    }

    override fun addAllAnnotations(toSync: List<RecordAnnotation>) {
        val unSynced = mutableListOf<RecordAnnotation>()
        unSynced.addAll(getUnsyncedAnnotations())
        unSynced.addAll(toSync)
        syncAnnotations(unSynced)
    }

    override fun addAllReviews(toSync: List<RecordReview>) {
        val unSynced = mutableListOf<RecordReview>()
        unSynced.addAll(getUnsyncedReviews())
        unSynced.addAll(toSync)
        syncReviews(unSynced)
    }

    override fun undoAnnotation(): RecordAnnotation? {
        var lastRecordAnnotation: RecordAnnotation? = null
        val unSynced = mutableListOf<RecordAnnotation>()
        unSynced.addAll(getUnsyncedAnnotations())
        if (unSynced.isNotEmpty()) {
            lastRecordAnnotation = unSynced.last()
            unSynced.remove(lastRecordAnnotation)
        }
        syncAnnotations(unSynced)
        return lastRecordAnnotation
    }

    override fun syncAnnotations(toSync: List<RecordAnnotation>) {
        sharedPreferences.edit { putString(UNSYNCED_ANNOTATIONS, toSync.toPrettyJson()) }
    }

    override fun getUnsyncedAnnotations(): MutableList<RecordAnnotation> {
        val stringValue = sharedPreferences.getString(UNSYNCED_ANNOTATIONS, "[]")
        return stringValue?.fromPrettyJsonList() ?: run { mutableListOf() }
    }

    override fun addJudgment(judgement: RecordJudgment) {
        val unSynced = mutableListOf<RecordJudgment>()
        unSynced.addAll(getUnsyncedJudgments())
        unSynced.add(judgement)
        syncJudgments(unSynced)
    }

    override fun addReview(review: RecordReview) {
        val unSynced = mutableListOf<RecordReview>()
        unSynced.addAll(getUnsyncedReviews())
        unSynced.add(review)
        syncReviews(unSynced)
    }

    override fun clearJudgments(toRemove: List<RecordJudgment>) {
        val unSynced = getUnsyncedJudgments().toMutableList()
        unSynced.removeAll(toRemove)
        syncJudgments(unSynced)
    }

    override fun addAllJudgments(toSync: List<RecordJudgment>) {
        val unSynced = mutableListOf<RecordJudgment>()
        unSynced.addAll(getUnsyncedJudgments())
        unSynced.addAll(toSync)
        syncJudgments(unSynced)
    }

    override fun undoJudgment() {
        val unSynced = mutableListOf<RecordJudgment>()
        unSynced.addAll(getUnsyncedJudgments())
        if (unSynced.isNotEmpty()) {
            unSynced.remove(unSynced.last())
        }
        syncJudgments(unSynced)
    }

    override fun getUnsyncedJudgments(): List<RecordJudgment> {
        val stringValue = sharedPreferences.getString(UNSYNCED_JUDGMENTS, "[]")
        return stringValue?.fromPrettyJsonList() ?: run { listOf() }
    }

    override fun syncJudgments(toSync: List<RecordJudgment>) {
        sharedPreferences.edit {
            putString(UNSYNCED_JUDGMENTS, toSync.toPrettyJson()).apply()
        }
    }

    override fun undoReview() {
        val unSynced = mutableListOf<RecordReview>()
        unSynced.addAll(getUnsyncedReviews())
        if (unSynced.isNotEmpty()) {
            unSynced.remove(unSynced.last())
        }
        syncReviews(unSynced)
    }

    override fun getUnsyncedReviews(): List<RecordReview> {
        val stringValue = sharedPreferences.getString(UNSYNCED_REVIEWS, "[]")
        return stringValue?.fromPrettyJsonList() ?: run { listOf() }
    }

    override fun syncReviews(toSync: List<RecordReview>) {
        sharedPreferences.edit {
            putString(UNSYNCED_REVIEWS, toSync.toPrettyJson()).apply()
        }
    }

    companion object {
        const val UNSYNCED_JUDGMENTS = "manager_judgments"
        const val UNSYNCED_REVIEWS = "manager_reviews"
        const val UNSYNCED_ANNOTATIONS = "manager_annotations"
    }
}