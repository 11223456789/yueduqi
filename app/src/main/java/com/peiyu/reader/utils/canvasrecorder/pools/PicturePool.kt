package com.peiyu.reader.utils.canvasrecorder.pools

import android.graphics.Picture
import com.peiyu.reader.utils.objectpool.BaseObjectPool

class PicturePool : BaseObjectPool<Picture>(64) {

    override fun create(): Picture = Picture()

}
