package co.nayan.c3v2.login.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

abstract class BaseFragment(layoutID: Int) : Fragment(layoutID) {

    private var layout: Int = layoutID

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param cls: Fragment class
     * @return A new instance of fragment depends on cls.
     */
    open fun newInstance(cls: Class<*>, layout: Int): BaseFragment? {
        var fragment: BaseFragment? = null
        try {
            fragment = cls.newInstance() as BaseFragment
            fragment.setLayout(layout)
        } catch (e: ReflectiveOperationException) {
            e.printStackTrace()
        }
        return fragment
    }

    /**
     * Use this method to set Fragment's layout
     * this method is not dynamic, should call after fragment created.
     *
     * @param layout
     */
    open fun setLayout(layout: Int) {
        val args = Bundle()
        args.putInt(LAYOUT, layout)
        arguments = args
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            val layInt = requireArguments().getInt(LAYOUT)
            layout = if (layInt != 0) layInt else layout
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(layout, container, false)
    }

    companion object {
        const val LAYOUT = "layout"
    }
}