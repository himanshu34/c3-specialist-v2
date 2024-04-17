package co.nayan.canvas.config

object ErrorCode {
    const val UNPROCESSED_ENTITY = 422
    const val DOWNLOAD_FAILED = "RVD-50"
    const val DOWNLOAD_CORRUPTED = "RVD-1"
    const val PROCESSING_FAILED = "RVP-50"
    const val PROCESSING_CORRUPTED = "RVP-1"
}

object TrainingStatus {
    const val IN_PROGRESS = "in_progress"
    const val SUCCESS = "success"
    const val FAILED = "failed"
}

object Timer {
    const val START_TIME_IN_MILLIS = 30000L
}

object Thresholds {
    const val CROP_ERROR_IGNORANCE_THRESHOLD = 5
}

object Dictionary {
    val dictionaryList = listOf(
        DictionaryData("A", false),
        DictionaryData("B", false),
        DictionaryData("C", false),
        DictionaryData("D", false),
        DictionaryData("E", false),
        DictionaryData("F", false),
        DictionaryData("G", false),
        DictionaryData("H", false),
        DictionaryData("I", false),
        DictionaryData("J", false),
        DictionaryData("K", false),
        DictionaryData("L", false),
        DictionaryData("M", false),
        DictionaryData("N", false),
        DictionaryData("O", false),
        DictionaryData("P", false),
        DictionaryData("Q", false),
        DictionaryData("R", false),
        DictionaryData("S", false),
        DictionaryData("T", false),
        DictionaryData("U", false),
        DictionaryData("V", false),
        DictionaryData("W", false),
        DictionaryData("X", false),
        DictionaryData("Y", false),
        DictionaryData("Z", false)
    )

    data class DictionaryData(
        val alphabet: String,
        var isSelected: Boolean
    )
}