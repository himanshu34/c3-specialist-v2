package co.nayan.c3v2.core.models.c3_module.responses

data class TeamMemberResponse(
    val members: List<Member>?,
    val pending: List<Member>?,
    val rejected: List<Member>?
)

data class Member(
    val id: Int?,
    val name: String?,
    val email: String?,
    val phoneNumber: String?
)

fun String?.initials(): String {
    if (this.isNullOrEmpty()) return "YN"
    val list = this.trim().split(" ")
    val firstCharSeq = StringBuilder()
    list.forEach {
        if (it.isNotEmpty())
            firstCharSeq.append(it.first())
    }

    return firstCharSeq.toString()
}
