package com.jmcomic_next.lyqs.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jmcomic_next.lyqs.JmApp
import com.jmcomic_next.lyqs.data.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 「你追的连载更新了」的**可选**系统通知（1.5.3）。
 *
 * ## 为什么是 AlarmManager 而不是 WorkManager
 *
 * 项目里原本没有 WorkManager。为这一个功能引入一个新的构建依赖（还要考虑版本兼容与网络拉取）
 * 不划算；而这件事的需求很朴素 —— **每隔半天左右醒一次、联网、比一下、必要时发一条通知**。
 * `setInexactRepeating` 正好是这个语义：系统会按自己的判断合并、延迟到合适的时机（省电），
 * 我们不需要精确时间，也**不应该**要精确时间。
 *
 * ## 三条自我约束
 *
 * 1. **默认关闭**。只在用户明确打开后才排程；关掉时立刻取消。
 * 2. **不重复打扰**：把"上次通知过的那批更新"的指纹存下来，指纹没变就不发。
 *    否则每醒一次都推一条一模一样的通知，用户很快会把通知权限关掉。
 * 3. **失败什么都不做**：网络失败、解析失败一律静默退出 —— 后台任务不该因为拿不到数据而崩溃或弹错。
 */
object SerialNotify {

    private const val CHANNEL_ID = "serial_updates"
    private const val REQUEST_CODE = 0x5E71
    private const val NOTIFICATION_ID = 0x5E72

    /** 半天醒一次。`setInexactRepeating` 会把它交给系统调度，实际间隔可能更长 —— 这是有意的。 */
    private const val INTERVAL_MS = 12L * 60 * 60 * 1000

    fun enable(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + INTERVAL_MS,
            INTERVAL_MS,
            pendingIntent(context),
        )
    }

    fun disable(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SerialNotifyReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 跑一次检查并按需通知。返回是否真的发了通知（便于测试与日志）。
     *
     * 抽成普通函数而不是写在 Receiver 里：Receiver 只是"闹钟响了"的入口，
     * 真正的逻辑要能被直接调用与验证。
     */
    suspend fun check(context: Context): Boolean {
        val prefs = AppPrefs(context)
        if (!prefs.serialNotify) return false

        val app = context.applicationContext as? JmApp ?: return false
        // 数据源是**服务端的未读通知数**：服务端在追更作品更新时生成通知，
        // 我们只镜像"未读变多了"，不去猜有没有更新。失败一律静默。
        val unread = runCatching { app.repository.notificationsUnread() }.getOrNull() ?: return false
        val count = unread.total
        // 没变多就不打扰：只有"比上次通知时更多"才值得再响一次
        if (count <= 0 || count <= prefs.serialNotifySeen) return false

        notify(context, count)
        prefs.serialNotifySeen = count
        return true
    }

    private fun notify(context: Context, count: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "连载更新",
                    // DEFAULT 而不是 HIGH：这是"有空看看"的提醒，不是需要立刻打断用户的事
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = open?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val text = "你追的连载有 $count 条新通知"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("连载更新")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        // 权限可能被用户拒绝（Android 13+）：notify 会抛 SecurityException，这里吞掉
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}

/** 闹钟入口。只负责把工作交给一个协程，然后让系统知道"可以回收这个广播了"。 */
class SerialNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SerialNotify.check(context.applicationContext)
            } catch (_: Throwable) {
                // 后台任务绝不因为异常而崩掉
            } finally {
                pending.finish()
            }
        }
    }
}
