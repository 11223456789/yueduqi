@file:Suppress("unused")

package com.peiyu.reader.exception

/**
 * 并发限制
 */
class ConcurrentException(msg: String, val waitTime: Int) : NoStackTraceException(msg)
