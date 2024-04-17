package co.nayan.canvas.views

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AutoFitRecyclerView(context: Context, attrs: AttributeSet) : RecyclerView(context, attrs) {

    private val gridLayoutManager = GridLayoutManager(context, 1)
    private var columnWidth = -1

    init {
        val attrsArray = intArrayOf(android.R.attr.columnWidth)
        val array = context.obtainStyledAttributes(attrs, attrsArray)
        columnWidth = array.getDimensionPixelSize(0, -1)
        array.recycle()
        layoutManager = gridLayoutManager
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (columnWidth > 0) {
            val spanCount = 1.coerceAtLeast(measuredWidth / columnWidth)
            gridLayoutManager.spanCount = spanCount
        }
    }
}