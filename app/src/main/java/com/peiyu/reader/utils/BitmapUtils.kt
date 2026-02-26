@file:Suppress("unused")

package com.peiyu.reader.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.graphics.Color
import com.google.android.renderscript.Toolkit
import java.io.*
import kotlin.math.*


@Suppress("WeakerAccess", "MemberVisibilityCanBePrivate")
object BitmapUtils {

    /**
     * 从path中获取图片信�?在通过BitmapFactory.decodeFile(String path)方法将突破转成Bitmap时，
     * 遇到大一些的图片，我们经常会遇到OOM(Out Of Memory)的问题。所以用到了我们上面提到的BitmapFactory.Options这个类�?     *
     * @param path   文件路径
     * @param width  想要显示的图片的宽度
     * @param height 想要显示的图片的高度
     * @return
     */
    @Throws(IOException::class)
    fun decodeBitmap(path: String, width: Int, height: Int? = null): Bitmap? {
        val fis = FileInputStream(path)
        return fis.use {
            val op = BitmapFactory.Options()
            // inJustDecodeBounds如果设置为true,仅仅返回图片实际的宽和高,宽和高是赋值给opts.outWidth,opts.outHeight;
            op.inJustDecodeBounds = true
            BitmapFactory.decodeFileDescriptor(fis.fd, null, op)
            op.inSampleSize = calculateInSampleSize(op, width, height)
            op.inJustDecodeBounds = false
            BitmapFactory.decodeFileDescriptor(fis.fd, null, op)
        }
    }

    /**
     *计算 InSampleSize。缺省返�?
     * @param options BitmapFactory.Options,
     * @param width  想要显示的图片的宽度
     * @param height 想要显示的图片的高度
     * @return
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        width: Int? = null,
        height: Int? = null
    ): Int {
        //获取比例大小
        val wRatio = width?.let { options.outWidth / it } ?: -1
        val hRatio = height?.let { options.outHeight / it } ?: -1
        //如果超出指定大小，则缩小相应的比�?        return when {
            wRatio > 1 && hRatio > 1 -> max(wRatio, hRatio)
            wRatio > 1 -> wRatio
            hRatio > 1 -> hRatio
            else -> 1
        }
    }

    /** 从path中获取Bitmap图片
     * @param path 图片路径
     * @return
     */
    @Throws(IOException::class)
    fun decodeBitmap(path: String): Bitmap? {
        val fis = FileInputStream(path)
        return fis.use {
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true

            BitmapFactory.decodeFileDescriptor(fis.fd, null, opts)
            opts.inSampleSize = computeSampleSize(opts, -1, 128 * 128)
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeFileDescriptor(fis.fd, null, opts)
        }
    }

    /**
     * 以最省内存的方式读取本地资源的图�?     * @param context 设备上下�?     * @param resId 资源ID
     * @return
     */
    fun decodeBitmap(context: Context, resId: Int): Bitmap? {
        val opt = BitmapFactory.Options()
        opt.inPreferredConfig = Config.RGB_565
        return BitmapFactory.decodeResource(context.resources, resId, opt)
    }

    /**
     * @param context 设备上下�?     * @param resId 资源ID
     * @param width
     * @param height
     * @return
     */
    fun decodeBitmap(context: Context, resId: Int, width: Int, height: Int): Bitmap? {
        val op = BitmapFactory.Options()
        // inJustDecodeBounds如果设置为true,仅仅返回图片实际的宽和高,宽和高是赋值给opts.outWidth,opts.outHeight;
        op.inJustDecodeBounds = true
        BitmapFactory.decodeResource(context.resources, resId, op) //获取尺寸信息
        op.inSampleSize = calculateInSampleSize(op, width, height)
        op.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(context.resources, resId, op)
    }

    /**
     * @param context 设备上下�?     * @param fileNameInAssets Assets里面文件的名�?     * @param width 图片的宽�?     * @param height 图片的高�?     * @return Bitmap
     * @throws IOException
     */
    @Throws(IOException::class)
    fun decodeAssetsBitmap(
        context: Context,
        fileNameInAssets: String,
        width: Int,
        height: Int
    ): Bitmap? {
        var inputStream = context.assets.open(fileNameInAssets)
        return inputStream.use {
            val op = BitmapFactory.Options()
            // inJustDecodeBounds如果设置为true,仅仅返回图片实际的宽和高,宽和高是赋值给opts.outWidth,opts.outHeight;
            op.inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStream, null, op) //获取尺寸信息
            op.inSampleSize = calculateInSampleSize(op, width, height)
            inputStream = context.assets.open(fileNameInAssets)
            op.inJustDecodeBounds = false
            BitmapFactory.decodeStream(inputStream, null, op)
        }
    }

    /**
     * @param options
     * @param minSideLength
     * @param maxNumOfPixels
     * @return
     * 设置恰当的inSampleSize是解决该问题的关键之一。BitmapFactory.Options提供了另一个成员inJustDecodeBounds�?     * 设置inJustDecodeBounds为true后，decodeFile并不分配空间，但可计算出原始图片的长度和宽度，即opts.width和opts.height�?     * 有了这两个参数，再通过一定的算法，即可得到一个恰当的inSampleSize�?     * 查看Android源码，Android提供了下面这种动态计算的方法�?     */
    fun computeSampleSize(
        options: BitmapFactory.Options,
        minSideLength: Int,
        maxNumOfPixels: Int
    ): Int {
        val initialSize = computeInitialSampleSize(options, minSideLength, maxNumOfPixels)
        var roundedSize: Int
        if (initialSize <= 8) {
            roundedSize = 1
            while (roundedSize < initialSize) {
                roundedSize = roundedSize shl 1
            }
        } else {
            roundedSize = (initialSize + 7) / 8 * 8
        }
        return roundedSize
    }


    private fun computeInitialSampleSize(
        options: BitmapFactory.Options,
        minSideLength: Int,
        maxNumOfPixels: Int
    ): Int {

        val w = options.outWidth.toDouble()
        val h = options.outHeight.toDouble()

        val lowerBound = when (maxNumOfPixels) {
            -1 -> 1
            else -> ceil(sqrt(w * h / maxNumOfPixels)).toInt()
        }

        val upperBound = when (minSideLength) {
            -1 -> 128
            else -> min(
                floor(w / minSideLength),
                floor(h / minSideLength)
            ).toInt()
        }

        if (upperBound < lowerBound) {
            // return the larger one when there is no overlapping zone.
            return lowerBound
        }

        return when {
            maxNumOfPixels == -1 && minSideLength == -1 -> {
                1
            }
            minSideLength == -1 -> {
                lowerBound
            }
            else -> {
                upperBound
            }
        }
    }

    /**
     * 将Bitmap转换成InputStream
     *
     * @param bitmap
     * @return
     */
    fun toInputStream(bitmap: Bitmap): InputStream {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90 /*ignored for PNG*/, bos)
        return ByteArrayInputStream(bos.toByteArray()).also { bos.close() }
    }

}

/**
 * 获取指定宽高的图�? */
fun Bitmap.resizeAndRecycle(newWidth: Int, newHeight: Int): Bitmap {
    //获取新的bitmap
    val bitmap = Toolkit.resize(this, newWidth, newHeight)
    recycle()
    return bitmap
}

/**
 * 高斯模糊
 */
fun Bitmap.stackBlur(radius: Int = 8): Bitmap {
    return Toolkit.blur(this, radius)
}

/**
 * 取平均色
 */
fun Bitmap.getMeanColor(): Int {
    val width: Int = this.width
    val height: Int = this.height
    var pixel: Int
    var pixelSumRed = 0
    var pixelSumBlue = 0
    var pixelSumGreen = 0
    for (i in 0..99) {
        for (j in 70..99) {
            pixel = this.getPixel(
                (i * width / 100.toFloat()).roundToInt(),
                (j * height / 100.toFloat()).roundToInt()
            )
            pixelSumRed += Color.red(pixel)
            pixelSumGreen += Color.green(pixel)
            pixelSumBlue += Color.blue(pixel)
        }
    }
    val averagePixelRed = pixelSumRed / 3000
    val averagePixelBlue = pixelSumBlue / 3000
    val averagePixelGreen = pixelSumGreen / 3000
    return Color.rgb(
        averagePixelRed + 3,
        averagePixelGreen + 3,
        averagePixelBlue + 3
    )

}
