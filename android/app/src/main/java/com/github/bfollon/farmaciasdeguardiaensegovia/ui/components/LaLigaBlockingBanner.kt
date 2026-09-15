/*
 * Copyright (C) 2026  Bruno Follon (@bFollon)
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

package com.github.bfollon.farmaciasdeguardiaensegovia.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.github.bfollon.farmaciasdeguardiaensegovia.services.AnalyticsService

/**
 * Banner shown instead of the generic [OfflineWarningCard] when our own sync server appears
 * unreachable specifically because of LaLiga's IP blocking during football matches, rather
 * than a plain offline/server-down state.
 */
@Composable
fun LaLigaBlockingBanner(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val color = Color(0xFFFFA726)

    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SportsSoccer,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Sin conexión al servidor: posible bloqueo de LaLiga en curso",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Detail sheet explaining why the app can't reach its own server: LaLiga's court-authorized
 * IP blocking (which targets Cloudflare-hosted piracy streams but collaterally blocks
 * unrelated sites sharing the same IPs) is the likely cause. Deliberately factual/sourced
 * rather than editorializing.
 */
@Composable
fun LaLigaBlockingDetailSheet() {
    val context = LocalContext.current
    val color = Color(0xFFFFA726)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SportsSoccer,
                contentDescription = null,
                tint = color,
            )
            Text(
                text = "¿Por qué no se actualizan los horarios?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = "Ahora mismo no podemos conectar con nuestro servidor. Coincide con un partido de LaLiga, y probablemente se deba al bloqueo de direcciones IP que LaLiga ordena a los operadores españoles durante los partidos para cortar las retransmisiones piratas.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = "El problema es que esas direcciones IP son compartidas por Cloudflare entre miles de webs legítimas — incluida, a veces, la nuestra — que quedan bloqueadas como daño colateral sin haber hecho nada ilegal.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = "Desde diciembre de 2024 la Justicia española avala esta práctica, pese a las críticas de Cloudflare y de expertos en ciberseguridad, que la consideran un ataque a la neutralidad de la red.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = "Esto no es un fallo de la app: mientras dure el bloqueo verás los horarios guardados en tu propio teléfono, que pueden no reflejar cambios muy recientes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "Más información en hayahora.futbol →",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                AnalyticsService.track("laliga_blocking_link_tapped")
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://hayahora.futbol".toUri()))
            },
        )
    }
}
