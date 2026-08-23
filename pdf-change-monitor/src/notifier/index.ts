// PDF Change Monitor
// Copyright (C) 2026 Bruno Follon (@bFollon)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.

import nodemailer from "nodemailer";
import type { ChangeDetail } from "../types.js";

const transport = nodemailer.createTransport({
  host: "smtp.mail.me.com",
  port: 587,
  secure: false,
  auth: {
    user: process.env.SMTP_USER,
    pass: process.env.SMTP_PASS,
  },
});

export async function verifySmtp(): Promise<void> {
  await transport.verify();
}

function describeRefresh(change: ChangeDetail): string {
  const { refresh } = change;
  if (refresh.status === 0) return "  Refresh: FAILED TO CALL - see monitor logs";
  if (refresh.status >= 500) return `  Refresh: server error (HTTP ${refresh.status}) - ${JSON.stringify(refresh.body)}`;
  if (refresh.status === 422) return `  Refresh: validation failed, previous data kept - ${JSON.stringify(refresh.body)}`;
  return `  Refresh: HTTP ${refresh.status} - ${JSON.stringify(refresh.body)}`;
}

export async function sendChangeNotification(changes: ChangeDetail[]): Promise<void> {
  const regionList = changes
    .map((c) => {
      const lines = [`• ${c.regionId}`];
      if (c.urlChanged) {
        lines.push(`  URL: ${c.previousUrl ?? "(none)"} → ${c.currentUrl}`);
      } else {
        lines.push(`  URL: ${c.currentUrl}`);
      }
      lines.push(`  SHA-256: ${c.previousSha256?.slice(0, 16)}… → ${c.currentSha256.slice(0, 16)}…`);
      lines.push(describeRefresh(c));
      return lines.join("\n");
    })
    .join("\n\n");

  const regionIds = changes.map((c) => c.regionId).join(", ");
  const anyFailed = changes.some((c) => c.refresh.status !== 200);

  await transport.sendMail({
    from: `Farmacias de Guardia Monitor <${process.env.SMTP_USER}>`,
    to: process.env.NOTIFY_EMAIL,
    subject: `[Farmacias de Guardia] PDF update ${anyFailed ? "(action needed) " : ""}detected: ${regionIds}`,
    text: [
      "The following pharmacy duty PDFs changed on cofsegovia.com:",
      "",
      regionList,
      "",
      anyFailed
        ? "One or more refreshes did not publish cleanly - check the details above and the server logs."
        : "All affected locations were refreshed and published successfully.",
    ].join("\n"),
  });
}
