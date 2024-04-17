package co.nayan.c3v2.core.config


const val DEFAULT_BUFFER_LENGTH = 11

object Judgment {
    const val JUNK = "junk"
}

object Mode {
    const val VALIDATE = "VALIDATE"
    const val EVENT_VALIDATION = "EVENT_VALIDATION"

    const val BINARY_CLASSIFY = "BINARY_CLASSIFY"
    const val CLASSIFY = "CLASSIFY"

    const val INPUT = "INPUT"
    const val MULTI_INPUT = "MULTI_INPUT"
    const val LP_INPUT = "LP_INPUT"

    const val CROP = "CROP"
    const val MULTI_CROP = "MULTI_CROP"
    const val BINARY_CROP = "BINARY_CROP"
    const val MCMI = "MCMI"
    const val MCML = "MCML"
    const val INTERPOLATED_MCML = "INTERPOLATED_MCML"
    const val INTERPOLATED_MCMT = "INTERPOLATED_MCMT"
    const val MCMT = "MCMT"

    const val DRAG_SPLIT = "DRAG_SPLIT"
    const val QUADRILATERAL = "QUADRILATERAL"
    const val POLYGON = "POLYGON"
    const val PAINT = "PAINT"
    const val DYNAMIC_CLASSIFY = "DYNAMIC_CLASSIFY"
}

object DrawType {
    const val PENCIL = "PENCIL"
    const val POINTS = "POINTS"
    const val SELECT = "SELECT"
    const val BOUNDING_BOX = "BOUNDING_BOX"
    const val POLYGON = "POLYGON"
    const val QUADRILATERAL = "QUADRILATERAL"
    const val CONNECTED_LINE = "CONNECTED_LINE"
    const val SPLIT_BOX = "SPLIT_BOX"
    const val MASK = "MASK"
    const val JUNK = "JUNK"
}

object WorkType {
    const val ANNOTATION = "annotation"
    const val VALIDATION = "validation"
    const val REVIEW = "review"
}

object MediaType {
    const val VIDEO = "video"
    const val CLASSIFICATION_VIDEO = "classification_video"
    const val IMAGE = "image"
}

object Role {
    const val ADMIN = "admin"
    const val MANAGER = "manager"
    const val SPECIALIST = "specialist"
    const val DRIVER = "driver"
    const val DELHI_POLICE = "delhi_police"
    const val LEADER = "leader"
}