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

package com.farmaciasdeGuardia.di

import android.content.Context
import androidx.room.Room
import com.farmaciasdeGuardia.database.AppDatabase
import com.farmaciasdeGuardia.database.dao.CachedCoordinateDao
import com.farmaciasdeGuardia.database.dao.CachedPDFDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database dependencies
 * Provides Room database and DAO instances
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Provides the main Room database instance
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "farmacias_de_guardia_db"
        )
        .fallbackToDestructiveMigration() // For development - remove in production
        .build()
    }
    
    /**
     * Provides CachedPDFDao
     */
    @Provides
    fun provideCachedPDFDao(database: AppDatabase): CachedPDFDao {
        return database.cachedPDFDao()
    }
    
    /**
     * Provides CachedCoordinateDao
     */
    @Provides
    fun provideCachedCoordinateDao(database: AppDatabase): CachedCoordinateDao {
        return database.cachedCoordinateDao()
    }
}
