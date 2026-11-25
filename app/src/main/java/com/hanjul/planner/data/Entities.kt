package com.hanjul.planner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.hanjul.planner.domain.ItemType
import com.hanjul.planner.domain.Repeat
import java.time.LocalDate
import java.time.LocalTime

/**
 * 할 일 / 일정 / 프로젝트를 한 표에 저장한다.
 * - 할 일: date = 마감일(없을 수 있음), time = 마감 시각(선택)
 * - 일정: date = 날짜, time = 시작 시각(없으면 하루 종일), endTime = 끝 시각
 * - 프로젝트: date = 목표 마감일(선택). 단계는 Step 표에.
 */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    val title: String,
    val note: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val endTime: LocalTime? = null,
    val repeat: Repeat = Repeat.NONE,
    val repeatDays: Int = 0,
    /** 몇 분 전에 알릴지. -1 이면 알림 없음 */
    val remindBefore: Int = 30,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "steps",
    foreignKeys = [ForeignKey(
        entity = Item::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("projectId")],
)
data class Step(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val position: Int,
    val done: Boolean = false,
    val date: LocalDate? = null,
)

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let { LocalDate.ofEpochDay(it) }
    @TypeConverter fun timeToInt(t: LocalTime?): Int? = t?.toSecondOfDay()
    @TypeConverter fun intToTime(v: Int?): LocalTime? = v?.let { LocalTime.ofSecondOfDay(it.toLong()) }
}
