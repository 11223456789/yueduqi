package com.peiyu.reader.ui.main.explore

import androidx.recyclerview.widget.DiffUtil
import com.peiyu.reader.data.entities.BookSourcePart


class ExploreDiffItemCallBack : DiffUtil.ItemCallback<BookSourcePart>() {

    override fun areItemsTheSame(oldItem: BookSourcePart, newItem: BookSourcePart): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: BookSourcePart, newItem: BookSourcePart): Boolean {
        return oldItem.bookSourceName == newItem.bookSourceName
    }

}
