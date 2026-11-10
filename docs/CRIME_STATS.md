# Crime statistics screen

The app draws division/city bar charts from `app/src/main/assets/crime_stats.json`.
The file ships with one report transcribed from the police table (see its `note` field); no figures are invented. A newer report published to `stats/latest` replaces it, and the screen shows an honest empty state if neither exists.

## Format

```json
{
  "title": "Reported cases, January 2024",
  "period": "2024-01",
  "source": "Bangladesh Police monthly crime statistics",
  "sourceUrl": "https://www.police.gov.bd/en/january_2020",
  "crimeTypes": ["Murder", "Robbery", "Theft"],
  "regions": [
    {"name": "DMP Dhaka", "group": "Dhaka", "counts": [12, 20, 150]},
    {"name": "CMP Chattogram", "group": "Chattogram", "counts": [7, 9, 90]}
  ]
}
```

`counts` follows the order of `crimeTypes`. `group` is the division; the screen can filter by it.

## Getting the numbers from the police PDFs

1. Download the monthly PDF(s) from https://www.police.gov.bd (Statistics > Crime statistics) in your browser.
2. Extract text: `pdftotext -layout report.pdf report.txt` (works on text PDFs; scanned PDFs need OCR).
3. Copy the table rows into the JSON above. Check the totals against the PDF.
4. Rebuild the app, or paste the JSON in via a copy in app assets.

Tables in these PDFs differ month to month, so check each file by eye. Do not mix periods in one chart.

## Publishing from the control center

Open Crime statistics while logged in as the control center and tap **Publish new statistics**. Paste the JSON. It replaces the report in the cloud (`stats/latest`), so only the latest report is kept, and every phone fetches it and keeps a copy for offline use.
