package com.peiyu.reader.constant

import androidx.annotation.IntDef

/**
 * 以二进制位来区分,可能一本书籍包含多个类�?每一位代表一个类�?数值为2的n次方
 * 以二进制位来区分,数据库查询更高效, 数�?=8和老版本类型区分开
 */
@Suppress("ConstPropertyName")
object BookType {
    /**
     * 8 文本
     */
    const val text = 0b1000

    /**
     * 16 更新失败
     */
    const val updateError = 0b10000

    /**
     * 32 音频
     */
    const val audio = 0b100000

    /**
     * 64 图片
     */
    const val image = 0b1000000

    /**
     * 128 只提供下载服务的网站
     */
    const val webFile = 0b10000000

    /**
     * 256 本地
     */
    const val local = 0b100000000

    /**
     * 512 压缩�?表明书籍文件是从压缩包内解压来的
     */
    const val archive = 0b1000000000

    /**
     * 1024 未正式加入到书架的临时阅读书�?     */
    const val notShelf = 0b100_0000_0000

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(text, updateError, audio, image, webFile, local, archive, notShelf)
    annotation class Type

    /**
     * 所有可以从书源转换的书籍类�?     */
    const val allBookType = text or image or audio or webFile

    const val allBookTypeLocal = text or image or audio or webFile or local

    /**
     * 本地书籍书源标志
     */
    const val localTag = "loc_book"

    /**
     * 书源已webDav::开头的书籍,可以从webDav更新或重新下�?     */
    const val webDavTag = "webDav::"

}
