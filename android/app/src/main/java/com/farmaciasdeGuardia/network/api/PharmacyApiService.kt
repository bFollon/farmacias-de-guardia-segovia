package com.farmaciasdeGuardia.network.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Url

/**
 * Retrofit API interface for pharmacy-related network operations
 * Handles PDF downloads and remote file operations
 */
interface PharmacyApiService {
    
    /**
     * Download a PDF file from a given URL
     * Used for downloading pharmacy schedule PDFs
     */
    @GET
    suspend fun downloadPDF(@Url url: String): Response<ResponseBody>
    
    /**
     * Get file metadata (HEAD request) to check version/modification date
     * Equivalent to iOS PDFCacheManager's checkRemoteVersion
     */
    @HEAD
    suspend fun getFileMetadata(@Url url: String): Response<Unit>
}
