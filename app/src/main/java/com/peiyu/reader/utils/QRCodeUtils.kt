package com.peiyu.reader.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.WriterException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.king.zxing.DecodeFormatManager
import java.util.EnumMap
import kotlin.math.max


@Suppress("MemberVisibilityCanBePrivate", "unused")
object QRCodeUtils {

    const val DEFAULT_REQ_WIDTH = 480
    const val DEFAULT_REQ_HEIGHT = 640

    /**
     * 生成二维�?     * @param content 二维码的内容
     * @param heightPix 二维码的�?     * @param logo 二维码中间的logo
     * @param ratio  logo所占比�?因为二维码的最大容错率�?0%，所以建议ratio的范围小�?.3
     * @param errorCorrectionLevel
     */
    fun createQRCode(
        content: String,
        heightPix: Int = DEFAULT_REQ_HEIGHT,
        logo: Bitmap? = null,
        @FloatRange(from = 0.0, to = 1.0) ratio: Float = 0.2f,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.H
    ): Bitmap? {
        //配置参数
        val hints: MutableMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = "utf-8"
        //容错级别
        hints[EncodeHintType.ERROR_CORRECTION] = errorCorrectionLevel
        //设置空白边距的宽�?        hints[EncodeHintType.MARGIN] = 1 //default is 4
        return createQRCode(content, heightPix, logo, ratio, hints)
    }

    /**
     * 生成二维�?     * @param content 二维码的内容
     * @param heightPix 二维码的�?     * @param logo 二维码中间的logo
     * @param ratio  logo所占比�?因为二维码的最大容错率�?0%，所以建议ratio的范围小�?.3
     * @param hints
     * @param codeColor 二维码的颜色
     * @return
     */
    fun createQRCode(
        content: String?,
        heightPix: Int,
        logo: Bitmap?,
        @FloatRange(from = 0.0, to = 1.0) ratio: Float = 0.2f,
        hints: Map<EncodeHintType, *>,
        codeColor: Int = Color.BLACK
    ): Bitmap? {
        try {
            // 图像数据转换，使用了矩阵转换
            val bitMatrix =
                QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, heightPix, heightPix, hints)
            val pixels = IntArray(heightPix * heightPix)
            // 下面这里按照二维码的算法，逐个生成二维码的图片�?            // 两个for循环是图片横列扫描的结果
            for (y in 0 until heightPix) {
                for (x in 0 until heightPix) {
                    if (bitMatrix[x, y]) {
                        pixels[y * heightPix + x] = codeColor
                    } else {
                        pixels[y * heightPix + x] = Color.WHITE
                    }
                }
            }

            // 生成二维码图片的格式
            var bitmap: Bitmap? = Bitmap.createBitmap(heightPix, heightPix, Bitmap.Config.ARGB_8888)
            bitmap!!.setPixels(pixels, 0, heightPix, 0, 0, heightPix, heightPix)
            if (logo != null) {
                bitmap = addLogo(bitmap, logo, ratio)
            }
            return bitmap
        } catch (e: WriterException) {
            e.printOnDebug()
        }
        return null
    }

    /**
     * 在二维码中间添加Logo图案
     * @param src
     * @param logo
     * @param ratio  logo所占比�?因为二维码的最大容错率�?0%，所以建议ratio的范围小�?.3
     * @return
     */
    private fun addLogo(
        src: Bitmap?,
        logo: Bitmap?,
        @FloatRange(from = 0.0, to = 1.0) ratio: Float
    ): Bitmap? {
        if (src == null) {
            return null
        }
        if (logo == null) {
            return src
        }

        //获取图片的宽�?        val srcWidth = src.width
        val srcHeight = src.height
        val logoWidth = logo.width
        val logoHeight = logo.height
        if (srcWidth == 0 || srcHeight == 0) {
            return null
        }
        if (logoWidth == 0 || logoHeight == 0) {
            return src
        }

        //logo大小为二维码整体大小
        val scaleFactor = srcWidth * ratio / logoWidth
        var bitmap: Bitmap? = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap!!)
            canvas.drawBitmap(src, 0f, 0f, null)
            canvas.scale(
                scaleFactor,
                scaleFactor,
                (srcWidth / 2).toFloat(),
                (srcHeight / 2).toFloat()
            )
            canvas.drawBitmap(
                logo,
                ((srcWidth - logoWidth) / 2).toFloat(),
                ((srcHeight - logoHeight) / 2).toFloat(),
                null
            )
            canvas.save()
            canvas.restore()
        } catch (e: Exception) {
            bitmap = null
            e.printOnDebug()
        }
        return bitmap
    }

    /**
     * 解析一维码/二维码图�?     * @param bitmap 解析的图�?     * @param hints 解析编码类型
     * @return
     */
    fun parseCode(
        bitmap: Bitmap,
        reqWidth: Int = DEFAULT_REQ_WIDTH,
        reqHeight: Int = DEFAULT_REQ_HEIGHT,
        hints: Map<DecodeHintType?, Any?> = DecodeFormatManager.ALL_HINTS
    ): String? {
        val result = parseCodeResult(bitmap, reqWidth, reqHeight, hints)
        return result?.text
    }

    /**
     * 解析一维码/二维码图�?     * @param bitmap 解析的图�?     * @param hints 解析编码类型
     * @return
     */
    fun parseCodeResult(
        bitmap: Bitmap,
        reqWidth: Int = DEFAULT_REQ_WIDTH,
        reqHeight: Int = DEFAULT_REQ_HEIGHT,
        hints: Map<DecodeHintType?, Any?> = DecodeFormatManager.ALL_HINTS
    ): Result? {
        if (bitmap.width > reqWidth || bitmap.height > reqHeight) {
            val bm = bitmap.resizeAndRecycle(reqWidth, reqHeight)
            return parseCodeResult(getRGBLuminanceSource(bm), hints)
        }
        return parseCodeResult(getRGBLuminanceSource(bitmap), hints)
    }

    /**
     * 解析一维码/二维码图�?     * @param source
     * @param hints
     * @return
     */
    fun parseCodeResult(source: LuminanceSource?, hints: Map<DecodeHintType?, Any?>?): Result? {
        var result: Result? = null
        val reader = MultiFormatReader()
        try {
            reader.setHints(hints)
            if (source != null) {
                result = decodeInternal(reader, source)
                if (result == null) {
                    result = decodeInternal(reader, source.invert())
                }
                if (result == null && source.isRotateSupported) {
                    result = decodeInternal(reader, source.rotateCounterClockwise())
                }
            }
        } catch (e: java.lang.Exception) {
            e.printOnDebug()
        } finally {
            reader.reset()
        }
        return result
    }

    /**
     * 解析二维码图�?     * @param bitmapPath 需要解析的图片路径
     * @return
     */
    fun parseQRCode(bitmapPath: String?): String? {
        val result = parseQRCodeResult(bitmapPath)
        return result?.text
    }

    /**
     * 解析二维码图�?     * @param bitmapPath 需要解析的图片路径
     * @param reqWidth 请求目标宽度，如果实际图片宽度大于此值，会自动进行压缩处理，�?reqWidth �?reqHeight都小于或等于0时，则不进行压缩处理
     * @param reqHeight 请求目标高度，如果实际图片高度大于此值，会自动进行压缩处理，�?reqWidth �?reqHeight都小于或等于0时，则不进行压缩处理
     * @return
     */
    fun parseQRCodeResult(
        bitmapPath: String?,
        reqWidth: Int = DEFAULT_REQ_WIDTH,
        reqHeight: Int = DEFAULT_REQ_HEIGHT
    ): Result? {
        return parseCodeResult(bitmapPath, reqWidth, reqHeight, DecodeFormatManager.QR_CODE_HINTS)
    }

    /**
     * 解析一维码/二维码图�?     * @param bitmapPath 需要解析的图片路径
     * @return
     */
    fun parseCode(
        bitmapPath: String?,
        reqWidth: Int = DEFAULT_REQ_WIDTH,
        reqHeight: Int = DEFAULT_REQ_HEIGHT,
        hints: Map<DecodeHintType?, Any?> = DecodeFormatManager.ALL_HINTS
    ): String? {
        return parseCodeResult(bitmapPath, reqWidth, reqHeight, hints)?.text
    }

    /**
     * 解析一维码/二维码图�?     * @param bitmapPath 需要解析的图片路径
     * @param reqWidth 请求目标宽度，如果实际图片宽度大于此值，会自动进行压缩处理，�?reqWidth �?reqHeight都小于或等于0时，则不进行压缩处理
     * @param reqHeight 请求目标高度，如果实际图片高度大于此值，会自动进行压缩处理，�?reqWidth �?reqHeight都小于或等于0时，则不进行压缩处理
     * @param hints 解析编码类型
     * @return
     */
    fun parseCodeResult(
        bitmapPath: String?,
        reqWidth: Int = DEFAULT_REQ_WIDTH,
        reqHeight: Int = DEFAULT_REQ_HEIGHT,
        hints: Map<DecodeHintType?, Any?> = DecodeFormatManager.ALL_HINTS
    ): Result? {
        var result: Result? = null
        val reader = MultiFormatReader()
        try {
            reader.setHints(hints)
            val source = getRGBLuminanceSource(compressBitmap(bitmapPath, reqWidth, reqHeight))
            result = decodeInternal(reader, source)
            if (result == null) {
                result = decodeInternal(reader, source.invert())
            }
            if (result == null && source.isRotateSupported) {
                result = decodeInternal(reader, source.rotateCounterClockwise())
            }
        } catch (e: Exception) {
            e.printOnDebug()
        } finally {
            reader.reset()
        }
        return result
    }

    private fun decodeInternal(reader: MultiFormatReader, source: LuminanceSource): Result? {
        var result: Result? = null
        try {
            try {
                //采用HybridBinarizer解析
                result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (_: Exception) {
            }
            if (result == null) {
                //如果没有解析成功，再采用GlobalHistogramBinarizer解析一�?                result = reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source)))
            }
        } catch (_: Exception) {
        }
        return result
    }


    /**
     * 压缩图片
     * @param path
     * @return
     */
    private fun compressBitmap(path: String?, reqWidth: Int, reqHeight: Int): Bitmap {
        if (reqWidth > 0 && reqHeight > 0) { //都大于进行判断是否压�?            val newOpts = BitmapFactory.Options()
            // 开始读入图片，此时把options.inJustDecodeBounds 设回true�?            newOpts.inJustDecodeBounds = true //获取原始图片大小
            BitmapFactory.decodeFile(path, newOpts) // 此时返回bm为空
            val width = newOpts.outWidth.toFloat()
            val height = newOpts.outHeight.toFloat()
            // 缩放比，由于是固定比例缩放，只用高或者宽其中一个数据进行计算即�?            var wSize = 1 // wSize=1表示不缩�?            if (width > reqWidth) { // 如果宽度大的话根据宽度固定大小缩�?                wSize = (width / reqWidth).toInt()
            }
            var hSize = 1 // wSize=1表示不缩�?            if (height > reqHeight) { // 如果高度高的话根据宽度固定大小缩�?                hSize = (height / reqHeight).toInt()
            }
            var size = max(wSize, hSize)
            if (size <= 0) size = 1
            newOpts.inSampleSize = size // 设置缩放比例
            // 重新读入图片，注意此时已经把options.inJustDecodeBounds 设回false�?            newOpts.inJustDecodeBounds = false
            return BitmapFactory.decodeFile(path, newOpts)
        }
        return BitmapFactory.decodeFile(path)
    }


    /**
     * 获取RGBLuminanceSource
     * @param bitmap
     * @return
     */
    private fun getRGBLuminanceSource(bitmap: Bitmap): RGBLuminanceSource {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return RGBLuminanceSource(width, height, pixels)
    }

    /**
     * 生成条形�?     * @param content
     * @param format
     * @param desiredWidth
     * @param desiredHeight
     * @param hints
     * @param isShowText
     * @param textSize
     * @param codeColor
     * @return
     */
    fun createBarCode(
        content: String?,
        desiredWidth: Int,
        desiredHeight: Int,
        format: BarcodeFormat = BarcodeFormat.CODE_128,
        hints: Map<EncodeHintType?, *>? = null,
        isShowText: Boolean = true,
        textSize: Int = 40,
        @ColorInt codeColor: Int = Color.BLACK
    ): Bitmap? {
        if (TextUtils.isEmpty(content)) {
            return null
        }
        val writer = MultiFormatWriter()
        try {
            val result = writer.encode(
                content, format, desiredWidth,
                desiredHeight, hints
            )
            val width = result.width
            val height = result.height
            val pixels = IntArray(width * height)
            // All are 0, or black, by default
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (result[x, y]) codeColor else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(
                width, height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return if (isShowText) {
                addCode(bitmap, content, textSize, codeColor, textSize / 2)
            } else bitmap
        } catch (e: WriterException) {
            e.printOnDebug()
        }
        return null
    }

    /**
     * 条形码下面添加文本信�?     * @param src
     * @param code
     * @param textSize
     * @param textColor
     * @return
     */
    private fun addCode(
        src: Bitmap?,
        code: String?,
        textSize: Int,
        @ColorInt textColor: Int,
        offset: Int
    ): Bitmap? {
        if (src == null) {
            return null
        }
        if (TextUtils.isEmpty(code)) {
            return src
        }

        //获取图片的宽�?        val srcWidth = src.width
        val srcHeight = src.height
        if (srcWidth <= 0 || srcHeight <= 0) {
            return null
        }
        var bitmap: Bitmap? = Bitmap.createBitmap(
            srcWidth,
            srcHeight + textSize + offset * 2,
            Bitmap.Config.ARGB_8888
        )
        try {
            val canvas = Canvas(bitmap!!)
            canvas.drawBitmap(src, 0f, 0f, null)
            val paint = TextPaint()
            paint.textSize = textSize.toFloat()
            paint.color = textColor
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                code!!,
                (srcWidth / 2).toFloat(),
                (srcHeight + textSize / 2 + offset).toFloat(),
                paint
            )
            canvas.save()
            canvas.restore()
        } catch (e: Exception) {
            bitmap = null
            e.printOnDebug()
        }
        return bitmap
    }


}
