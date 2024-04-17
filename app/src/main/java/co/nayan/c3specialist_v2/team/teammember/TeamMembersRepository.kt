package co.nayan.c3specialist_v2.team.teammember

import co.nayan.c3v2.core.api.factory.ApiClientFactory
import co.nayan.c3v2.core.models.c3_module.responses.TeamMemberResponse
import javax.inject.Inject

class TeamMembersRepository @Inject constructor(private val apiClientFactory: ApiClientFactory) {

    suspend fun fetchTeamMembers(): TeamMemberResponse {
        return apiClientFactory.apiClientBase.fetchTeamMembers()
    }
}