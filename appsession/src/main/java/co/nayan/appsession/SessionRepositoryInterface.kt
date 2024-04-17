package co.nayan.appsession

interface SessionRepositoryInterface {
    suspend fun submitSessions(toSync: List<List<Any>>): Boolean
    fun userPhoneNumber(): String?
}