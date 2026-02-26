package com.peiyu.reader.ui.book.read.page.delegate

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.VelocityTracker
import com.peiyu.reader.data.entities.Book
import com.peiyu.reader.help.book.isImage
import com.peiyu.reader.model.ReadBook
import com.peiyu.reader.ui.book.read.page.ReadView
import com.peiyu.reader.ui.book.read.page.provider.ChapterProvider

class ScrollPageDelegate(readView: ReadView) : PageDelegate(readView) {

    // 滑动追踪的时�?    private val velocityDuration = 1000

    //速度追踪�?    private val mVelocity: VelocityTracker = VelocityTracker.obtain()
    private val slopSquare get() = readView.pageSlopSquare2

    var noAnim: Boolean = false

    override fun onAnimStart(animationSpeed: Int) {
        readView.onScrollAnimStart()
        //惯性滚�?        fling(
            0, touchY.toInt(), 0, mVelocity.yVelocity.toInt(),
            0, 0, -10 * viewHeight, 10 * viewHeight
        )
    }

    override fun onAnimStop() {
        readView.onScrollAnimStop()
    }

    override fun onTouch(event: MotionEvent) {
        //在多点触控时，事件不走ACTION_DOWN分支而产生的特殊事件处理
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            //当多个手指同时按下的情况，将最后一个按下的手指的坐标设置为起始坐标，所以只有最后一个手指的滑动事件被处�?            readView.setStartPoint(
                event.getX(event.pointerCount - 1),
                event.getY(event.pointerCount - 1),
                false
            )
        } else if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            //当多个手指同时按下的情况，当抬起一个手指时，起始坐标恢复为第一次按下的手指的坐�?            readView.setStartPoint(event.x, event.y, false)
            return
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
                mVelocity.clear()
            }

            MotionEvent.ACTION_MOVE -> {
                onScroll(event)
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                onAnimStart(readView.defaultAnimationSpeed)
            }
        }
    }

    override fun onScroll() {
        curPage.scroll((touchY - lastY).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        // nothing
    }

    private fun onScroll(event: MotionEvent) {
        mVelocity.addMovement(event)
        mVelocity.computeCurrentVelocity(velocityDuration)
        //取最后添�?即最新的)一个触摸点来计算滚动位�?        //多点触控时即最后按下的手指产生的事件点
        val pointX = event.getX(event.pointerCount - 1)
        val pointY = event.getY(event.pointerCount - 1)
        if (isMoved || readView.isLongScreenShot()) {
            readView.setTouchPoint(pointX, pointY, false)
        }
        if (!isMoved) {
            val deltaX = (pointX - startX).toInt()
            val deltaY = (pointY - startY).toInt()
            val distance = deltaX * deltaX + deltaY * deltaY
            isMoved = distance > slopSquare
            if (isMoved) {
                readView.setStartPoint(event.x, event.y, false)
            }
        }
        if (isMoved) {
            isRunning = true
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            readView.setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat(), false)
        } else if (isStarted) {
            onAnimStop()
            stopScroll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mVelocity.recycle()
    }

    override fun abortAnim() {
        readView.onScrollAnimStop()
        isStarted = false
        isMoved = false
        isRunning = false
        if (!scroller.isFinished) {
            readView.isAbortAnim = true
            scroller.abortAnimation()
        } else {
            readView.isAbortAnim = false
        }
    }

    override fun nextPageByAnim(animationSpeed: Int) {
        if (readView.isAbortAnim) {
            readView.isAbortAnim = false
            return
        }
        if (noAnim) {
            curPage.scroll(calcNextPageOffset())
            return
        }
        readView.setStartPoint(0f, 0f, false)
        startScroll(0, 0, 0, calcNextPageOffset(), animationSpeed)
    }

    override fun prevPageByAnim(animationSpeed: Int) {
        if (readView.isAbortAnim) {
            readView.isAbortAnim = false
            return
        }
        if (noAnim) {
            curPage.scroll(calcPrevPageOffset())
            return
        }
        readView.setStartPoint(0f, 0f, false)
        startScroll(0, 0, 0, calcPrevPageOffset(), animationSpeed)
    }

    /**
     * 计算点击翻页保留一行的滚动距离
     * 图片页使用可视高度作为滚动距�?     */
    private fun calcNextPageOffset(): Int {
        val visibleHeight = ChapterProvider.visibleHeight
        val book = ReadBook.book
        if (book == null || book.isImage) {
            return -visibleHeight
        }
        val visiblePage = readView.getCurVisiblePage()
        val isTextStyle = book.getImageStyle().equals(Book.imgStyleText, true)
        if (!isTextStyle && visiblePage.hasImageOrEmpty()) {
            return -visibleHeight
        }
        val lastLineTop = visiblePage.lines.last().lineTop.toInt()
        val offset = lastLineTop - ChapterProvider.paddingTop
        return -offset
    }

    private fun calcPrevPageOffset(): Int {
        val visibleHeight = ChapterProvider.visibleHeight
        val book = ReadBook.book
        if (book == null || book.isImage) {
            return visibleHeight
        }
        val visiblePage = readView.getCurVisiblePage()
        val isTextStyle = book.getImageStyle().equals(Book.imgStyleText, true)
        if (!isTextStyle && visiblePage.hasImageOrEmpty()) {
            return visibleHeight
        }
        val firstLineBottom = visiblePage.lines.first().lineBottom.toInt()
        val offset = visibleHeight - (firstLineBottom - ChapterProvider.paddingTop)
        return offset
    }
}
