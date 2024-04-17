package co.nayan.canvas.widgets

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import co.nayan.canvas.R
import co.nayan.canvas.config.Timer.START_TIME_IN_MILLIS
import co.nayan.canvas.databinding.LayoutSniffingWarningDialogBinding
import co.nayan.canvas.viewmodels.BaseCanvasViewModel
import java.text.DecimalFormat
import java.text.NumberFormat

class SniffingIncorrectWarningDialogFragment : DialogFragment() {

    private lateinit var binding: LayoutSniffingWarningDialogBinding
    private lateinit var viewModel: BaseCanvasViewModel
    private lateinit var timer: CountDownTimer

    companion object {
        private const val MESSAGE = "message"

        fun newInstance(
            message: String
        ): SniffingIncorrectWarningDialogFragment {
            val args = Bundle()
            args.putString(MESSAGE, message)
            val f = SniffingIncorrectWarningDialogFragment()
            f.arguments = args
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, co.nayan.appsession.R.style.SessionDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return LayoutSniffingWarningDialogBinding.inflate(inflater, container, false).apply {
            binding = this
        }.root
    }

    fun setViewModel(viewModel: BaseCanvasViewModel) {
        this.viewModel = viewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        binding.messageTxt.text = arguments?.getString(MESSAGE)
        if (::viewModel.isInitialized && viewModel.mTimerRunning)
            viewModel.mTimeLeftInMillis = viewModel.mEndTime - System.currentTimeMillis()
        startTimer()
    }

    private fun startTimer() {
        if (::viewModel.isInitialized) {
            viewModel.mEndTime = System.currentTimeMillis() + viewModel.mTimeLeftInMillis
            timer = object : CountDownTimer(viewModel.mTimeLeftInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    viewModel.mTimeLeftInMillis = millisUntilFinished
                    updateCountDownText(viewModel.mTimeLeftInMillis)
                }

                override fun onFinish() {
                    viewModel.apply {
                        mTimerRunning = false
                        mTimeLeftInMillis = START_TIME_IN_MILLIS
                        mEndTime = 0
                        setInitialState()
                    }
                    binding.timerTxt.text = String.format("00:00")
                    dialog?.dismiss()
                }
            }.start()

            viewModel.mTimerRunning = true
        }
    }

    private fun updateCountDownText(mTimeLeftInMillis: Long) {
        val formatter: NumberFormat = DecimalFormat("00")
        val minutes = mTimeLeftInMillis / 60000 % 60
        val seconds = mTimeLeftInMillis / 1000 % 60
        binding.timerTxt.text =
            String.format(formatter.format(minutes) + ":" + formatter.format(seconds))
    }

    private fun stopTimer() {
        if (::timer.isInitialized) timer.cancel()
        if (::viewModel.isInitialized) viewModel.mTimerRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }
}