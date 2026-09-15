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

import SwiftUI

/**
 * Banner shown instead of the generic `OfflineWarningCard` when our own sync server appears
 * unreachable specifically because of LaLiga's IP blocking during football matches, rather
 * than a plain offline/server-down state. Tapping opens `LaLigaBlockingDetailSheet`.
 */
struct LaLigaBlockingBanner: View {
    var onTap: () -> Void = {}

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 8) {
                Image(systemName: "soccerball")
                    .foregroundColor(Color.orange)
                    .frame(width: 20)

                Text("Sin conexión al servidor: posible bloqueo de LaLiga en curso")
                    .font(.subheadline)
                    .foregroundColor(Color.orange)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption2)
                    .foregroundColor(Color.orange)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.orange.opacity(0.15))
            )
        }
        .buttonStyle(.plain)
    }
}

/**
 * Detail sheet explaining why the app can't reach its own server: LaLiga's court-authorized
 * IP blocking (which targets Cloudflare-hosted piracy streams but collaterally blocks
 * unrelated sites sharing the same IPs) is the likely cause. Deliberately factual/sourced
 * rather than editorializing.
 */
struct LaLigaBlockingDetailSheet: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 8) {
                    Image(systemName: "soccerball")
                        .foregroundColor(.orange)
                    Text("¿Por qué no se actualizan los horarios?")
                        .font(.title2)
                        .fontWeight(.bold)
                }
                .padding(.top, 48)

                Text("Ahora mismo no podemos conectar con nuestro servidor. Coincide con un partido de LaLiga, y probablemente se deba al bloqueo de direcciones IP que LaLiga ordena a los operadores españoles durante los partidos para cortar las retransmisiones piratas.")
                    .font(.subheadline)

                Text("El problema es que esas direcciones IP son compartidas por Cloudflare entre miles de webs legítimas — incluida, a veces, la nuestra — que quedan bloqueadas como daño colateral sin haber hecho nada ilegal.")
                    .font(.subheadline)

                Text("Desde diciembre de 2024 la Justicia española avala esta práctica, pese a las críticas de Cloudflare y de expertos en ciberseguridad, que la consideran un ataque a la neutralidad de la red.")
                    .font(.subheadline)

                Text("Esto no es un fallo de la app: mientras dure el bloqueo verás los horarios guardados en tu propio teléfono, que pueden no reflejar cambios muy recientes.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                VStack(alignment: .leading, spacing: 10) {
                    LaLigaBlockingLink(
                        text: "Seguimiento en directo de los bloqueos: hayahora.futbol",
                        url: "https://hayahora.futbol",
                        destination: "hayahora"
                    )
                    LaLigaBlockingLink(
                        text: "Comunicado de LaLiga sobre el bloqueo a clientes de Cloudflare",
                        url: "https://www.laliga.com/noticias/laliga-pone-un-buzon-a-disposicion-de-los-clientes-de-cloudflare-afectados-por-los-bloqueos",
                        destination: "laliga_statement"
                    )
                    LaLigaBlockingLink(
                        text: "Análisis técnico independiente del bloqueo (OONI)",
                        url: "https://ooni.org/post/2026-laliga-collateral/",
                        destination: "ooni_report"
                    )
                    LaLigaBlockingLink(
                        text: "El caso de Vercel, afectado por el mismo bloqueo",
                        url: "https://vercel.com/blog/update-on-spain-and-laliga-blocks-of-the-internet",
                        destination: "vercel_blog"
                    )
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .presentationDragIndicator(.visible)
        .presentationDetents([.medium, .large])
    }
}

private struct LaLigaBlockingLink: View {
    let text: String
    let url: String
    let destination: String

    var body: some View {
        Link(destination: URL(string: url)!) {
            Text("\(text) ↗")
                .font(.subheadline.weight(.semibold))
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .simultaneousGesture(TapGesture().onEnded {
            AnalyticsService.shared.track("laliga_blocking_link_tapped", with: ["destination": destination])
        })
    }
}
