package com.peiyu.reader.utils

import com.peiyu.reader.help.config.AppConfig

// 匹配待“输入的章节”字符串
private val regexEpisode = Regex("\\d+(-\\d+)?(,\\d+(-\\d+)?)*")

/**
 * 是否启用自定义导�? *
 * @author Discut
 */
fun enableCustomExport(): Boolean {
    return AppConfig.enableCustomExport && AppConfig.exportType == 1
}

/**
 * 验证 输入的范�?是否正确
 *
 * @since 1.0.0
 * @author Discut
 * @param text 输入的范�?字符�? * @return 是否正确
 */
fun verificationField(text: String): Boolean {
    return text.matches(regexEpisode)
}
