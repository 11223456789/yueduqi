package com.peiyu.reader.constant

import java.util.regex.Pattern

@Suppress("RegExpRedundantEscape", "unused")
object AppPattern {
    val JS_PATTERN: Pattern =
        Pattern.compile("<js>([\\w\\W]*?)</js>|@js:([\\w\\W]*)", Pattern.CASE_INSENSITIVE)
    val EXP_PATTERN: Pattern = Pattern.compile("\\{\\{([\\w\\W]*?)\\}\\}")

    //匹配格式化后的图片格�?    val imgPattern: Pattern = Pattern.compile("<img[^>]*src=['\"]([^'\"]*(?:['\"][^>]+\\})?)['\"][^>]*>")

    //dataURL图片类型
    val dataUriRegex = Regex("^data:.*?;base64,(.*)")

    val nameRegex = Regex("\\s+作\\s*�?*|\\s+\\S+\\s+�?)
    val authorRegex = Regex("^\\s*作\\s*者[:：\\s]+|\\s+�?)
    val fileNameRegex = Regex("[\\\\/:*?\"<>|.]")
    val fileNameRegex2 = Regex("[\\\\/:*?\"<>|]")
    val splitGroupRegex = Regex("[,;，；]")
    val titleNumPattern: Pattern = Pattern.compile("(�?(.+?)(�?")

    //书源调试信息中的各种符号
    val debugMessageSymbolRegex = Regex("[⇒◇┌└≡]")

    //本地书籍支持类型
    val bookFileRegex = Regex(".*\\.(txt|epub|umd|pdf|mobi|azw3|azw)", RegexOption.IGNORE_CASE)
    //压缩文件支持类型
    val archiveFileRegex = Regex(".*\\.(zip|rar|7z)$", RegexOption.IGNORE_CASE)

    /**
     * 所有标�?     */
    val bdRegex = Regex("(\\p{P})+")

    /**
     * 换行
     */
    val rnRegex = Regex("[\\r\\n]")

    /**
     * 不发音段落判�?     */
    val notReadAloudRegex = Regex("^(\\s|\\p{C}|\\p{P}|\\p{Z}|\\p{S})+$")

    val xmlContentTypeRegex = "(application|text)/\\w*\\+?xml.*".toRegex()

    val semicolonRegex = ";".toRegex()

    val equalsRegex = "=".toRegex()

    val spaceRegex = "\\s+".toRegex()

    val regexCharRegex = "[{}()\\[\\].+*?^$\\\\|]".toRegex()

    val LFRegex = "\n".toRegex()
}
