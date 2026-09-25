"use client";

import Link from "next/link";
import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { listTickets, type ListTicketsParams } from "@/lib/api/ticketsApi";
import type { PagedResponse, TicketSummary } from "@/lib/types";
import TicketSearchBar from "@/components/tickets/TicketSearchBar";
import TicketTable from "@/components/tickets/TicketTable";
import Pagination from "@/components/common/Pagination";
import LoadingSpinner from "@/components/common/LoadingSpinner";
import ErrorNotification from "@/components/common/ErrorNotification";

/** Fixed page size for the ticket list. */
const PAGE_SIZE = 20;

/**
 * Ticket list page.
 *
 * Owns the search/filter/pagination state and drives a react-query `useQuery` that
 * calls {@link listTickets}. Submitting the {@link TicketSearchBar} updates the search
 * and status filters and resets the page back to the first page (so results are not
 * shown for an out-of-range page). While a request is in flight a
 * {@link LoadingSpinner} is shown, errors surface through {@link ErrorNotification},
 * and successful data is rendered in the {@link TicketTable} with {@link Pagination}.
 */
export default function TicketListPage() {
  const [search, setSearch] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(0);

  const params: ListTicketsParams = {
    search,
    status,
    page,
    size: PAGE_SIZE,
  };

  const query = useQuery<PagedResponse<TicketSummary>>({
    // Include every filter in the key so react-query caches per distinct query.
    queryKey: ["tickets", { search, status, page, size: PAGE_SIZE }],
    queryFn: () => listTickets(params),
    // Keep showing the previous page while the next one loads to avoid flicker.
    placeholderData: keepPreviousData,
  });

  /** Apply a new search, resetting pagination to the first page. */
  function handleSearch(keyword: string, nextStatus: string | undefined) {
    setSearch(keyword === "" ? undefined : keyword);
    setStatus(nextStatus);
    setPage(0);
  }

  const tickets = query.data?.data ?? [];
  const totalPages = query.data?.totalPages ?? 0;

  return (
    <main style={{ display: "flex", flexDirection: "column", gap: "1rem", padding: "1rem" }}>
      <header style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <h1>Tickets</h1>
        <Link href="/tickets/new">New Ticket</Link>
      </header>

      <TicketSearchBar onSearch={handleSearch} />

      {query.isError && (
        <ErrorNotification message={(query.error as Error).message} />
      )}

      {query.isLoading ? (
        <LoadingSpinner label="Loading tickets" />
      ) : (
        <>
          <TicketTable tickets={tickets} />
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        </>
      )}
    </main>
  );
}
