package co.nayan.c3v2.core.models.c3_module.responses

import co.nayan.c3v2.core.models.Events

data class EventsResponse(
    val data: MutableList<Events>
)