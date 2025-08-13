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
