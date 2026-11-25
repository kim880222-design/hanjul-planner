package com.hanjul.planner.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeItems(): Flow<List<Item>>

    @Query("SELECT * FROM items")
    suspend fun allItems(): List<Item>

    @Query("SELECT * FROM steps ORDER BY projectId, position")
    fun observeSteps(): Flow<List<Step>>

    @Query("SELECT * FROM steps")
    suspend fun allSteps(): List<Step>

    @Query("SELECT * FROM steps WHERE projectId = :projectId ORDER BY position")
    suspend fun stepsOf(projectId: Long): List<Step>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun item(id: Long): Item?

    @Insert suspend fun insert(item: Item): Long
    @Update suspend fun update(item: Item)
    @Delete suspend fun delete(item: Item)

    @Insert suspend fun insertStep(step: Step): Long
    @Insert suspend fun insertSteps(steps: List<Step>)
    @Update suspend fun updateStep(step: Step)
    @Update suspend fun updateSteps(steps: List<Step>)
    @Delete suspend fun deleteStep(step: Step)

    @Transaction
    suspend fun insertProject(project: Item, steps: List<Pair<String, java.time.LocalDate?>>): Long {
        val id = insert(project)
        insertSteps(steps.mapIndexed { i, (t, d) -> Step(projectId = id, title = t, position = i, date = d) })
        return id
    }
}

@Database(entities = [Item::class, Step::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun dao(): PlannerDao

    companion object {
        @Volatile private var instance: PlannerDatabase? = null

        fun get(context: Context): PlannerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, PlannerDatabase::class.java, "planner.db",
            ).build().also { instance = it }
        }
    }
}
