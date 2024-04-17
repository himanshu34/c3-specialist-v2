package co.nayan.c3specialist_v2.utils

import co.nayan.c3v2.core.models.RecordAnnotation
import co.nayan.c3v2.core.models.RecordJudgment
import co.nayan.c3v2.core.models.RecordReview
import co.nayan.c3v2.core.models.c3_module.requests.SubmitAnnotationsRequest
import co.nayan.c3v2.core.models.c3_module.requests.SubmitJudgmentsRequest
import co.nayan.c3v2.core.models.c3_module.requests.SubmitReviewRequest

fun List<RecordJudgment>.createJudgmentRequest(): SubmitJudgmentsRequest {
    val sniffingCorrect = mutableListOf<Int>()
    val sniffingIncorrect = mutableListOf<Int>()
    val recordJudgments = mutableListOf<RecordJudgment>()

    forEach {
        val recordId = it.dataRecordId
        if (it.isSniffing == true && recordId != null) {
            if (it.isSniffingCorrect == true) {
                sniffingCorrect.add(recordId)
            } else {
                sniffingIncorrect.add(recordId)
            }
        } else {
            recordJudgments.add(
                RecordJudgment(
                    recordAnnotationId = it.recordAnnotationId,
                    judgment = it.judgment
                )
            )
        }
    }

    return SubmitJudgmentsRequest(
        judgments = recordJudgments,
        sniffingCorrect = sniffingCorrect,
        sniffingIncorrect = sniffingIncorrect
    )
}

fun List<RecordAnnotation>.createAnnotationRequest(): SubmitAnnotationsRequest {
    val sniffingAnnotations = filter { it.isSniffing == true }
    val sniffingCorrect =
        sniffingAnnotations.filter { it.isSniffingCorrect == true }.map { it.dataRecordId }
    val sniffingIncorrect =
        sniffingAnnotations.filter { it.isSniffingCorrect == false }.map { it.dataRecordId }
    val recordAnnotations = filter { it.isSniffing == false }.map {
        RecordAnnotation(
            it.dataRecordId,
            it.annotationObjectsAttributes
        )
    }

    return SubmitAnnotationsRequest(
        annotations = recordAnnotations,
        sniffingCorrect = sniffingCorrect,
        sniffingIncorrect = sniffingIncorrect
    )
}

fun List<RecordReview>.createReviewRequest(): SubmitReviewRequest {
    val sniffingCorrect = mutableListOf<Int>()
    val sniffingIncorrect = mutableListOf<Int>()
    val approvedReviews = mutableListOf<Int>()
    val rejectedReviews = mutableListOf<Int>()

    forEach {
        val recordId = it.recordId
        if (it.isSniffing == true) {
            if (it.isSniffingCorrect == true) sniffingCorrect.add(recordId)
            else sniffingIncorrect.add(recordId)
        } else {
            if (it.review) approvedReviews.add(recordId)
            else rejectedReviews.add(recordId)
        }
    }

    return SubmitReviewRequest(
        approved = approvedReviews,
        rejected = rejectedReviews,
        sniffingCorrect = sniffingCorrect,
        sniffingIncorrect = sniffingIncorrect
    )
}