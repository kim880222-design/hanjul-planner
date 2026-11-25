package com.hanjul.planner.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hanjul.planner.R
import com.hanjul.planner.data.Agenda
import com.hanjul.planner.data.Item
import com.hanjul.planner.data.PlannerDatabase
import com.hanjul.planner.data.Step
import com.hanjul.planner.domain.ItemType
import com.hanjul.planner.domain.KoreanDate
import com.hanjul.planner.domain.Recurrence
import com.hanjul.planner.domain.Repeat
import com.hanjul.planner.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 알림 예약. 데이터가 바뀔 때마다 앞으로 7일 동안의 알림을 모두 다시 예약한다.
 * 매일 아침 요약 알림이 울릴 때도 다시 예약하므로 7일보다 먼 알림도 결국 예약된다.
 */
object ReminderScheduler {

    const val CHANNEL_REMIND = "reminders"
    const val CHANNEL_SUMMARY = "summary"
    const val ACTION_REMIND = "com.hanjul.planner.REMIND"
    const val ACTION_SUMMARY = "com.hanjul.planner.SUMMARY"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_ID = "nid"

    private const val PREFS = "alarms"
    private const val KEY_CODES = "codes"
    private const val SUMMARY_CODE = 1
    val MORNING: LocalTime = LocalTime.of(8, 0)
    private val DUE_NOTICE: LocalTime = LocalTime.of(9, 0)

    private data class Alarm(val code: Int, val at: LocalDateTime, val title: String, val text: String)

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMIND, "일정·마감 알림", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "아침 요약", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dao = PlannerDatabase.get(app).dao()
        val items = dao.allItems()
        val steps = dao.allSteps()
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val alarms = buildAlarms(items, steps, now, today)

        val am = app.getSystemService(AlarmManager::class.java)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 예전 예약 지우기
        prefs.getStringSet(KEY_CODES, emptySet())!!.mapNotNull { it.toIntOrNull() }.forEach { code ->
            am.cancel(remindIntent(app, code, "", ""))
        }
        alarms.forEach { a -> set(am, a.at, remindIntent(app, a.code, a.title, a.text)) }
        prefs.edit().putStringSet(KEY_CODES, alarms.map { it.code.toString() }.toSet()).apply()

        // 매일 아침 요약
        val nextMorning = if (now.toLocalTime().isBefore(MORNING)) today.atTime(MORNING)
        else today.plusDays(1).atTime(MORNING)
        set(am, nextMorning, summaryIntent(app))
    }

    private fun buildAlarms(items: List<Item>, steps: List<Step>, now: LocalDateTime, today: LocalDate): List<Alarm> {
        val out = ArrayList<Alarm>()
        val until = today.plusDays(7)
        // 프로젝트 단계 마감: 하루 전, 당일 오전 9시
        for (e in Agenda.allDatedSteps(items, steps)) {
            if (e.project.done || e.project.remindBefore < 0) continue
            val due = e.step.date ?: continue
            val code = 1_000_000 + (e.step.id * 4).toInt()
            out += Alarm(code, due.minusDays(1).atTime(DUE_NOTICE), "📁 내일 마감: ${e.step.title}", "${e.project.title} · ${KoreanDate.date(due, today)}")
            out += Alarm(code + 1, due.atTime(DUE_NOTICE), "📁 오늘 마감: ${e.step.title}", "${e.project.title}의 단계예요")
        }
        for (item in items) {
            if (item.done || item.remindBefore < 0) continue
            val start = item.date ?: continue
            val base = (item.id * 16).toInt()
            when (item.type) {
                ItemType.EVENT -> {
                    Recurrence.occurrencesBetween(start, item.repeat, item.repeatDays, today, until)
                        .take(8).forEachIndexed { i, day ->
                            val at = if (item.time != null) day.atTime(item.time).minusMinutes(item.remindBefore.toLong())
                            else day.atTime(DUE_NOTICE)
                            val text = buildString {
                                append(KoreanDate.date(day, today))
                                item.time?.let { append(" ").append(KoreanDate.time(it)) }
                            }
                            out += Alarm(base + i, at, "📅 ${item.title}", text)
                        }
                }
                ItemType.TASK, ItemType.PROJECT -> {
                    val due = if (item.repeat == Repeat.NONE) start
                    else Recurrence.nextOnOrAfter(start, item.repeat, item.repeatDays, today) ?: start
                    val icon = if (item.type == ItemType.PROJECT) "📁" else "✅"
                    // 하루 전 / 당일
                    out += Alarm(base + 10, due.minusDays(1).atTime(DUE_NOTICE), "$icon 내일 마감: ${item.title}", KoreanDate.date(due, today))
                    val dayOf = if (item.time != null) due.atTime(item.time).minusMinutes(item.remindBefore.toLong())
                    else due.atTime(DUE_NOTICE)
                    out += Alarm(base + 11, dayOf, "$icon 오늘 마감: ${item.title}",
                        item.time?.let { "${KoreanDate.time(it)}까지" } ?: "오늘까지 해야 해요")
                    if (item.type == ItemType.PROJECT) {
                        out += Alarm(base + 12, due.minusDays(3).atTime(DUE_NOTICE), "$icon 마감 3일 전: ${item.title}", KoreanDate.date(due, today))
                    }
                }
            }
        }
        return out.filter { it.at.isAfter(now) && !it.at.toLocalDate().isAfter(until) }
    }

    private fun set(am: AlarmManager, at: LocalDateTime, pi: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun remindIntent(context: Context, code: Int, title: String, text: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_ID, code)
        }
        return PendingIntent.getBroadcast(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun summaryIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_SUMMARY }
        return PendingIntent.getBroadcast(
            context, SUMMARY_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun show(context: Context, id: Int, channel: String, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // 알림 권한이 없으면 조용히 넘어간다
        }
    }

    /** 아침 요약 문장 만들기 */
    suspend fun summaryText(context: Context): String? = withContext(Dispatchers.IO) {
        val dao = PlannerDatabase.get(context).dao()
        val items = dao.allItems()
        val steps = dao.allSteps()
        val today = LocalDate.now()
        val events = Agenda.eventsOn(items, today).size
        val due = Agenda.dueOn(items, today).size + Agenda.stepsDueOn(items, steps, today).size
        val overdue = Agenda.overdue(items, today).size + Agenda.stepsOverdue(items, steps, today).size
        if (events == 0 && due == 0 && overdue == 0) return@withContext null
        listOfNotNull(
            if (events > 0) "일정 ${events}개" else null,
            if (due > 0) "오늘 마감 ${due}개" else null,
            if (overdue > 0) "⚠️ 마감 지난 것 ${overdue}개" else null,
        ).joinToString(" · ")
    }
}
