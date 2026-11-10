# Control Center reorganisation
- CenterHomeActivity: dashboard (counters, "needs attention now", unanswered-SOS banner) + 5 sections.
- LiveMapActivity / IncidentDetailActivity: real OpenStreetMap (TileMapView), latest tracked position, trails, old-fix marker.
- New: InboxActivity (citizen chats, awaiting-reply first), IncidentsActivity, AnalyticsActivity (+ BarChartView).
- Community board is now one tap from the dashboard.
- database.rules.json: added ".read" on /msgs (needed for the inbox). PUBLISH THE RULES again in Firebase.
