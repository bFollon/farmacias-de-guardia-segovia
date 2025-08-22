/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.bfollon.farmaciasdeGuardia.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.bfollon.farmaciasdeGuardia.database.converters.DatabaseConverters
import com.bfollon.farmaciasdeGuardia.database.dao.CachedCoordinateDao
import com.bfollon.farmaciasdeGuardia.database.dao.CachedPDFDao
import com.bfollon.farmaciasdeGuardia.database.entities.CachedCoordinate
import com.bfollon.farmaciasdeGuardia.database.entities.CachedPDF

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
