package com.peiyu.reader.ui.widget.recycler


import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Created by GKF on 2018/3/16.
 */
@Suppress("MemberVisibilityCanBePrivate")
class ItemTouchCallback(private val callback: Callback) : ItemTouchHelper.Callback() {

    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    /**
     * 是否可以拖拽
     */
    var isCanDrag = false

    /**
     * 是否可以被滑�?     */
    var isCanSwipe = false

    /**
     * 当Item被长按的时候是否可以被拖拽
     */
    override fun isLongPressDragEnabled(): Boolean {
        return isCanDrag
    }

    /**
     * Item是否可以被滑�?H：左右滑动，V：上下滑�?
     */
    override fun isItemViewSwipeEnabled(): Boolean {
        return isCanSwipe
    }

    /**
     * 当用户拖拽或者滑动Item的时候需要我们告诉系统滑动或者拖拽的方向
     */
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is GridLayoutManager) {// GridLayoutManager
            // flag如果值是0，相当于这个功能被关�?            val dragFlag =
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN
            val swipeFlag = 0
            // create make
            return makeMovementFlags(dragFlag, swipeFlag)
        } else if (layoutManager is LinearLayoutManager) {// linearLayoutManager
            val linearLayoutManager = layoutManager as LinearLayoutManager?
            val orientation = linearLayoutManager!!.orientation

            var dragFlag = 0
            var swipeFlag = 0

            // 为了方便理解，相当于分为横着的ListView和竖着的ListView
            if (orientation == LinearLayoutManager.HORIZONTAL) {// 如果是横向的布局
                swipeFlag = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                dragFlag = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            } else if (orientation == LinearLayoutManager.VERTICAL) {// 如果是竖向的布局，相当于ListView
                dragFlag = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                swipeFlag = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            }
            return makeMovementFlags(dragFlag, swipeFlag)
        }
        return 0
    }

    /**
     * 当Item被拖拽的时候被回调
     *
     * @param recyclerView     recyclerView
     * @param srcViewHolder    拖拽的ViewHolder
     * @param targetViewHolder 目的地的viewHolder
     */
    override fun onMove(
        recyclerView: RecyclerView,
        srcViewHolder: RecyclerView.ViewHolder,
        targetViewHolder: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition: Int = srcViewHolder.bindingAdapterPosition
        val toPosition: Int = targetViewHolder.bindingAdapterPosition
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                callback.swap(i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                callback.swap(i, i - 1)
            }
        }
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        callback.onSwiped(viewHolder.bindingAdapterPosition)
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        val swiping = actionState == ItemTouchHelper.ACTION_STATE_DRAG
        swipeRefreshLayout?.isEnabled = !swiping
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        callback.onClearView(recyclerView, viewHolder)
    }

    interface Callback {

        /**
         * 当某个Item被滑动删除的时�?         *
         * @param adapterPosition item的position
         */
        fun onSwiped(adapterPosition: Int) {

        }

        /**
         * 当两个Item位置互换的时候被回调
         *
         * @param srcPosition    拖拽的item的position
         * @param targetPosition 目的地的Item的position
         * @return 开发者处理了操作应该返回true，开发者没有处理就返回false
         */
        fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            return true
        }

        /**
         * 手指松开
         */
        fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {

        }

    }
}
