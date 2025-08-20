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

import com.farmaciasdeGuardia.services.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for service-related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun providePDFCacheService(
        context: android.content.Context,
        cachedPDFDao: com.farmaciasdeGuardia.database.dao.CachedPDFDao,
        httpClient: okhttp3.OkHttpClient,
        sharedPreferences: android.content.SharedPreferences
    ): PDFCacheService {
        return PDFCacheService(context, cachedPDFDao, httpClient, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideLocationService(
        context: android.content.Context
    ): LocationService {
        return LocationService(context)
    }

    @Provides
    @Singleton
    fun provideGeocodingService(
        context: android.content.Context,
        cachedCoordinateDao: com.farmaciasdeGuardia.database.dao.CachedCoordinateDao
    ): GeocodingService {
        return GeocodingService(context, cachedCoordinateDao)
    }

    @Provides
    @Singleton
    fun providePDFProcessingService(): PDFProcessingService {
        return PDFProcessingService()
    }

    @Provides
    @Singleton
    fun provideScheduleService(
        pdfCacheService: PDFCacheService,
        pdfProcessingService: PDFProcessingService
    ): ScheduleService {
        return ScheduleService(pdfCacheService, pdfProcessingService)
    }

    @Provides
    @Singleton
    fun provideZBSScheduleService(): ZBSScheduleService {
        return ZBSScheduleService()
    }

    @Provides
    @Singleton
    fun provideRoutingService(): RoutingService {
        return RoutingService()
    }

    @Provides
    @Singleton
    fun provideClosestPharmacyService(
        scheduleService: ScheduleService,
        zbsScheduleService: ZBSScheduleService,
        geocodingService: GeocodingService,
        routingService: RoutingService
    ): ClosestPharmacyService {
        return ClosestPharmacyService(scheduleService, zbsScheduleService, geocodingService, routingService)
    }
}
