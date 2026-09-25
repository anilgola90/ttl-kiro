import { redirect } from "next/navigation";

/**
 * Root index page.
 *
 * The application entry point is the ticket list, so the root path redirects
 * straight to `/tickets`.
 */
export default function Home() {
  redirect("/tickets");
}
