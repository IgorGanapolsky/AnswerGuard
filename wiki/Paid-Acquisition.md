# Paid Acquisition

<!-- markdownlint-disable MD013 -->

Campaign configuration and live paid performance for AnswerGuard store growth.

> Auto-updated by `scripts/wiki_sync.py` from `marketing/data/*.json`.

## Live Paid Snapshot

<!-- LIVE_PAID_START -->
| Metric | Value |
|--------|-------|
| Snapshot (UTC) | `2026-05-05T22:41:05+00:00` |
| Paid Attributed Users (30d) | — |
| Paid Events (30d) | 0 |
| Active Campaign Count (tracked) | — |
| Daily Budget Configured | $0.00 |
| Blended CPI Target | $3.00 |
| Open -> Completed Rate (30d) | — |
| WQTU (7d) | — |
| WQTU Checkpoint Target (2026-03-31) | — |
| WQTU Quarter Target (2026-06-30) | — |
| Downloads (30d) | — |
| Apple Ads Campaigns (API) | 0 |
| Apple Ads Active Campaigns (API) | 0 |
| Apple Ads Impressions (30d) | 0 |
| Apple Ads Clicks/Taps (30d) | 0 |
| Apple Ads Spend (30d) | $0.00 |
| Apple Ads Installs (30d) | 0 |
| Apple Ads Live Finding | No live Apple Ads check available |
| Guardrail Violated | NO |
<!-- LIVE_PAID_END -->

## Paid Attribution Sources

<!-- LIVE_PAID_SOURCES_START -->
| Source | Events (30d) | Users (30d) |
|--------|:------------:|:-----------:|
| (none) | 0 | 0 |
<!-- LIVE_PAID_SOURCES_END -->

## Paid Charts

<!-- LIVE_PAID_CHARTS_START -->
_No paid chart data available yet._
<!-- LIVE_PAID_CHARTS_END -->

## Budget Allocation

<!-- LIVE_PAID_BUDGET_START -->
_No paid campaign configuration available._
<!-- LIVE_PAID_BUDGET_END -->

## Apple Search Ads

Focus on high-intent searches around spam calls, scam call protection, robocall
blocking, unknown caller handling, and caller ID safety.

## Google App Campaigns

Optimize toward installs first, then shift toward activated users once Firebase
events confirm the onboarding funnel is healthy.

## Campaign Status

<!-- LIVE_CAMPAIGN_STATUS_START -->
| Platform | Config Status | Live Status | Daily Budget |
|----------|---------------|-------------|-------------:|
| (none) | — | — | $0.00 |
<!-- LIVE_CAMPAIGN_STATUS_END -->

## Source Files

- `scripts/north_star_guardrail.py` - North Star and paid attribution snapshot
- `scripts/store_downloads_snapshot.py` - Store/download snapshot
- `scripts/apple_ads_live_metrics.py` - Apple Ads live metrics snapshot
- `.github/workflows/wiki-sync.yml` - Scheduled wiki publication
