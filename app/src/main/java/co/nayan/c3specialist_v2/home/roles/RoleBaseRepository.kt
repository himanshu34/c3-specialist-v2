package co.nayan.c3specialist_v2.home.roles

import co.nayan.c3specialist_v2.storage.LearningVideoSharedStorage
import co.nayan.c3specialist_v2.storage.SharedStorage
import javax.inject.Inject

class RoleBaseRepository @Inject constructor(
    private val learningVideoSharedStorage: LearningVideoSharedStorage,
    private val sharedStorage: SharedStorage
) {
    private fun getUID() = sharedStorage.getUserProfileInfo()?.email ?: ""

    fun saveLearningVideoCompletedFor(applicationModeName: String?) {
        if (applicationModeName.isNullOrEmpty()) return
        learningVideoSharedStorage.saveLearningVideoCompletedFor(
            learningVideoSharedStorage.learningVideosCompletedFor(),
            getUID(),
            applicationModeName
        )
    }

    fun setCanvasRole(role: String) {
        sharedStorage.setRoleForCanvas(role)
    }

    fun currentRole(): String? = sharedStorage.getRoleForCanvas()
}