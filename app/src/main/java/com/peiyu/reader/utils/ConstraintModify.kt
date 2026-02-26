package com.peiyu.reader.utils

import androidx.annotation.IdRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.transition.TransitionManager

@Suppress("unused")
fun ConstraintLayout.modifyBegin(withAnim: Boolean = false): ConstraintModify.ConstraintBegin {
    val begin = ConstraintModify(this).begin
    if (withAnim) {
        TransitionManager.beginDelayedTransition(this)
    }
    return begin
}

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ConstraintModify(private val constraintLayout: ConstraintLayout) {

    val begin: ConstraintBegin by lazy {
        applyConstraintSet.clone(constraintLayout)
        ConstraintBegin(constraintLayout, applyConstraintSet)
    }
    private val applyConstraintSet = ConstraintSet()
    private val resetConstraintSet = ConstraintSet()

    init {
        resetConstraintSet.clone(constraintLayout)
    }

    /**
     * 带动画的修改
     * @return
     */
    fun beginWithAnim(): ConstraintBegin {
        TransitionManager.beginDelayedTransition(constraintLayout)
        return begin
    }

    /**
     * 重置
     */
    fun reSet() {
        resetConstraintSet.applyTo(constraintLayout)
    }

    /**
     * 带动画的重置
     */
    fun reSetWidthAnim() {
        TransitionManager.beginDelayedTransition(constraintLayout)
        resetConstraintSet.applyTo(constraintLayout)
    }


    @Suppress("unused", "MemberVisibilityCanBePrivate")
    class ConstraintBegin(
        private val constraintLayout: ConstraintLayout,
        private val applyConstraintSet: ConstraintSet
    ) {

        /**
         * 清除关系,这里不仅仅会清除关系，还会清除对应控件的宽高�?w:0,h:0
         * @param viewId 视图ID
         * @return
         */
        fun clear(viewId: Int): ConstraintBegin {
            applyConstraintSet.clear(viewId)
            return this
        }

        /**
         * 清除某个控件的，某个关系
         * @param viewId 控件ID
         * @param anchor 要解除的关系
         * @return
         */
        fun clear(viewId: Int, anchor: Anchor): ConstraintBegin {
            applyConstraintSet.clear(viewId, anchor.toInt())
            return this
        }

        fun setHorizontalWeight(viewId: Int, weight: Float): ConstraintBegin {
            applyConstraintSet.setHorizontalWeight(viewId, weight)
            return this
        }

        fun setVerticalWeight(viewId: Int, weight: Float): ConstraintBegin {
            applyConstraintSet.setVerticalWeight(viewId, weight)
            return this
        }

        /**
         * 为某个控件设�?margin
         * @param viewId 某个控件ID
         * @param left marginLeft
         * @param top   marginTop
         * @param right marginRight
         * @param bottom marginBottom
         * @return
         */
        fun setMargin(
            @IdRes viewId: Int,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int
        ): ConstraintBegin {
            setMarginLeft(viewId, left)
            setMarginTop(viewId, top)
            setMarginRight(viewId, right)
            setMarginBottom(viewId, bottom)
            return this
        }

        /**
         * 为某个控件设�?marginLeft
         * @param viewId 某个控件ID
         * @param left marginLeft
         * @return
         */
        fun setMarginLeft(@IdRes viewId: Int, left: Int): ConstraintBegin {
            applyConstraintSet.setMargin(viewId, ConstraintSet.LEFT, left)
            return this
        }

        /**
         * 为某个控件设�?marginRight
         * @param viewId 某个控件ID
         * @param right marginRight
         * @return
         */
        fun setMarginRight(@IdRes viewId: Int, right: Int): ConstraintBegin {
            applyConstraintSet.setMargin(viewId, ConstraintSet.RIGHT, right)
            return this
        }

        /**
         * 为某个控件设�?marginTop
         * @param viewId 某个控件ID
         * @param top marginTop
         * @return
         */
        fun setMarginTop(@IdRes viewId: Int, top: Int): ConstraintBegin {
            applyConstraintSet.setMargin(viewId, ConstraintSet.TOP, top)
            return this
        }

        /**
         * 为某个控件设置marginBottom
         * @param viewId 某个控件ID
         * @param bottom marginBottom
         * @return
         */
        fun setMarginBottom(@IdRes viewId: Int, bottom: Int): ConstraintBegin {
            applyConstraintSet.setMargin(viewId, ConstraintSet.BOTTOM, bottom)
            return this
        }

        /**
         * 为某个控件设置关联关�?left_to_left_of
         * @param startId
         * @param endId
         * @return
         */
        fun leftToLeftOf(@IdRes startId: Int, @IdRes endId: Int): ConstraintBegin {
            applyConstraintSet.connect(startId, ConstraintSet.LEFT, endId, ConstraintSet.LEFT)
            return this
        }

        /**
         * 为某个控件设置关联关�?left_to_right_of
         * @param startId
         * @param endId
         * @return
         */
        fun leftToRightOf(@IdRes startId: Int, @IdRes endId: Int): ConstraintBegin {
            applyConstraintSet.connect(startId, ConstraintSet.LEFT, endId, ConstraintSet.RIGHT)
            return this
        }

        /**
         * 为某个控件设置关联关�?top_to_top_of
         * @param startId
         * @param endId
         * @return
         */
        fun topToTopOf(@IdRes startId: Int, @IdRes endId: Int): ConstraintBegin {
            applyConstraintSet.connect(startId, ConstraintSet.TOP, endId, ConstraintSet.TOP)
            return this
        }

        /**
         * 为某个控件设置关联关�?top_to_bottom_of
         * @param startId
         * @param endId
         * @return
         */
        fun topToBottomOf(@IdRes startId: Int, @IdRes endId: Int): ConstraintBegin {
            applyConstraintSet.connect(startId, ConstraintSet.TOP, endId, ConstraintSet.BOTTOM)
            return this
        }

        /**
         * 为某个控件设置关联关�?right_to_left_of
         * @param startId
         * @param endId
         * @return
         */
        fun rightToLeftOf(@IdRes startId: Int, @IdRes endId: Int): ConstraintBegin {
            applyConstraintSet.connect(startId, ConstraintSet.RIGHT, endId, ConstraintSet.LEFT)
            return this
        }

        /**
         * 为某个控件设置关联关�?right_to_right_of
         * @param startId
         * @param endId
         * @return
         */
        fun rightToRightOf(@IdRes startId: Int, @IdRes endId: Int): ConstraintBegin {
            applyConstraintSet.connect(startId, ConstraintSet.RIGHT, endId, ConstraintSet.RIGHT)
            return this
        }

        /**
         * 为某个控件设置关联关�?bottom_to_bottom_of
         * @param startId
         * @param endId
         * @return
         */
        fun bottomToBottomOf(@IdRes startId: Int, @IdRes endId: Int): ConstraintBegin {
            applyConstraintSet.connect(startId, ConstraintSet.BOTTOM, endId, ConstraintSet.BOTTOM)
            return this
        }

        /**
         * 为某个控件设置关联关�?bottom_to_top_of
         * @param startId
         * @param endId
         * @return
         */
        fun bottomToTopOf(@IdRes startId: Int, @IdRes endId: Int): ConstraintBegin {
            applyConstraintSet.connect(startId, ConstraintSet.BOTTOM, endId, ConstraintSet.TOP)
            return this
        }

        /**
         * 为某个控件设置宽�?         * @param viewId
         * @param width
         * @return
         */
        fun setWidth(@IdRes viewId: Int, width: Int): ConstraintBegin {
            applyConstraintSet.constrainWidth(viewId, width)
            return this
        }

        /**
         * 某个控件设置高度
         * @param viewId
         * @param height
         * @return
         */
        fun setHeight(@IdRes viewId: Int, height: Int): ConstraintBegin {
            applyConstraintSet.constrainHeight(viewId, height)
            return this
        }

        /**
         * 提交应用生效
         */
        fun commit() {
            constraintLayout.post {
                applyConstraintSet.applyTo(constraintLayout)
            }
        }
    }

    enum class Anchor {
        LEFT, RIGHT, TOP, BOTTOM, BASELINE, START, END, CIRCLE_REFERENCE;

        fun toInt(): Int {
            return when (this) {
                LEFT -> ConstraintSet.LEFT
                RIGHT -> ConstraintSet.RIGHT
                TOP -> ConstraintSet.TOP
                BOTTOM -> ConstraintSet.BOTTOM
                BASELINE -> ConstraintSet.BASELINE
                START -> ConstraintSet.START
                END -> ConstraintSet.END
                CIRCLE_REFERENCE -> ConstraintSet.CIRCLE_REFERENCE
            }
        }

    }

}
