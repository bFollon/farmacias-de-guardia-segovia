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

package com.github.bfollon.farmaciasdeguardiaensegovia.data

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global application configuration
 */
object AppConfig {
    /** Contact email for error reporting, feedback, and general inquiries */
    const val CONTACT_EMAIL = "bfollon.dev@icloud.com"
    
    /** Ko-fi support link */
    const val KOFI_URL = "https://ko-fi.com/bfollon"
    
    /** GitHub repository URL */
    const val GITHUB_REPO_URL = "https://github.com/bFollon/farmacias-de-guardia-segovia"
    
    /** GitHub profile URL */
    const val GITHUB_PROFILE_URL = "https://github.com/bFollon"
    
    /**
     * Email links for common contact purposes
     */
    object EmailLinks {
        /**
         * Encode string for mailto URI (replaces + with %20 for proper space encoding)
         */
        private fun encodeMailtoParameter(text: String): String {
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        }
        
        /**
         * For reporting errors in pharmacy schedule data
         */
        fun errorReport(
            subject: String = "Error en Farmacias de Guardia",
            body: String = ""
        ): String {
            val finalBody = body.ifEmpty { defaultErrorReportBody }
            val encodedSubject = encodeMailtoParameter(subject)
            val encodedBody = encodeMailtoParameter(finalBody)
            return "mailto:$CONTACT_EMAIL?subject=$encodedSubject&body=$encodedBody"
        }
        
        /**
         * For feedback and feature requests
         */
        fun feedback(
            subject: String = "Sugerencias y mejoras - Farmacias de Guardia",
            body: String = "Me gustaría sugerir..."
        ): String {
            val encodedSubject = encodeMailtoParameter(subject)
            val encodedBody = encodeMailtoParameter(body)
            return "mailto:$CONTACT_EMAIL?subject=$encodedSubject&body=$encodedBody"
        }
        
        /**
         * For general contact
         */
        fun general(
            subject: String = "Contacto - Farmacias de Guardia",
            body: String = ""
        ): String {
            val encodedSubject = encodeMailtoParameter(subject)
            val encodedBody = encodeMailtoParameter(body)
            return "mailto:$CONTACT_EMAIL?subject=$encodedSubject&body=$encodedBody"
        }
        
        /**
         * Generate error report email body for daily schedule errors
         */
        fun dayScheduleErrorBody(
            date: String,
            dayPharmacyName: String,
            dayPharmacyAddress: String,
            nightPharmacyName: String,
            nightPharmacyAddress: String
        ): String = """
            Hola,
            
            He encontrado un error en las farmacias de guardia mostradas para:
            
            Fecha: $date
            
            Turno diurno:
            Farmacia mostrada: $dayPharmacyName
            Dirección: $dayPharmacyAddress
            
            Turno nocturno:
            Farmacia mostrada: $nightPharmacyName
            Dirección: $nightPharmacyAddress
            
            La información correcta es:
            
            
            Gracias.
        """.trimIndent()
        
        /**
         * Generate error report email body for ZBS schedule errors
         */
        fun zbsScheduleErrorBody(
            date: String,
            zbsName: String,
            pharmacyName: String,
            pharmacyAddress: String
        ): String = """
            Hola,
            
            He encontrado un error en la farmacia de guardia mostrada para:
            
            Fecha: $date
            ZBS: $zbsName
            Farmacia mostrada: $pharmacyName
            Dirección: $pharmacyAddress
            
            La farmacia correcta es:
            
            
            Gracias.
        """.trimIndent()
        
        /**
         * Generate error report email body for schedule content errors
         */
        fun scheduleContentErrorBody(
            dateTime: String,
            shiftName: String,
            pharmacyName: String,
            pharmacyAddress: String
        ): String = """
            Hola,
            
            He encontrado un error en la farmacia de guardia mostrada para:
            
            Fecha y hora: $dateTime
            Turno: $shiftName
            Farmacia mostrada: $pharmacyName
            Dirección: $pharmacyAddress
            
            La farmacia correcta es:
            
            
            Gracias.
        """.trimIndent()
        
        /**
         * Get current date and time formatted for email reporting
         */
        private fun getCurrentEmailDateTime(): String {
            val today = Date()
            val locale = Locale.forLanguageTag("es-ES")
            val dateFormatter = SimpleDateFormat("EEEE d 'de' MMMM", locale)
            val timeFormatter = SimpleDateFormat("HH:mm", locale)
            
            return "${dateFormatter.format(today)} · ${timeFormatter.format(today)}"
        }
        
        /**
         * Generate error report email body for current schedule content errors (uses current time)
         */
        fun currentScheduleContentErrorBody(
            shiftName: String,
            pharmacyName: String,
            pharmacyAddress: String
        ): String = scheduleContentErrorBody(
            dateTime = getCurrentEmailDateTime(),
            shiftName = shiftName,
            pharmacyName = pharmacyName,
            pharmacyAddress = pharmacyAddress
        )
        
        /**
         * Generate error report email body when there's no pharmacy assigned
         */
        fun noPharmacyAssignedErrorBody(
            dateTime: String,
            location: String
        ): String = """
            Hola,
            
            He encontrado un problema en la información mostrada:
            
            Fecha y hora: $dateTime
            Ubicación: $location
            
            Problema: No hay farmacia de guardia asignada para esta fecha/hora, pero debería haberla.
            
            Información adicional:
            
            
            Gracias.
        """.trimIndent()
        
        /**
         * Generate error report email body for current date when there's no pharmacy assigned
         */
        fun currentNoPharmacyAssignedErrorBody(
            location: String
        ): String = noPharmacyAssignedErrorBody(
            dateTime = getCurrentEmailDateTime(),
            location = location
        )
        
        /**
         * Default body template for error reports
         */
        private val defaultErrorReportBody = """
            Hola,
            
            He encontrado un error en la información mostrada en la app.
            
            Detalles del error:
            
            
            La información correcta es:
            
            
            Gracias.
        """.trimIndent()
    }
}





