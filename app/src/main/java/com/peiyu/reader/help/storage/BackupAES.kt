package com.peiyu.reader.help.storage

import cn.hutool.crypto.symmetric.AES
import com.peiyu.reader.help.config.LocalConfig
import com.peiyu.reader.utils.MD5Utils

class BackupAES : AES(
    MD5Utils.md5Encode(LocalConfig.password ?: "").encodeToByteArray(0, 16)
)
