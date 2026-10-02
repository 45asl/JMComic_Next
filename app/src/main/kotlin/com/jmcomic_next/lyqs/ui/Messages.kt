package com.jmcomic_next.lyqs.ui

import com.jmcomic_next.lyqs.data.remote.JmException

/**
 * 把异常转成能直接显示给用户的一句话。
 *
 * 放在 ui 顶层而不是某个页面里：[JmException] 自带的已经是**面向用户**的中文文案，
 * 其它异常只保证 `message` 非空，两者都要落到同一类提示上；而需要提示的不止一个页面。
 * 用 `message` 而不是 `toString()` —— 后者会把类名与堆栈带进界面。
 */
internal fun Throwable?.toUserMessage(): String? = when (this) {
    null -> null
    is JmException -> message
    else -> message ?: "未知错误"
}
