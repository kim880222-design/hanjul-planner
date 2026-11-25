package com.hanjul.planner.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        ReminderScheduler.createChannels(app)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ReminderScheduler.ACTION_REMIND -> {
                        ReminderScheduler.show(
                            app,
                            intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0) + 1000,
                            ReminderScheduler.CHANNEL_REMIND,
                            intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "알림",
                            intent.getStringExtra(ReminderScheduler.EXTRA_TEXT) ?: "",
                        )
                    }
                    ReminderScheduler.ACTION_SUMMARY -> {
                        ReminderScheduler.summaryText(app)?.let {
                            ReminderScheduler.show(app, 1, ReminderScheduler.CHANNEL_SUMMARY, "☀️ 오늘의 할 일", it)
                        }
                        ReminderScheduler.rescheduleAll(app)
                    }
                    else -> ReminderScheduler.rescheduleAll(app) // 재부팅, 시간 변경 등
                }
            } finally {
                pending.finish()
            }
        }
    }
}
