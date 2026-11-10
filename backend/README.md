# AidAlert ingest (Java, no framework)

Downloads an official police statistics PDF, extracts the table text with Apache PDFBox 3, turns rows into the app's
statistics JSON and replaces `stats/latest` in Firebase. Plain Java 17 + Maven. No Spring, no Python.

```
mvn package
java -jar target/aidalert-ingest-1.0.jar --pdf https://www.police.gov.bd/.../report.pdf \
     --period 2025-11 --title "Reported cases, November 2025" \
     --columns "Murder,Robbery,Theft" --dry-run
```

1. Run with `--dry-run` first. It prints the JSON and every line it did **not** understand. Compare with the PDF.
2. `--columns` must list the table's numeric columns in the exact order of the PDF. A row is accepted only when it has
   exactly that many numbers, so the totals row and page numbers are skipped, never guessed.
3. Optional `--groups groups.csv` with lines `DMP Dhaka,Dhaka` maps each row to a division.
4. To publish, set `AID_API_KEY` (Web API key from Firebase project settings), `AID_DB_URL`, `AID_EMAIL`, `AID_PASSWORD`
   (the control center account) and run without `--dry-run`.
5. Scheduling: Windows Task Scheduler or cron can run the command monthly. The same PDF is never published twice.

Limits: only https `.gov.bd` addresses are downloaded; only real PDFs under 25 MB; scanned PDFs (no text) are refused
because they need OCR. The police table layout changes between reports, so check the dry run each time.
This tool has only been tested on made-up text here, not on a real police PDF.

## Monthly automatic update (10th of each month)

`--page https://www.police.gov.bd/en/january_2020 --period auto` opens the police statistics page, takes the first PDF
link (the page lists the latest report first), downloads it, builds the statistics and **replaces** `stats/latest`, so
the previous month is removed from the database. `.github/workflows/monthly-crime-stats.yml` runs this on the 10th at
10:00 Bangladesh time; you only add the four secrets and the `CRIME_COLUMNS` variable in GitHub. On a laptop, use cron
(`0 10 10 * * java -jar ...`) or Windows Task Scheduler instead. The sandbox could not open police.gov.bd, so the page
layout and the real column list are still untested: run a `--dry-run` once with the real PDF.
