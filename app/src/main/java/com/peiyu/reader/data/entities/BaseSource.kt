package com.peiyu.reader.data.entities

import cn.hutool.crypto.symmetric.AES
import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import com.peiyu.reader.constant.AppConst
import com.peiyu.reader.constant.AppLog
import com.peiyu.reader.data.entities.rule.RowUi
import com.peiyu.reader.help.CacheManager
import com.peiyu.reader.help.JsExtensions
import com.peiyu.reader.help.config.AppConfig
import com.peiyu.reader.help.crypto.SymmetricCryptoAndroid
import com.peiyu.reader.help.http.CookieStore
import com.peiyu.reader.help.source.getShareScope
import com.peiyu.reader.utils.GSON
import com.peiyu.reader.utils.GSONStrict
import com.peiyu.reader.utils.fromJsonArray
import com.peiyu.reader.utils.fromJsonObject
import com.peiyu.reader.utils.has
import com.peiyu.reader.utils.printOnDebug
import org.intellij.lang.annotations.Language

/**
 * 可在js里调�?source.xxx()
 */
@Suppress("unused")
interface BaseSource : JsExtensions {
    /**
     * 并发�?     */
    var concurrentRate: String?

    /**
     * 登录地址
     */
    var loginUrl: String?

    /**
     * 登录UI
     */
    var loginUi: String?

    /**
     * 请求�?     */
    var header: String?

    /**
     * 启用cookieJar
     */
    var enabledCookieJar: Boolean?

    /**
     * js�?     */
    var jsLib: String?

    fun getTag(): String

    fun getKey(): String

    override fun getSource(): BaseSource? {
        return this
    }

    fun loginUi(): List<RowUi>? {
        return GSON.fromJsonArray<RowUi>(loginUi).onFailure {
            it.printOnDebug()
        }.getOrNull()
    }

    fun getLoginJs(): String? {
        val loginJs = loginUrl
        return when {
            loginJs == null -> null
            loginJs.startsWith("@js:") -> loginJs.substring(4)
            loginJs.startsWith("<js>") -> loginJs.substring(4, loginJs.lastIndexOf("<"))
            else -> loginJs
        }
    }

    /**
     * 调用login函数 实现登录请求
     */
    fun login() {
        val loginJs = getLoginJs()
        if (!loginJs.isNullOrBlank()) {
            @Language("js")
            val js = """$loginJs
                if(typeof login=='function'){
                    login.apply(this);
                } else {
                    throw('Function login not implements!!!')
                }
            """.trimIndent()
            evalJS(js)
        }
    }

    /**
     * 解析header规则
     */
    fun getHeaderMap(hasLoginHeader: Boolean = false) = HashMap<String, String>().apply {
        header?.let {
            try {
                val json = when {
                    it.startsWith("@js:", true) -> evalJS(it.substring(4)).toString()
                    it.startsWith("<js>", true) -> evalJS(
                        it.substring(4, it.lastIndexOf("<"))
                    ).toString()

                    else -> it
                }
                GSONStrict.fromJsonObject<Map<String, String>>(json).getOrNull()?.let { map ->
                    putAll(map)
                } ?: GSON.fromJsonObject<Map<String, String>>(json).getOrNull()?.let { map ->
                    log("请求头规�?JSON 格式不规范，请改为规范格�?)
                    putAll(map)
                }
            } catch (e: Exception) {
                AppLog.put("执行请求头规则出错\n$e", e)
            }
        }
        if (!has(AppConst.UA_NAME, true)) {
            put(AppConst.UA_NAME, AppConfig.userAgent)
        }
        if (hasLoginHeader) {
            getLoginHeaderMap()?.let {
                putAll(it)
            }
        }
    }

    /**
     * 获取用于登录的头部信�?     */
    fun getLoginHeader(): String? {
        return CacheManager.get("loginHeader_${getKey()}")
    }

    fun getLoginHeaderMap(): Map<String, String>? {
        val cache = getLoginHeader() ?: return null
        return GSON.fromJsonObject<Map<String, String>>(cache).getOrNull()
    }

    /**
     * 保存登录头部信息,map格式,访问时自动添�?     */
    fun putLoginHeader(header: String) {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(header).getOrNull()
        val cookie = headerMap?.get("Cookie") ?: headerMap?.get("cookie")
        cookie?.let {
            CookieStore.replaceCookie(getKey(), it)
        }
        CacheManager.put("loginHeader_${getKey()}", header)
    }

    fun removeLoginHeader() {
        CacheManager.delete("loginHeader_${getKey()}")
        CookieStore.removeCookie(getKey())
    }

    /**
     * 获取用户信息,可以用来登录
     * 用户信息采用aes加密存储
     */
    fun getLoginInfo(): String? {
        try {
            val key = AppConst.androidId.encodeToByteArray(0, 16)
            val cache = CacheManager.get("userInfo_${getKey()}") ?: return null
            return AES(key).decryptStr(cache)
        } catch (e: Exception) {
            AppLog.put("获取登陆信息出错", e)
            return null
        }
    }

    fun getLoginInfoMap(): Map<String, String>? {
        return GSON.fromJsonObject<Map<String, String>>(getLoginInfo()).getOrNull()
    }

    /**
     * 保存用户信息,aes加密
     */
    fun putLoginInfo(info: String): Boolean {
        return try {
            val key = (AppConst.androidId).encodeToByteArray(0, 16)
            val encodeStr = SymmetricCryptoAndroid("AES", key).encryptBase64(info)
            CacheManager.put("userInfo_${getKey()}", encodeStr)
            true
        } catch (e: Exception) {
            AppLog.put("保存登陆信息出错", e)
            false
        }
    }

    fun removeLoginInfo() {
        CacheManager.delete("userInfo_${getKey()}")
    }

    /**
     * 设置自定义变�?     * @param variable 变量内容
     */
    fun setVariable(variable: String?) {
        if (variable != null) {
            CacheManager.put("sourceVariable_${getKey()}", variable)
        } else {
            CacheManager.delete("sourceVariable_${getKey()}")
        }
    }

    /**
     * 获取自定义变�?     */
    fun getVariable(): String {
        return CacheManager.get("sourceVariable_${getKey()}") ?: ""
    }

    /**
     * 保存数据
     */
    fun put(key: String, value: String): String {
        CacheManager.put("v_${getKey()}_${key}", value)
        return value
    }

    /**
     * 获取保存的数�?     */
    fun get(key: String): String {
        return CacheManager.get("v_${getKey()}_${key}") ?: ""
    }

    /**
     * 执行JS
     */
    @Throws(Exception::class)
    fun evalJS(jsStr: String, bindingsConfig: ScriptBindings.() -> Unit = {}): Any? {
        val bindings = buildScriptBindings { bindings ->
            bindings["java"] = this
            bindings["source"] = this
            bindings["baseUrl"] = getKey()
            bindings["cookie"] = CookieStore
            bindings["cache"] = CacheManager
            bindings.apply(bindingsConfig)
        }
        val sharedScope = getShareScope()
        val scope = if (sharedScope == null) {
            RhinoScriptEngine.getRuntimeScope(bindings)
        } else {
            bindings.apply {
                prototype = sharedScope
            }
        }
        return RhinoScriptEngine.eval(jsStr, scope)
    }
}
