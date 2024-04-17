package co.nayan.c3specialist_v2.performance.teamperformance

import android.content.Intent
import android.os.Bundle
import co.nayan.c3specialist_v2.R
import co.nayan.c3specialist_v2.config.BaseActivity
import co.nayan.c3specialist_v2.config.Extras
import co.nayan.c3specialist_v2.databinding.ActivityTeamMemberPerformanceBinding
import co.nayan.c3specialist_v2.incorrect_records_wf_steps.IncorrectRecordsWfStepsActivity
import co.nayan.c3specialist_v2.performance.models.Performance
import co.nayan.c3specialist_v2.performance.widgets.OnIncorrectClickListener
import co.nayan.c3specialist_v2.performance.widgets.PerformanceFragment
import co.nayan.c3specialist_v2.viewBinding
import co.nayan.c3v2.core.config.Role
import co.nayan.c3v2.core.config.WorkType
import co.nayan.c3v2.core.utils.parcelable
import co.nayan.c3v2.core.utils.setupActionBar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TeamMemberPerformanceActivity : BaseActivity() {

    private var userType = Role.SPECIALIST
    private val binding: ActivityTeamMemberPerformanceBinding by viewBinding(
        ActivityTeamMemberPerformanceBinding::inflate
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupActionBar(binding.actionBar.appToolbar)
        setupExtras()
    }

    private val onIncorrectClickListener = object : OnIncorrectClickListener {
        override fun onIncorrectAnnotationClicked() {
            moveToIncorrectRecordsScreen(WorkType.ANNOTATION, userType)
        }

        override fun onIncorrectJudgmentClicked() {
            moveToIncorrectRecordsScreen(WorkType.VALIDATION, userType)
        }

        override fun onIncorrectReviewClicked() {
            moveToIncorrectRecordsScreen(WorkType.REVIEW, userType)
        }
    }

    private fun moveToIncorrectRecordsScreen(workType: String, userRole: String) {
        Intent(
            this@TeamMemberPerformanceActivity, IncorrectRecordsWfStepsActivity::class.java
        ).apply {
            putExtra(Extras.START_DATE, binding.startDateTxt.text)
            putExtra(Extras.END_DATE, binding.endDateTxt.text)
            putExtra(Extras.WORK_TYPE, workType)
            putExtra(Extras.USER_ROLE, userRole)
            putExtra(Extras.USER_ID, intent.getIntExtra(Extras.USER_ID, -1))
            startActivity(this)
        }
    }

    private fun setupExtras() {
        userType = intent.getStringExtra(Extras.USER_ROLE) ?: Role.SPECIALIST
        title = intent.getStringExtra(Extras.USER_NAME) ?: "Member Performance"
        binding.startDateTxt.text = intent.getStringExtra(Extras.START_DATE)
        binding.endDateTxt.text = intent.getStringExtra(Extras.END_DATE)
        intent.parcelable<Performance>(Extras.PERFORMANCE)?.let {
            val fragment = PerformanceFragment.newInstance(it, true, onIncorrectClickListener)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment).commit()
        }
    }

    override fun showMessage(message: String) {}
}