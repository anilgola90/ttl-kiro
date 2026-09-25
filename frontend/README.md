# Support Ticket Management — Frontend

Next.js 15 + React 19 + TypeScript frontend for the AI-powered support ticket management system.

## Prerequisites

- Node.js 18.18+ (or 20+)
- The Spring Boot backend running on **http://localhost:8080**

## Setup

Dependencies are declared in `package.json` but are **not** committed. Install them first:

```bash
npm install
```

## Run (development)

```bash
npm run dev
```

The app starts on http://localhost:3000. All requests to `/api/*` are proxied to the
backend at `http://localhost:8080/api/*` (configured in `next.config.ts`), so make sure the
backend is running before you use the app.

## Other scripts

| Command         | Description                       |
|-----------------|-----------------------------------|
| `npm run build` | Production build                  |
| `npm run start` | Serve the production build        |
| `npm run lint`  | Run Next.js lint                  |

## Structure

```
src/
├── app/
│   ├── layout.tsx      # root layout + QueryClientProvider wrapper
│   ├── providers.tsx   # 'use client' React Query provider
│   └── page.tsx        # redirects to /tickets
└── lib/
    └── types.ts        # TypeScript mirrors of backend DTOs
```

## Notes

- The backend **must** be running on port `8080` for the API proxy to work.
- Enum values (`TicketStatus`, `Priority`) and DTO shapes in `src/lib/types.ts` mirror the
  backend contracts under `com.example.kirotest.dto`.
