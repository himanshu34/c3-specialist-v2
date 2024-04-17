package co.nayan.c3v2.core.models.c3_module.requests

import co.nayan.c3v2.core.models.RecordAnnotation
import co.nayan.c3v2.core.models.RecordJudgment

data class SubmitJudgmentsRequest(
    val judgments: List<RecordJudgment>,
    val sniffingCorrect: List<Int>?,
    val sniffingIncorrect: List<Int>?
)

data class SubmitAnnotationsRequest(
    val annotations: List<RecordAnnotation>,
    val sniffingCorrect: List<Int>?,
    val sniffingIncorrect: List<Int>?
)