package com.lumeryx.app.library

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "library_root")
internal data class LibraryRootEntity(
    @PrimaryKey val slot: Int = 1,
    val treeUri: String,
)

@Entity(tableName = "favorites", primaryKeys = ["treeUri", "documentId"])
internal data class FavoriteEntity(
    val treeUri: String,
    val documentId: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val modifiedMillis: Long?,
)

@Dao
internal interface LibraryDao {
    @Query("SELECT treeUri FROM library_root WHERE slot = 1")
    suspend fun selectedTreeUri(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun selectRoot(root: LibraryRootEntity)

    @Query("SELECT * FROM favorites WHERE treeUri = :treeUri")
    fun favorites(treeUri: String): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE treeUri = :treeUri AND documentId = :documentId")
    suspend fun removeFavorite(treeUri: String, documentId: String)
}

@Database(entities = [LibraryRootEntity::class, FavoriteEntity::class], version = 1)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var instance: LibraryDatabase? = null

        fun get(context: Context): LibraryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LibraryDatabase::class.java,
                "local-library.db",
            ).build().also { instance = it }
        }
    }
}
