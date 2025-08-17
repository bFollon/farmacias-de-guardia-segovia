package com.farmaciasdeGuardia.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.farmaciasdeGuardia.database.converters.DatabaseConverters
import com.farmaciasdeGuardia.database.dao.CachedCoordinateDao
import com.farmaciasdeGuardia.database.dao.CachedPDFDao
import com.farmaciasdeGuardia.database.entities.CachedCoordinate
import com.farmaciasdeGuardia.database.entities.CachedPDF

/**
 * Main Room database for Farmacias de Guardia app
 * Handles local caching of PDFs and coordinates
 */
@Database(
    entities = [
        CachedPDF::class,
        CachedCoordinate::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun cachedPDFDao(): CachedPDFDao
    abstract fun cachedCoordinateDao(): CachedCoordinateDao
    
    companion object {
        private const val DATABASE_NAME = "farmacias_de_guardia_db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Get database instance (singleton pattern)
         * This will be replaced by Hilt injection in production
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * For testing purposes - create an in-memory database
         */
        fun createInMemoryDatabase(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).build()
        }
    }
}
