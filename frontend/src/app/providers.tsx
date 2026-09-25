"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

/**
 * Client-side provider wrapper.
 *
 * Creates a single {@link QueryClient} instance per browser session (via `useState`
 * initializer so it is stable across re-renders) and makes it available to the whole
 * component tree through {@link QueryClientProvider}. This lives in a dedicated client
 * component so the root layout can remain a server component.
 */
export default function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            staleTime: 30_000,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
