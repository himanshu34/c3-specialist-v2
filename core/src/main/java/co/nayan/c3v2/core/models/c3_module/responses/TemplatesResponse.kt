package co.nayan.c3v2.core.models.c3_module.responses

import co.nayan.c3v2.core.models.Template

data class TemplatesResponse(
    val templates: List<Template>?,
    val success: Boolean,
    val message: String
)

data class AddTemplateRequest(
    val template: Template,
    val wfStepId: Int?
)