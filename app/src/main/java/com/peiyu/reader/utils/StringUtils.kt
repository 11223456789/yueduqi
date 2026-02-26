package com.peiyu.reader.utils

import android.annotation.SuppressLint
import android.text.TextUtils.isEmpty
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs


@Suppress("unused", "MemberVisibilityCanBePrivate")
object StringUtils {
    private const val HOUR_OF_DAY = 24
    private const val DAY_OF_YESTERDAY = 2
    private const val TIME_UNIT = 60
    private val ChnMap = chnMap
    private val wordCountFormatter by lazy {
        DecimalFormat("#.#")
    }

    private val chnMap: HashMap<Char, Int>
        get() {
            val map = HashMap<Char, Int>()
            var cnStr = "零一二三四五六七八九�?
            var c = cnStr.toCharArray()
            for (i in 0..10) {
                map[c[i]] = i
            }
            cnStr = "〇壹贰叁肆伍陆柒捌玖�?
            c = cnStr.toCharArray()
            for (i in 0..10) {
                map[c[i]] = i
            }
            map['�?] = 2
            map['�?] = 100
            map['�?] = 100
            map['�?] = 1000
            map['�?] = 1000
            map['�?] = 10000
            map['�?] = 100000000
            return map
        }

    /**
     * 将日期转换成昨天、今天、明�?     */
    fun dateConvert(source: String, pattern: String): String {
        val format = SimpleDateFormat(pattern, Locale.getDefault())
        val calendar = Calendar.getInstance()
        kotlin.runCatching {
            val date = format.parse(source) ?: return ""
            val curTime = calendar.timeInMillis
            calendar.time = date
            //将MISC 转换�?sec
            val difSec = abs((curTime - date.time) / 1000)
            val difMin = difSec / 60
            val difHour = difMin / 60
            val difDate = difHour / 60
            val oldHour = calendar.get(Calendar.HOUR)
            //如果没有时间
            if (oldHour == 0) {
                //比日�?昨天今天和明�?                return when {
                    difDate == 0L -> "今天"
                    difDate < DAY_OF_YESTERDAY -> "昨天"
                    else -> {
                        @SuppressLint("SimpleDateFormat")
                        val convertFormat = SimpleDateFormat("yyyy-MM-dd")
                        convertFormat.format(date)
                    }
                }
            }

            return when {
                difSec < TIME_UNIT -> difSec.toString() + "秒前"
                difMin < TIME_UNIT -> difMin.toString() + "分钟�?
                difHour < HOUR_OF_DAY -> difHour.toString() + "小时�?
                difDate < DAY_OF_YESTERDAY -> "昨天"
                else -> {
                    @SuppressLint("SimpleDateFormat")
                    val convertFormat = SimpleDateFormat("yyyy-MM-dd")
                    convertFormat.format(date)
                }
            }
        }.onFailure {
            it.printOnDebug()
        }
        return ""
    }

    /**
     * 首字母大�?     */
    @SuppressLint("DefaultLocale")
    fun toFirstCapital(str: String): String {
        return str.substring(0, 1).uppercase(Locale.getDefault()) + str.substring(1)
    }

    /**
     * 将文本中的半角字符，转换成全角字�?     */
    fun halfToFull(input: String): String {
        val c = input.toCharArray()
        for (i in c.indices) {
            if (c[i].code == 32)
            //半角空格
            {
                c[i] = 12288.toChar()
                continue
            }
            //根据实际情况，过滤不需要转换的符号
            //if (c[i] == 46) //半角点号，不转换
            // continue;

            if (c[i].code in 33..126)
            //其他符号都转换为全角
                c[i] = (c[i].code + 65248).toChar()
        }
        return String(c)
    }

    /**
     * 字符串全角转换为半角
     */
    fun fullToHalf(input: String): String {
        val c = input.toCharArray()
        for (i in c.indices) {
            if (c[i].code == 12288)
            //全角空格
            {
                c[i] = 32.toChar()
                continue
            }

            if (c[i].code in 65281..65374)
                c[i] = (c[i].code - 65248).toChar()
        }
        return String(c)
    }

    /**
     * 中文大写数字转数�?     */
    fun chineseNumToInt(chNum: String): Int {
        var result = 0
        var tmp = 0
        var billion = 0
        val cn = chNum.toCharArray()

        // "一零二�? 形式
        if (cn.size > 1 && chNum.matches("^[〇零一二三四五六七八九壹贰叁肆伍陆柒捌玖]$".toRegex())) {
            for (i in cn.indices) {
                cn[i] = (48 + ChnMap[cn[i]]!!).toChar()
            }
            return Integer.parseInt(String(cn))
        }

        // "一千零二十�?, "一千二" 形式
        return kotlin.runCatching {
            for (i in cn.indices) {
                val tmpNum = ChnMap[cn[i]]!!
                when {
                    tmpNum == 100000000 -> {
                        result += tmp
                        result *= tmpNum
                        billion = billion * 100000000 + result
                        result = 0
                        tmp = 0
                    }

                    tmpNum == 10000 -> {
                        result += tmp
                        result *= tmpNum
                        tmp = 0
                    }

                    tmpNum >= 10 -> {
                        if (tmp == 0)
                            tmp = 1
                        result += tmpNum * tmp
                        tmp = 0
                    }

                    else -> {
                        tmp = if (i >= 2 && i == cn.size - 1 && ChnMap[cn[i - 1]]!! > 10)
                            tmpNum * ChnMap[cn[i - 1]]!! / 10
                        else
                            tmp * 10 + tmpNum
                    }
                }
            }
            result += tmp + billion
            result
        }.getOrDefault(-1)
    }

    /**
     * 字符串转数字
     */
    fun stringToInt(str: String?): Int {
        if (str != null) {
            val num = fullToHalf(str).replace("\\s+".toRegex(), "")
            return kotlin.runCatching {
                Integer.parseInt(num)
            }.getOrElse {
                chineseNumToInt(num)
            }
        }
        return -1
    }

    /**
     * 是否包含数字
     */
    fun isContainNumber(company: String): Boolean {
        val p = Pattern.compile("[0-9]+")
        val m = p.matcher(company)
        return m.find()
    }

    /**
     * 是否数字
     */
    fun isNumeric(str: String): Boolean {
        val pattern = Pattern.compile("-?[0-9]+")
        val isNum = pattern.matcher(str)
        return isNum.matches()
    }

    fun wordCountFormat(words: Int): String {
        var wordsS = ""
        if (words > 0) {
            if (words > 10000) {
                val df = wordCountFormatter
                wordsS = df.format(words * 1.0f / 10000f.toDouble()) + "万字"
            } else {
                wordsS = words.toString() + "�?
            }
        }
        return wordsS
    }

    fun wordCountFormat(wc: String?): String {
        if (wc == null) return ""
        var wordsS = ""
        if (isNumeric(wc)) {
            val words: Int = wc.toInt()
            if (words > 0) {
                if (words > 10000) {
                    val df = wordCountFormatter
                    wordsS = df.format(words * 1.0f / 10000f.toDouble()) + "万字"
                } else {
                    wordsS = words.toString() + "�?
                }
            }
        } else {
            wordsS = wc
        }
        return wordsS
    }

    /**
     * 移除字符串首尾空字符的高效方�?利用ASCII值判�?包括全角空格)
     */
    fun trim(s: String): String {
        if (isEmpty(s)) return ""
        var start = 0
        val len = s.length
        var end = len - 1
        while (start < end && (s[start].code <= 0x20 || s[start] == '　')) {
            ++start
        }
        while (start < end && (s[end].code <= 0x20 || s[end] == '　')) {
            --end
        }
        ++end
        return if (start > 0 || end < len) s.substring(start, end) else s
    }

    /**
     * 重复字符�?     */
    fun repeat(str: String, n: Int): String {
        val stringBuilder = StringBuilder()
        for (i in 0 until n) {
            stringBuilder.append(str)
        }
        return stringBuilder.toString()
    }

    /**
     * 移除UTF�?     */
    fun removeUTFCharacters(data: String?): String? {
        if (data == null) return null
        val p = Pattern.compile("\\\\u(\\p{XDigit}{4})")
        val m = p.matcher(data)
        val buf = StringBuffer(data.length)
        while (m.find()) {
            val ch = Integer.parseInt(m.group(1)!!, 16).toChar().toString()
            m.appendReplacement(buf, Matcher.quoteReplacement(ch))
        }
        m.appendTail(buf)
        return buf.toString()
    }

    /**
     * 压缩字符�?     */
    fun compress(str: String): Result<String> {
        return kotlin.runCatching {
            if (str.isEmpty()) {
                return@runCatching str
            }
            val out = ByteArrayOutputStream()
            var gzip: GZIPOutputStream? = null
            return@runCatching try {
                gzip = GZIPOutputStream(out)
                gzip.write(str.toByteArray())
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } finally {
                gzip?.runCatching {
                    close()
                }
                out.runCatching {
                    close()
                }
            }
        }
    }

    /**
     * 解压字符�?     */
    @Throws(IOException::class)
    fun unCompress(str: String): Result<String> {
        return kotlin.runCatching {
            val outputStream = ByteArrayOutputStream()
            var inputStream: ByteArrayInputStream? = null
            var ginZip: GZIPInputStream? = null
            return@runCatching try {
                val compressed = Base64.decode(str, Base64.NO_WRAP)
                inputStream = ByteArrayInputStream(compressed)
                ginZip = GZIPInputStream(inputStream)
                ginZip.copyTo(outputStream)
                outputStream.toString()
            } finally {
                ginZip?.runCatching {
                    close()
                }
                inputStream?.runCatching {
                    close()
                }
                outputStream.runCatching {
                    close()
                }
            }
        }
    }

}
