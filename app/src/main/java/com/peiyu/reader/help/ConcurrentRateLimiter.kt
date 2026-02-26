package com.peiyu.reader.help

import com.peiyu.reader.data.entities.BaseSource
import com.peiyu.reader.exception.ConcurrentException
import com.peiyu.reader.model.analyzeRule.AnalyzeUrl.ConcurrentRecord
import kotlinx.coroutines.delay

class ConcurrentRateLimiter(val source: BaseSource?) {

    companion object {
        private val concurrentRecordMap = hashMapOf<String, ConcurrentRecord>()
    }

    /**
     * 开始访�?并发判断
     */
    @Throws(ConcurrentException::class)
    private fun fetchStart(): ConcurrentRecord? {
        source ?: return null
        val concurrentRate = source.concurrentRate
        if (concurrentRate.isNullOrEmpty() || concurrentRate == "0") {
            return null
        }
        val rateIndex = concurrentRate.indexOf("/")
        var fetchRecord = concurrentRecordMap[source.getKey()]
        if (fetchRecord == null) {
            synchronized(concurrentRecordMap) {
                fetchRecord = concurrentRecordMap[source.getKey()]
                if (fetchRecord == null) {
                    fetchRecord = ConcurrentRecord(rateIndex > 0, System.currentTimeMillis(), 1)
                    concurrentRecordMap[source.getKey()] = fetchRecord
                    return fetchRecord
                }
            }
        }
        val waitTime: Int = synchronized(fetchRecord!!) {
            try {
                if (!fetchRecord.isConcurrent) {
                    //并发控制�?次数/毫秒
                    if (fetchRecord.frequency > 0) {
                        //已经有访问线�?直接等待
                        return@synchronized concurrentRate.toInt()
                    }
                    //没有线程访问,判断还剩多少时间可以访问
                    val nextTime = fetchRecord.time + concurrentRate.toInt()
                    if (System.currentTimeMillis() >= nextTime) {
                        fetchRecord.time = System.currentTimeMillis()
                        fetchRecord.frequency = 1
                        return@synchronized 0
                    }
                    return@synchronized (nextTime - System.currentTimeMillis()).toInt()
                } else {
                    //并发控制�?次数/毫秒
                    val sj = concurrentRate.substring(rateIndex + 1)
                    val nextTime = fetchRecord.time + sj.toInt()
                    if (System.currentTimeMillis() >= nextTime) {
                        //已经过了限制时间,重置开始时�?                        fetchRecord.time = System.currentTimeMillis()
                        fetchRecord.frequency = 1
                        return@synchronized 0
                    }
                    val cs = concurrentRate.substring(0, rateIndex)
                    if (fetchRecord.frequency > cs.toInt()) {
                        return@synchronized (nextTime - System.currentTimeMillis()).toInt()
                    } else {
                        fetchRecord.frequency += 1
                        return@synchronized 0
                    }
                }
            } catch (_: Exception) {
                return@synchronized 0
            }
        }
        if (waitTime > 0) {
            throw ConcurrentException(
                "根据并发率还需等待${waitTime}毫秒才可以访�?,
                waitTime = waitTime
            )
        }
        return fetchRecord
    }

    /**
     * 访问结束
     */
    fun fetchEnd(concurrentRecord: ConcurrentRecord?) {
        if (concurrentRecord != null && !concurrentRecord.isConcurrent) {
            synchronized(concurrentRecord) {
                concurrentRecord.frequency -= 1
            }
        }
    }

    /**
     * 获取并发记录，若处于并发限制状态下则会等待
     */
    suspend fun getConcurrentRecord(): ConcurrentRecord? {
        while (true) {
            try {
                return fetchStart()
            } catch (e: ConcurrentException) {
                delay(e.waitTime.toLong())
            }
        }
    }

    fun getConcurrentRecordBlocking(): ConcurrentRecord? {
        while (true) {
            try {
                return fetchStart()
            } catch (e: ConcurrentException) {
                Thread.sleep(e.waitTime.toLong())
            }
        }
    }

    suspend inline fun <T> withLimit(block: () -> T): T {
        val concurrentRecord = getConcurrentRecord()
        try {
            return block()
        } finally {
            fetchEnd(concurrentRecord)
        }
    }

    inline fun <T> withLimitBlocking(block: () -> T): T {
        val concurrentRecord = getConcurrentRecordBlocking()
        try {
            return block()
        } finally {
            fetchEnd(concurrentRecord)
        }
    }

}
