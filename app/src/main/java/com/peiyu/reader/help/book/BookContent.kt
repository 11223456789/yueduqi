package com.peiyu.reader.help.book

import com.peiyu.reader.data.entities.ReplaceRule

data class BookContent(
    val sameTitleRemoved: Boolean,
    val textList: List<String>,
    //起效的替换规�?    val effectiveReplaceRules: List<ReplaceRule>?
) {

    override fun toString(): String {
        return textList.joinToString("\n")
    }

}
