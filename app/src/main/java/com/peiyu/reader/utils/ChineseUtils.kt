package com.peiyu.reader.utils

import com.github.liuyueyi.quick.transfer.ChineseUtils
import com.github.liuyueyi.quick.transfer.constants.TransType

object ChineseUtils {

    private var fixed = false

    fun s2t(content: String): String {
        return ChineseUtils.s2t(content)
    }

    fun t2s(content: String): String {
        if (!fixed) {
            fixT2sDict()
        }
        return ChineseUtils.t2s(content)
    }

    fun preLoad(async: Boolean, vararg transType: TransType) {
        ChineseUtils.preLoad(async, *transType)
    }

    fun unLoad(vararg transType: TransType) {
        ChineseUtils.unLoad(*transType)
    }

    fun fixT2sDict() {
        fixed = true
        val excludeList = listOf(
            "�?,
            "划槳", "列根", "雪梨", "雪糕", "多士", "起司", "芝士", "沙芬", "母音",
            "华乐", "民乐", "晶元", "晶片", "映像", "明覆", "明瞭", "新力", "新喻",
            "零錢", "零钱", "離線", "碟片", "模組", "桌球", "案頭", "機車", "電漿",
            "鳳梨", "魔戒", "載入", "菲林", "整合", "變數", "解碼", "散钱", "插水",
            "房屋", "房价", "快取", "德士", "建立", "常式", "席丹", "布殊", "布希",
            "巴哈", "巨集", "夜学", "向量", "半形", "加彭", "列印", "函式", "全形",
            "光碟", "介面", "乳酪", "沈船", "永珍", "演化", "牛油", "相容", "磁碟",
            "菲林", "規則", "酵素", "雷根", "饭盒",
            "路易�?, "非同�?, "出租�?, "周杰�?, "马铃�?, "馬鈴�?, "機械�?, "電單�?,
            "電扶�?, "音效�?, "飆車�?, "點陣�?, "個入�?, "顆進球", "沃尓�?, "晶片�?,
            "斯瓦�?, "斜角�?, "战列�?, "快速面", "希特�?, "太空�?, "吐瓦�?, "吉布�?,
            "吉布�?, "史太�?, "南冰�?, "区域�?, "波札�?, "解析�?, "酷洛�?, "金夏�?,
            "魔獸紀�?, "高空彈跳", "铁达尼号", "太空战士", "埃及妖后", "吉里巴斯", "附加元件",
            "魔鬼終結�?, "純文字檔�?, "奇幻魔法Melody", "列支敦斯�?
        )
        ChineseUtils.loadExcludeDict(TransType.TRADITIONAL_TO_SIMPLE, excludeList)
    }

}
