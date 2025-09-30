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
         * For reporting errors in pharmacy schedule data
         */
        fun errorReport(
            subject: String = "Error en Farmacias de Guardia",
            body: String = ""
        ): String {
            val finalBody = body.ifEmpty { defaultErrorReportBody }
            val encodedSubject = URLEncoder.encode(subject, "UTF-8")
            val encodedBody = URLEncoder.encode(finalBody, "UTF-8")
            return "mailto:$CONTACT_EMAIL?subject=$encodedSubject&body=$encodedBody"
        }
        
        /**
         * For feedback and feature requests
         */
        fun feedback(
            subject: String = "Sugerencias y mejoras - Farmacias de Guardia",
            body: String = "Me gustaría sugerir..."
        ): String {
            val encodedSubject = URLEncoder.encode(subject, "UTF-8")
            val encodedBody = URLEncoder.encode(body, "UTF-8")
            return "mailto:$CONTACT_EMAIL?subject=$encodedSubject&body=$encodedBody"
        }
        
        /**
         * For general contact
         */
        fun general(
            subject: String = "Contacto - Farmacias de Guardia",
            body: String = ""
        ): String {
            val encodedSubject = URLEncoder.encode(subject, "UTF-8")
            val encodedBody = URLEncoder.encode(body, "UTF-8")
            return "mailto:$CONTACT_EMAIL?subject=$encodedSubject&body=$encodedBody"
        }
        
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


