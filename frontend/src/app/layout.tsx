import type { Metadata } from "next";
import type { ReactNode } from "react";
import Providers from "./providers";

export const metadata: Metadata = {
  title: "Support Ticket Management",
  description: "AI-powered support ticket management system",
};

/**
 * Root layout for the application.
 *
 * Renders the top-level html/body shell and wraps all pages in {@link Providers}
 * so React Query is available throughout the component tree.
 */
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
