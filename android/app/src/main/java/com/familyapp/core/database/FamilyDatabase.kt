package com.familyapp.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.familyapp.core.database.dao.AccountDao
import com.familyapp.core.database.dao.CalendarEventDao
import com.familyapp.core.database.dao.FinanceTransactionDao
import com.familyapp.core.database.dao.ItemDao
import com.familyapp.core.database.entity.AccountEntity
import com.familyapp.core.database.entity.CalendarEventEntity
import com.familyapp.core.database.entity.FinanceTransactionEntity
import com.familyapp.core.database.entity.ItemEntity

@Database(
    entities = [
        ItemEntity::class,
        CalendarEventEntity::class,
        AccountEntity::class,
        FinanceTransactionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FamilyDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun accountDao(): AccountDao
    abstract fun financeTransactionDao(): FinanceTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: FamilyDatabase? = null

        fun getInstance(context: Context): FamilyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FamilyDatabase::class.java,
                    "family_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
