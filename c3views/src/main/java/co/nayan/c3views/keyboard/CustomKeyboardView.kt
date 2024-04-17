package co.nayan.c3views.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View.OnClickListener
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import co.nayan.c3v2.core.utils.invisible
import co.nayan.c3v2.core.utils.rightSwipeInvisibleAnimation
import co.nayan.c3v2.core.utils.rightSwipeVisibleAnimation
import co.nayan.c3v2.core.utils.visible
import co.nayan.c3views.databinding.KeyboardBinding

class CustomKeyboardView(
    context: Context,
    attributeSet: AttributeSet
) : LinearLayout(context, attributeSet) {

    private lateinit var keyboardActionListener: KeyboardActionListener
    private var binding: KeyboardBinding

    init {
        binding = KeyboardBinding.inflate(LayoutInflater.from(context), this, true)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.alphabet.children.forEach {
            (it as ConstraintLayout).children.forEach { view ->
                when (view) {
                    is ImageButton -> view.setOnClickListener(onDeleteClickListener)
                    else -> view.setOnClickListener(onClickListener)
                }
            }
        }
        binding.numeric.children.forEach {
            (it as ConstraintLayout).children.forEach { view ->
                when (view) {
                    is ImageButton -> view.setOnClickListener(onDeleteClickListener)
                    else -> view.setOnClickListener(onClickListener)
                }
            }
        }
    }

    private val onDeleteClickListener = OnClickListener {
        when (it.id) {
            else -> {
                if (it is ImageButton) {
                    if (::keyboardActionListener.isInitialized)
                        keyboardActionListener.delete()
                }
            }
        }
    }

    private val onClickListener = OnClickListener {
        when (it.id) {
            else -> {
                if (it is TextView) {
                    when (it.text) {
                        "ABC" -> {
                            binding.alphabet.visible()
                            binding.alphabet.rightSwipeVisibleAnimation()
                            binding.numeric.rightSwipeInvisibleAnimation()
                            binding.numeric.invisible()
                        }

                        "123" -> {
                            binding.numeric.visible()
                            binding.numeric.rightSwipeVisibleAnimation()
                            binding.alphabet.rightSwipeInvisibleAnimation()
                            binding.alphabet.invisible()
                        }

                        else -> {
                            if (::keyboardActionListener.isInitialized)
                                keyboardActionListener.setValue(it.text.toString())
                        }
                    }
                }
            }
        }
    }

    fun setKeyboardActionListener(toSet: KeyboardActionListener) {
        keyboardActionListener = toSet
    }
}

interface KeyboardActionListener {
    fun setValue(value: String)
    fun delete()
}