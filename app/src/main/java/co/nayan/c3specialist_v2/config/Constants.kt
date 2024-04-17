package co.nayan.c3specialist_v2.config

object OTPRequestState {
    const val EVENT_SEND_OTP = 1
    const val EVENT_VALIDATE_OTP = 2
    const val EVENT_RESEND_OTP = 3
    const val EVENT_CHANGE_NUMBER = 4
}

object KycStatus {
    const val NOT_UPLOADED = "not_uploaded"
    const val REJECTED = "rejected"
    const val PENDING_VERIFICATION = "pending_verification"
    const val VERIFIED = "verified"
}

object AppLanguage {
    const val ENGLISH = "en"
    const val HINDI = "hi"
}

object KycType {
    const val PAN = "PAN"
    const val PHOTO_ID = "PhotoID"
}

object TransactionStatus {
    const val CREATED = "created"
    const val INITIATED = "initiated"
    const val FAILURE = "failure"
    const val PROCESSED = "processed"
}

object WalletType {
    const val BONUS = "wallet_type_bonus"
    const val SPECIALIST = "wallet_type_specialist"
    const val MANAGER = "wallet_type_manager"
    const val DRIVER = "wallet_type_driver"
    const val LEADER = "wallet_type_leader"
    const val REFERRAL = "wallet_type_referal"
    const val REFERRAL_NETWORK = "wallet_type_referal_network"
}

object Extras {
    const val SANDBOX_REQUIRED = "sandbox_required"
    const val SELECTED_FAQ_DATA_ID = "selected_faq_data_id"
    const val WF_STEP_ID = "wf_step_id"
    const val RECORD_ID = "record_id"
    const val QUESTION = "question"
    const val RECORD = "record"
    const val USER_ID = "user_id"
    const val USER_NAME = "user_name"
    const val PERFORMANCE = "performance"
    const val LEARNING_VIDEOS_CATEGORIES = "learning_videos_categories"
    const val PERMISSION_DENIED = "permission_denied"
    const val IS_ADMIN = "isAdmin"
    const val USER_ROLE = "userRole"
    const val WORK_TYPE = "workType"
    const val WORK_ASSIGNMENT = "workAssignment"
    const val INCORRECT_ANNOTATION = "incorrectAnnotations"
    const val INCORRECT_JUDGMENT = "incorrectJudgments"
    const val INCORRECT_REVIEW = "incorrectReviews"
    const val START_DATE = "startDate"
    const val END_DATE = "endDate"
    const val WF_STEP = "wfStep"
    const val WORK_FLOW_NAME = "workFlowName"
    const val WORK_FLOW_ID = "workflowId"
    const val TRANSACTION = "transactions"
    const val UPDATED_MESSAGE = "updatedMessage"
    const val TOKEN = "token"
    const val APPLICATION_MODE = "applicationMode"
    const val REMOTE_USER_ID = "remoteUserId"
    const val REMOTE_USER_NAME = "remoteUserName"
    const val REMOTE_MESSAGE_BODY = "remoteMessageBody"
    const val IS_STORAGE_FULL = "IS_STORAGE_FULL"
    const val SHOULD_FORCE_START_HOVER = "SHOULD_FORCE_START_HOVER"
    const val COMING_FROM="COMING_FROM"
    const val SURGE_MODE ="SURGE_MODE"
}

object ProfileConstants {
    const val PERSONAL_INFO = "Personal Info"
    const val PAN = "PAN"
    const val PHOTO_ID = "Photo ID"
    const val BANK_DETAILS = "Bank Details"
    const val RESET_PASSWORD = "Password"
    const val SETTINGS = "Settings"
    const val REFERRAL ="Referral"
}

object Regex {
    const val MOBILE = "[6-9][0-9]{9}"
}

object Tag {
    const val CALL_INIT_ERROR = "Call Init Error"
    const val SEND_REQUEST = "Send Request"
    const val DOWNLOAD_FAILED = "Download Failed"
    const val EXTRACTION_FAILED = "Extraction Failed"
    const val TAG_RECEIPT_DOWNLOADER_TAG = "Receipt Downloader"
    const val RECEIPT_DOWNLOAD_FOLDER = "Nayan_Receipts"
}

object LearningVideosCategory {
    const val DELAYED_1 = (1 * 60 * 1000).toLong()
    const val DELAYED_60 = (60 * 60 * 1000).toLong()
    const val CURRENT_ROLE = "current_role"
    const val VIOLATION = "violation_videos"
    const val INTRODUCTION = "introduction_videos"
}

object ResultType {
    const val CORRECT = "correct"
    const val INCONCLUSIVE = "inconclusive"
    const val INCORRECT = "incorrect"
    const val NO_CONSENSUS = "no_consensus"
}

enum class MemberStatus {
    REJECTED, MEMBERS, PENDING
}

object FaqDataCategories {
    const val CORRECT = "correct"
    const val INCORRECT = "incorrect"
    const val JUNK = "junk"
}

enum class CurrentRole {
    ADMIN, DRIVER, MANAGER, SPECIALIST, LEADER, SEARCH, PROFILE, ROLEREQUEST
}