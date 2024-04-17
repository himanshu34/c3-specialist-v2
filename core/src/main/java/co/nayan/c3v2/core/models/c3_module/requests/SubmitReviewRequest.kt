package co.nayan.c3v2.core.models.c3_module.requests

data class SubmitReviewRequest(
    val rejected: List<Int>,
    val approved: List<Int>,
    val sniffingCorrect: List<Int>?,
    val sniffingIncorrect: List<Int>?
)