package com.noor.shoplocal.data.offline

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * A shop action the user performed while offline, stored locally in SQLite (Room)
 * and replayed against the REST API once connectivity returns. This is the backbone
 * of the app's offline-mode-with-sync feature.
 */
@Entity(tableName = "pending_ops")
data class PendingOp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,            // WISHLIST_ADD | CART_SET
    val productId: String,
    val quantity: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val WISHLIST_ADD = "WISHLIST_ADD"
        const val CART_SET = "CART_SET"
    }
}

@Dao
interface PendingOpDao {
    @Insert
    suspend fun insert(op: PendingOp): Long

    @Query("SELECT * FROM pending_ops ORDER BY createdAt ASC")
    suspend fun all(): List<PendingOp>

    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM pending_ops")
    suspend fun count(): Int
}

@Database(entities = [PendingOp::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingOps(): PendingOpDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "shoplocal.db"
                ).build().also { instance = it }
            }
    }
}
