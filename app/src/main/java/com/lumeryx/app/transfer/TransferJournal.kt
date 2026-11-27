package com.lumeryx.app.transfer

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transfer_journal")
internal data class TransferJournalEntry(
    @PrimaryKey val id: String,
    val sourceUri: String,
    val destinationUri: String,
    val sourceName: String,
    val destinationName: String,
    val policy: String,
    val createdAt: Long,
    val status: String = "RUNNING",
    val bytes: Long = 0,
)

@Dao
internal interface TransferJournalDao {
    @Insert
    suspend fun insert(entry: TransferJournalEntry)

    @Query("SELECT * FROM transfer_journal ORDER BY createdAt DESC, id DESC LIMIT 50")
    fun observe(): Flow<List<TransferJournalEntry>>

    @Query("UPDATE transfer_journal SET status = 'INTERRUPTED' WHERE status = 'RUNNING'")
    suspend fun interruptRunning()

    @Query("UPDATE transfer_journal SET status = :status, bytes = :bytes WHERE id = :id AND status = 'RUNNING'")
    suspend fun finish(id: String, status: String, bytes: Long): Int
}

@Database(entities = [TransferJournalEntry::class], version = 1)
internal abstract class TransferJournalDatabase : RoomDatabase() {
    abstract fun journal(): TransferJournalDao

    companion object {
        @Volatile
        private var instance: TransferJournalDatabase? = null

        fun get(context: Context): TransferJournalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TransferJournalDatabase::class.java,
                "transfer-journal.db",
            ).build().also { instance = it }
        }
    }
}
