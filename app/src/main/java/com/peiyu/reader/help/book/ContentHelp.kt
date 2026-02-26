package com.peiyu.reader.help.book

import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

@Suppress("SameParameterValue", "RegExpRedundantEscape")
object ContentHelp {

    /**
     * 段落重排算法入口。把整篇内容输入，连接错误的分段，再把每个段落调用其他方法重新切�?     *
     * @param content     正文
     * @param chapterName 标题
     * @return
     */
    fun reSegment(content: String, chapterName: String): String {
        var content1 = content
        val dict = makeDict(content1)
        var p = content1
            .replace("&quot;".toRegex(), "�?)
            .replace("[:：]['\"‘”“]+".toRegex(), "：�?)
            .replace("[\"”“]+\\s*[\"”“][\\s\"”“]*".toRegex(), "”\n�?)
            .split("\n(\\s*)".toRegex()).toTypedArray()

        //初始化StringBuilder的长�?在原content的长度基础上做冗余
        var buffer = StringBuilder((content1.length * 1.15).toInt())
        //          章节的文本格式为章节标题-空行-首段，所以处理段落时需要略过第一行文本�?        buffer.append("  ")
        if (chapterName.trim { it <= ' ' } != p[0].trim { it <= ' ' }) {
            // 去除段落内空格。unicode 3000 象形字间隔（中日韩符号和标点），不包含在\s�?            buffer.append(p[0].replace("[\u3000\\s]+".toRegex(), ""))
        }

        //如果原文存在分段错误，需要把段落重新黏合
        for (i in 1 until p.size) {
            if (match(MARK_SENTENCES_END, buffer.last())
                || (match(MARK_QUOTATION_RIGHT, buffer.last())
                        && match(MARK_SENTENCES_END, buffer[buffer.lastIndex - 1]))
            ) {
                buffer.append("\n")
            }
            // 段落开头以外的地方不应该有空格
            // 去除段落内空格。unicode 3000 象形字间隔（中日韩符号和标点），不包含在\s�?            buffer.append(p[i].replace("[\u3000\\s]".toRegex(), ""))
        }
        //     预分段预处理
        //         ”“处理为”\n“�?        //         ”。“处理为”。\n“。不考虑“？�? “！”的情况�?        // ”。xxx处理�?”。\n xxx
        p = buffer.toString()
            .replace("[\"”“]+\\s*[\"”“]+".toRegex(), "”\n�?)
            .replace("[\"”“]+(？。！?!~)[\"”“]+".toRegex(), "�?1\n�?)
            .replace("[\"”“]+(？。！?!~)([^\"”“])".toRegex(), "�?1\n$2")
            .replace(
                "([问说喊唱叫骂道着答])[\\.。]".toRegex(),
                "$1。\n"
            )
            .split("\n".toRegex()).toTypedArray()
        buffer = StringBuilder((content1.length * 1.15).toInt())
        for (s in p) {
            buffer.append("\n")
            buffer.append(findNewLines(s, dict))
        }
        buffer = reduceLength(buffer)
        content1 = (buffer.toString() //         处理章节头部空格和换�?            .replaceFirst("^\\s+".toRegex(), "")
            .replace("\\s*[\"”“]+\\s*[\"”“][\\s\"”“]*".toRegex(), "”\n�?)
            .replace("[:：][”“\"\\s]+".toRegex(), "：�?)
            .replace("\n[\"“”]([^\n\"“”]+)([,:，：][\"”“])([^\n\"“”]+)".toRegex(), "\n$1：�?3")
            .replace("\n(\\s*)".toRegex(), "\n"))
        return content1
    }

    /**
     * 强制切分，减少段落内的句�?     * 如果连续2对引号的段落没有提示语，进入对话模式。最后一对引号后强制切分段落
     * 如果引号内的内容长于5句，可能引号状态有误，随机分段
     * 如果引号外的内容长于3句，随机分段
     *
     * @param str
     * @return
     */
    private fun reduceLength(str: StringBuilder): StringBuilder {
        val p = str.toString().split("\n".toRegex()).toTypedArray()
        val l = p.size
        val b = BooleanArray(l)
        for (i in 0 until l) {
            b[i] = p[i].matches(PARAGRAPH_DIAGLOG)
        }
        var dialogue = 0
        for (i in 0 until l) {
            if (b[i]) {
                if (dialogue < 0) dialogue = 1 else if (dialogue < 2) dialogue++
            } else {
                if (dialogue > 1) {
                    p[i] = splitQuote(p[i])
                    dialogue--
                } else if (dialogue > 0 && i < l - 2) {
                    if (b[i + 1]) p[i] = splitQuote(p[i])
                }
            }
        }
        val string = StringBuilder()
        for (i in 0 until l) {
            string.append('\n')
            string.append(p[i])
            //System.out.print(" "+b[i]);
        }
        //System.out.println(" " + str);
        return string
    }

    // 强制切分进入对话模式后，未构�?“xxx�?形式的段�?    private fun splitQuote(str: String): String {
        val length = str.length
        if (length < 3) return str
        if (match(MARK_QUOTATION, str[0])) {
            val i = seekIndex(str, MARK_QUOTATION, 1, length - 2, true) + 1
            if (i > 1) if (!match(MARK_QUOTATION_BEFORE, str[i - 1])) {
                return "${str.take(i)}\n${str.substring(i)}"
            }
        } else if (match(MARK_QUOTATION, str[length - 1])) {
            val i = length - 1 - seekIndex(str, MARK_QUOTATION, 1, length - 2, false)
            if (i > 1) {
                if (!match(MARK_QUOTATION_BEFORE, str[i - 1])) {
                    return "${str.take(i)}\n${str.substring(i)}"
                }
            }
        }
        return str
    }

    /**
     * 计算随机插入换行符的位置�?     * @param str 字符�?     * @param offset 传回的结果需要叠加的偏移�?     * @param min 最低几个句子，随机插入换行
     * @param gain 倍率。每个句子插入换行的数学期望 = 1 / gain , gain越大越不容易插入换行
     * @return
     */
    private fun forceSplit(
        str: String,
        offset: Int,
        min: Int,
        gain: Int,
        tigger: Int
    ): ArrayList<Int> {
        val result = ArrayList<Int>()
        val arrayEnd = seekIndexes(str, MARK_SENTENCES_END_P, 0, str.length - 2, true)
        val arrayMid = seekIndexes(str, MARK_SENTENCES_MID, 0, str.length - 2, true)
        if (arrayEnd.size < tigger && arrayMid.size < tigger * 3) return result
        var j = 0
        var i = min
        while (i < arrayEnd.size) {
            var k = 0
            while (j < arrayMid.size) {
                if (arrayMid[j] < arrayEnd[i]) k++
                j++
            }
            if (Math.random() * gain < 0.8 + k / 2.5) {
                result.add(arrayEnd[i] + offset)
                i = max(i + min, i)
            }
            i++
        }
        return result
    }

    // 对内容重新划分段�?输入参数str已经使用换行符预分割
    private fun findNewLines(str: String, dict: List<String>): String {
        val string = StringBuilder(str)
        // 标记string中每个引号的位置.特别的，用引号进行列举时视为只有一对引号�?如：“锅”、“碗”视为“锅、碗”，从而避免误断句�?        val arrayQuote: MutableList<Int> = ArrayList()
        //  标记插入换行符的位置，int为插入位置（str的char下标�?        var insN = ArrayList<Int>()

        //mod[i]标记str的每一段处于引号内还是引号外。范围： str.substring( array_quote.get(i), array_quote.get(i+1) )的状态�?        //长度：array_quote.size(),但是初始化时未预估占用的长度，用空间换时�?        //0未知，正数引号内，负数引号外�?        //如果相邻的两个标记都�?1，那么需要增�?个引号�?        //引号内不进行断句
        val mod = IntArray(str.length)
        var waitClose = false
        for (i in str.indices) {
            val c = str[i]
            if (match(MARK_QUOTATION, c)) {
                val size = arrayQuote.size

                //        把“xxx”、“yy”合并为“xxx_yy”进行处�?                if (size > 0) {
                    val quotePre = arrayQuote[size - 1]
                    if (i - quotePre == 2) {
                        var remove = false
                        if (waitClose) {
                            if (match(",，�?", str[i - 1])) {
                                // 考虑出现“和”这种特殊情�?                                remove = true
                            }
                        } else if (match(",，�?和与�?, str[i - 1])) {
                            remove = true
                        }
                        if (remove) {
                            string.setCharAt(i, '�?)
                            string.setCharAt(i - 2, '�?)
                            arrayQuote.removeAt(size - 1)
                            mod[size - 1] = 1
                            mod[size] = -1
                            continue
                        }
                    }
                }
                arrayQuote.add(i)

                //  为xxx：“xxx”做标记
                if (i > 1) {
                    // 当前发言的正引号的前一个字�?                    val charB1 = str[i - 1]
                    // 上次发言的正引号的前一个字�?                    var charB2 = 0.toChar()
                    if (match(MARK_QUOTATION_BEFORE, charB1)) {
                        // 如果不是第一处引号，寻找上一处断句，进行分段
                        if (arrayQuote.size > 1) {
                            val lastQuote = arrayQuote[arrayQuote.size - 2]
                            var p = 0
                            if (charB1 == ',' || charB1 == '�?) {
                                if (arrayQuote.size > 2) {
                                    p = arrayQuote[arrayQuote.size - 3]
                                    if (p > 0) {
                                        charB2 = str[p - 1]
                                    }
                                }
                            }
                            //if(char_b2=='.' || char_b2=='�?)
                            if (match(MARK_SENTENCES_END_P, charB2)) {
                                insN.add(p - 1)
                            } else if (!match("�?, charB2)) {
                                val lastEnd = seekLast(str, MARK_SENTENCES_END, i, lastQuote)
                                if (lastEnd > 0) insN.add(lastEnd) else insN.add(lastQuote)
                            }
                        }
                        waitClose = true
                        mod[size] = 1
                        if (size > 0) {
                            mod[size - 1] = -1
                            if (size > 1) {
                                mod[size - 2] = 1
                            }
                        }
                    } else if (waitClose) {
                        run {
                            waitClose = false
                            insN.add(i)
                        }
                    }
                }
            }
        }
        val size = arrayQuote.size


        //标记循环状态，此位置前的引号是否已经配�?        var opend = false
        if (size > 0) {
            //�?次遍历array_quote，令其元素的值不�?
            for (i in 0 until size) {
                if (mod[i] > 0) {
                    opend = true
                } else if (mod[i] < 0) {
                    //连续2个反引号表明存在冲突，强制把前一个设为正引号
                    if (!opend) {
                        if (i > 0) mod[i] = 3
                    }
                    opend = false
                } else {
                    opend = !opend
                    if (opend) mod[i] = 2 else mod[i] = -2
                }
            }
            //        修正，断尾必须封闭引�?            if (opend) {
                if (arrayQuote[size - 1] - string.length > -3) {
                    //if((match(MARK_QUOTATION,string.charAt(string.length()-1)) || match(MARK_QUOTATION,string.charAt(string.length()-2)))){
                    if (size > 1) mod[size - 2] = 4
                    // 0<=i<size,故无需判断size>=1
                    mod[size - 1] = -4
                } else if (!match(MARK_SENTENCES_SAY, string[string.length - 2])) string.append(
                    "�?
                )
            }


            //�?次循环，mod[i]由负变正时，�?字符如果是句末，需要插入换�?            var loop2Mod1 = -1 //上一个引号跟随内容的状�?            var loop2Mod2: Int //当前引号跟随内容的状�?            var i = 0
            var j = arrayQuote[0] - 1 //当前引号前一字符的序�?            if (j < 0) {
                i = 1
                loop2Mod1 = 0
            }
            while (i < size) {
                j = arrayQuote[i] - 1
                loop2Mod2 = mod[i]
                if (loop2Mod1 < 0 && loop2Mod2 > 0) {
                    if (match(MARK_SENTENCES_END, string[j])) insN.add(j)
                }
                loop2Mod1 = loop2Mod2
                i++
            }
        }

        //�?次循环，匹配并插入换行�?        //"xxxx" xxxx。\n xxx“xxxx�?        //未实�?
        // 使用字典验证ins_n , 避免插入不必要的换行�?        // 由于目前没有插入、的列表，无法解�?“xx”、“xx”“xx�?被插入换行的问题
        val insN1 = ArrayList<Int>()
        for (i in insN) {
            if (match("\"'”�?, string[i])) {
                val start: Int = seekLast(
                    str,
                    "\"'”�?,
                    i - 1,
                    i - WORD_MAX_LENGTH
                )
                if (start > 0) {
                    val word = str.substring(start + 1, i)
                    if (dict.contains(word)) {
                        //System.out.println("使用字典验证 跳过\tins_n=" + i + "  word=" + word);
                        //引号内如果是字典词条，后方不插入换行符（前方不需要优化）
                        continue
                    } else {
                        //System.out.println("使用字典验证 插入\tins_n=" + i + "  word=" + word);
                        if (match("的地�?, str[start])) {
                            //xx的“xx”，后方不插入换行符（前方不需要优化）
                            continue
                        }
                    }
                }
            }
            insN1.add(i)
        }
        insN = insN1

//        随机在句末插入换行符
        insN = ArrayList(HashSet(insN))
        insN.sort()
        run {
            var subs: String
            var j = 0
            var progress = 0
            var nextLine = -1
            if (insN.isNotEmpty()) nextLine = insN[j]
            var gain = 3
            var min = 0
            var trigger = 2
            for (i in arrayQuote.indices) {
                val qutoe = arrayQuote[i]
                if (qutoe > 0) {
                    gain = 4
                    min = 2
                    trigger = 4
                } else {
                    gain = 3
                    min = 0
                    trigger = 2
                }

//            把引号前的换行符与内容相间插�?                while (j < insN.size) {

//                如果下一个换行符在当前引号前，那么需要此次处�?如果紧挨当前引号，需要考虑插入引号的情�?                    if (nextLine >= qutoe) break
                    nextLine = insN[j]
                    if (progress < nextLine) {
                        subs = string.substring(progress, nextLine)
                        insN.addAll(forceSplit(subs, progress, min, gain, trigger))
                        progress = nextLine + 1
                    }
                    j++
                }
                if (progress < qutoe) {
                    subs = string.substring(progress, qutoe + 1)
                    insN.addAll(forceSplit(subs, progress, min, gain, trigger))
                    progress = qutoe + 1
                }
            }
            while (j < insN.size) {
                nextLine = insN[j]
                if (progress < nextLine) {
                    subs = string.substring(progress, nextLine)
                    insN.addAll(forceSplit(subs, progress, min, gain, trigger))
                    progress = nextLine + 1
                }
                j++
            }
            if (progress < string.length) {
                subs = string.substring(progress, string.length)
                insN.addAll(forceSplit(subs, progress, min, gain, trigger))
            }
        }

//     根据段落状态修正引号方向、计算需要插入引号的位置
//     ins_quote跟随array_quote   ins_quote[i]!=0,则array_quote.get(i)的引号前需要前插入'�?
        val insQuote = BooleanArray(size)
        opend = false
        for (i in 0 until size) {
            val p = arrayQuote[i]
            if (mod[i] > 0) {
                string.setCharAt(p, '�?)
                if (opend) insQuote[i] = true
                opend = true
            } else if (mod[i] < 0) {
                string.setCharAt(p, '�?)
                opend = false
            } else {
                opend = !opend
                if (opend) string.setCharAt(p, '�?) else string.setCharAt(p, '�?)
            }
        }
        insN = ArrayList(HashSet(insN))
        insN.sort()

//     完成字符串拼接（从string复制、插入引号和换行
//     ins_quote 在引号前插入一个引号�?  ins_quote[i]!=0,则array_quote.get(i)的引号前需要前插入'�?
//     ins_n 插入换行。数组的值表示插入换行符的位�?        val buffer = StringBuilder((str.length * 1.15).toInt())
        var j = 0
        var progress = 0
        var nextLine = -1
        if (insN.isNotEmpty()) nextLine = insN[j]
        for (i in arrayQuote.indices) {
            val quote = arrayQuote[i]

//            把引号前的换行符与内容相间插�?            while (j < insN.size) {

//                如果下一个换行符在当前引号前，那么需要此次处�?如果紧挨当前引号，需要考虑插入引号的情�?                if (nextLine >= quote) break
                nextLine = insN[j]
                buffer.append(string, progress, nextLine + 1)
                buffer.append('\n')
                progress = nextLine + 1
                j++
            }
            if (progress < quote) {
                buffer.append(string, progress, quote + 1)
                progress = quote + 1
            }
            if (insQuote[i] && buffer.length > 2) {
                if (buffer[buffer.length - 1] == '\n') buffer.append('�?) else buffer.insert(
                    buffer.length - 1,
                    "”\n"
                )
            }
        }
        while (j < insN.size) {
            nextLine = insN[j]
            if (progress <= nextLine) {
                buffer.append(string, progress, nextLine + 1)
                buffer.append('\n')
                progress = nextLine + 1
            }
            j++
        }
        if (progress < string.length) {
            buffer.append(string, progress, string.length)
        }
        return buffer.toString()
    }

    /**
     * 从字符串提取引号包围,且不止出现一次的内容为字�?     *
     * @param str
     * @return 词条列表
     */
    private fun makeDict(str: String): List<String> {

        // 引号中间不包含任何标�?        val patten = Pattern.compile(
            """
          (?<=["'”“])([^
          \p{P}]{1,$WORD_MAX_LENGTH})(?=["'”“])
          """.trimIndent()
        )
        //Pattern patten = Pattern.compile("(?<=[\"'”“])([^\n\"'”“]{1,16})(?=[\"'”“])");
        val matcher = patten.matcher(str)
        val cache: MutableList<String> = ArrayList()
        val dict: MutableList<String> = ArrayList()
        while (matcher.find()) {
            val word = matcher.group()
            if (cache.contains(word)) {
                if (!dict.contains(word)) dict.add(word)
            } else cache.add(word)
        }
        return dict
    }

    /**
     * 计算匹配到字典的每个字符的位�?     *
     * @param str     待匹配的字符�?     * @param key     字典
     * @param from    从字符串的第几个字符开始匹�?     * @param to      匹配到第几个字符结束
     * @param inOrder 是否按照从前向后的顺序匹�?     * @return 返回距离构成的ArrayList<Int>
     */
    private fun seekIndexes(
        str: String,
        key: String,
        from: Int,
        to: Int,
        inOrder: Boolean
    ): ArrayList<Int> {
        val list = ArrayList<Int>()
        if (str.length - from < 1) return list
        var i = 0
        if (from > 0) i = from
        var t = str.length
        if (to > 0) t = min(t, to)
        var c: Char
        while (i < t) {
            c = if (inOrder) str[i] else str[str.length - i - 1]
            if (key.indexOf(c) != -1) {
                if (list.isNotEmpty() && i - list.last() == 1) {
                    list[list.lastIndex] = i
                } else {
                    list.add(i)
                }
            }
            i++
        }
        return list
    }

    /**
     * 计算字符串最后出现与字典中字符匹配的位置
     *
     * @param str  数据字符�?     * @param key  字典字符�?     * @param from 从哪个字符开始匹配，默认最末位
     * @param to   匹配到哪个字符（不包含此字符）默�?
     * @return 位置（正向计�?
     */
    private fun seekLast(str: String, key: String, from: Int, to: Int): Int {
        if (str.length - from < 1) return -1
        var i = str.lastIndex
        if (from < i && i > 0) i = from
        var t = 0
        if (to > 0) t = to
        var c: Char
        while (i > t) {
            c = str[i]
            if (key.indexOf(c) != -1) {
                return i
            }
            i--
        }
        return -1
    }

    /**
     * 计算字符串与字典中字符的最短距�?     *
     * @param str     数据字符�?     * @param key     字典字符�?     * @param from    从哪个字符开始匹配，默认0
     * @param to      匹配到哪个字符（不包含此字符）默认匹配到最末位
     * @param inOrder 是否从正向开始匹�?     * @return 返回最短距�? 注意不是str的char的下�?     */
    private fun seekIndex(str: String, key: String, from: Int, to: Int, inOrder: Boolean): Int {
        if (str.length - from < 1) return -1
        var i = 0
        if (from > 0) i = from
        var t = str.length
        if (to > 0) t = min(t, to)
        var c: Char
        while (i < t) {
            c = if (inOrder) str[i] else str[str.length - i - 1]
            if (key.indexOf(c) != -1) {
                return i
            }
            i++
        }
        return -1
    }

    /* 搜寻引号并进行分段。处理了一、二、五三类常见情况
    参照百科词条[引号#应用示例](https://baike.baidu.com/item/%E5%BC%95%E5%8F%B7/998963?#5)对引号内容进行矫正并分句�?    一、完整引用说话内容，在反引号内侧有断句标点。例如：
            1) 丫姑折断几枝扔下来，边叫我的小名儿边说：“先喂饱你！�?            2）“哎呀，真是美极了！”皇帝说，“我十分满意！�?            3）“怕什么！海的美就在这里！”我说道�?    二、部分引用，在反引号外侧有断句标点：
            4）适当地改善自己的生活，岂但“你管得着吗”，而且是顺乎天理，合乎人情的�?            5）现代画家徐悲鸿笔下的马，正如有的评论家所说的那样，“形神兼备，充满生机”�?            6）唐朝的张嘉贞说它“制造奇特，人不知其所为”�?    三、一段接着一段地直接引用时，中间段落只在段首用起引号，该段段尾却不用引回号。但是正统文学不在考虑范围内�?    四、引号里面又要用引号时，外面一层用双引号，里面一层用单引号。暂时不需要考虑
    五、反语和强调，周围没有断句符号�?    */

    //  句子结尾的标点。因为引号可能存在误判，不包含引号�?    private const val MARK_SENTENCES_END = "？。！?!~"
    private const val MARK_SENTENCES_END_P = ".？。！?!~"

    //  句中标点，由于某些网站常把“，”写�?."，故英文句点按照句中标点判断
    private const val MARK_SENTENCES_MID = ".，�?—�?
    private const val MARK_SENTENCES_SAY = "问说喊唱叫骂道着�?

    //  XXX说：“”的冒号
    private const val MARK_QUOTATION_BEFORE = "，：,:"

    //  引号
    private const val MARK_QUOTATION = "\"“�?
    private const val MARK_QUOTATION_RIGHT = "\"�?
    private val PARAGRAPH_DIAGLOG = "^[\"”“][^\"”“]+[\"”“]$".toRegex()

    //  限制字典的长�?    private const val WORD_MAX_LENGTH = 16

    private fun match(rule: String, chr: Char): Boolean {
        return rule.indexOf(chr) != -1
    }
}
