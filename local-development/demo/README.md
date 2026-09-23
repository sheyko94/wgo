# World map demo data

With the local backend and its dependencies running, use Node.js 24:

```sh
node local-development/demo/seed-world-events.mjs
```

This adds 80 fictional reports through `POST /v1/observations` and waits for the
normal consumer to create events and index them. Existing data is preserved.
Click **Refresh events** in the UI afterwards.

- 50 reports: five close points in each of Amsterdam, London, New York,
  San Francisco, São Paulo, Cape Town, Nairobi, Mumbai, Tokyo, and Sydney.
- 30 reports: isolated locations across the Americas, Europe, Africa, Asia,
  and the Pacific.

Titles start with `[Map demo NNN]` and explicitly say fictional. Nearby reports
are dated three days apart in August 2026 so the default 24-hour matching window
keeps them as separate events. Custom matching settings can change the result.

The script skips reports already returned by the events API and saves accepted
observation IDs under ignored `local-development/data/world-demo-receipts.json`
so rerunning while work is pending does not resubmit accepted reports. If you
reset the database, remove that receipt file before seeding again. A network
failure after the server accepts a request but before saving its receipt remains
ambiguous; check the API before rerunning in that case.
