package com.momosubtitle.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mediaUri: String,
    val mediaName: String,
    val durationSec: Double,
    val createdAt: Long
)

@Entity(tableName = "runs", indices = [Index("projectId")])
data class TranscriptionRunEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val modelId: String,
    val mode: String,
    val language: String,
    val chunkSeconds: Int,
    val status: String,
    val stage: String,
    val totalChunks: Int,
    val doneChunks: Int,
    val detectedLanguage: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "chunks", indices = [Index("runId")])
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val status: String,
    val errorInfo: String?,
    val segmentCount: Int
)

@Entity(tableName = "segments", indices = [Index("runId")])
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val seq: Int,
    val startMs: Long,
    val endMs: Long,
    val englishText: String,
    val originalText: String?,
    val sourceChunk: Int
)

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: ProjectEntity)

    @Query("UPDATE projects SET durationSec = :durationSec WHERE id = :id")
    suspend fun updateDuration(id: String, durationSec: Double)

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?
}

@Dao
interface RunDao {
    @Insert
    suspend fun insert(run: TranscriptionRunEntity)

    @Update
    suspend fun update(run: TranscriptionRunEntity)

    @Query("SELECT * FROM runs WHERE id = :id")
    fun observe(id: String): Flow<TranscriptionRunEntity?>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: String): TranscriptionRunEntity?

    @Query("SELECT * FROM runs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TranscriptionRunEntity>>
}

@Dao
interface ChunkDao {
    @Insert
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("SELECT * FROM chunks WHERE runId = :runId ORDER BY `index`")
    fun observeForRun(runId: String): Flow<List<ChunkEntity>>

    @Query("SELECT * FROM chunks WHERE runId = :runId ORDER BY `index`")
    suspend fun forRun(runId: String): List<ChunkEntity>

    @Query(
        "UPDATE chunks SET status = :status, errorInfo = :errorInfo, segmentCount = :segmentCount " +
            "WHERE runId = :runId AND `index` = :index"
    )
    suspend fun updateStatus(runId: String, index: Int, status: String, errorInfo: String?, segmentCount: Int)

    @Query("DELETE FROM chunks WHERE runId = :runId")
    suspend fun deleteForRun(runId: String)
}

@Dao
interface SegmentDao {
    @Insert
    suspend fun insertAll(segments: List<SegmentEntity>)

    @Query("SELECT * FROM segments WHERE runId = :runId ORDER BY seq")
    fun observeForRun(runId: String): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE runId = :runId ORDER BY seq")
    suspend fun forRun(runId: String): List<SegmentEntity>

    @Query("UPDATE segments SET seq = :seq WHERE id = :id")
    suspend fun updateSeq(id: Long, seq: Int)

    @Query("SELECT COUNT(*) FROM segments WHERE runId = :runId")
    suspend fun countForRun(runId: String): Int
}

@Database(
    entities = [
        ProjectEntity::class,
        TranscriptionRunEntity::class,
        ChunkEntity::class,
        SegmentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun runDao(): RunDao
    abstract fun chunkDao(): ChunkDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "momo-subtitle.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
