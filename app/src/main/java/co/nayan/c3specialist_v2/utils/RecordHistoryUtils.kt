package co.nayan.c3specialist_v2.utils

import co.nayan.c3v2.core.models.CurrentAnnotation
import co.nayan.c3v2.core.models.Record
import co.nayan.c3v2.core.models.RecordAnnotationHistory

fun Record.createHistoryRecord(recordHistory: RecordAnnotationHistory?): Record {
    return Record(
        id = id,
        displayImage = displayImage,
        workAssignmentId = workAssignmentId,
        parentAnnotation = parentAnnotation,
        currentAnnotation = CurrentAnnotation(
            id = recordHistory?.id,
            annotationObjects = recordHistory?.annotationObjects,
        ),
        currentJudgment = currentJudgment,
        annotation = annotation,
        starred = starred,
        isSniffingRecord = isSniffingRecord,
        needsRejection = needsRejection,
        mediaUrl = mediaUrl,
        mediaType = mediaType,
        randomSniffingId = randomSniffingId,
        applicationMode = applicationMode,
        recordAnnotations = recordAnnotations,
        questionAnnotation = questionAnnotation,
        questionValidation = questionValidation,
    )
}