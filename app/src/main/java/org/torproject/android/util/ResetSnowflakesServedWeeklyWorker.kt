<<<<<<<< HEAD:orbotservice/src/main/java/org/torproject/android/service/circumvention/ResetSnowflakesServedWeeklyWorker.kt
package org.torproject.android.service.circumvention
========
package org.torproject.android.util
>>>>>>>> 3a31ff56ad93822f874270ad785ec064b4a60ef4:app/src/main/java/org/torproject/android/util/ResetSnowflakesServedWeeklyWorker.kt

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.torproject.android.service.util.Prefs

class ResetSnowflakesServedWeeklyWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    override fun doWork(): Result {
        Prefs.resetSnowflakesServedWeekly()
        return Result.success()
    }
}