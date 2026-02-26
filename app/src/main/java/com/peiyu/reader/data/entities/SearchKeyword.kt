package com.peiyu.reader.data.entities

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize


@Parcelize
@Entity(tableName = "search_keywords", indices = [(Index(value = ["word"], unique = true))])
data class SearchKeyword(
    /** 搜索关键�?*/
    @PrimaryKey
    var word: String = "",
    /** 使用次数 */
    var usage: Int = 1,
    /** 最后一次使用时�?*/
    var lastUseTime: Long = System.currentTimeMillis()
) : Parcelable
