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

package com.bfollon.farmaciasdeGuardia.di

import android.content.Context
import android.content.SharedPreferences
import com.bfollon.farmaciasdeGuardia.database.dao.CachedCoordinateDao
import com.bfollon.farmaciasdeGuardia.database.dao.CachedPDFDao
import com.bfollon.farmaciasdeGuardia.services.ClosestPharmacyService
import com.bfollon.farmaciasdeGuardia.services.GeocodingService
import com.bfollon.farmaciasdeGuardia.services.LocationService
import com.bfollon.farmaciasdeGuardia.services.PDFCacheService
import com.bfollon.farmaciasdeGuardia.services.PDFProcessingService
import com.bfollon.farmaciasdeGuardia.services.RoutingService
import com.bfollon.farmaciasdeGuardia.services.ScheduleService
import com.bfollon.farmaciasdeGuardia.services.ZBSScheduleService
import com.bfollon.farmaciasdeGuardia.services.pdfparsing.PDFParsingStrategyFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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
        context: Context,
        cachedPDFDao: CachedPDFDao,
        httpClient: OkHttpClient,
        sharedPreferences: SharedPreferences
    ): PDFCacheService {
        return PDFCacheService(context, cachedPDFDao, httpClient, sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideLocationService(
        context: Context
    ): LocationService {
        return LocationService(context)
    }

    @Provides
    @Singleton
    fun provideGeocodingService(
        context: Context,
        cachedCoordinateDao: CachedCoordinateDao
    ): GeocodingService {
        return GeocodingService(context, cachedCoordinateDao)
    }

    @Provides
    @Singleton
    fun providePDFProcessingService(
        @ApplicationContext context: Context,
        geocodingService: GeocodingService,
        parsingStrategyFactory: PDFParsingStrategyFactory
    ): PDFProcessingService {
        return PDFProcessingService(context, geocodingService, parsingStrategyFactory)
    }

    @Provides
    @Singleton
    fun providePDFParsingStrategyFactory(): PDFParsingStrategyFactory {
        return PDFParsingStrategyFactory()
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
        return ClosestPharmacyService(
            scheduleService,
            zbsScheduleService,
            geocodingService,
            routingService
        )
    }
}
