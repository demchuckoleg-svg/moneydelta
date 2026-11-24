# MoneyDelta

100% offline Android bank SMS monthly net delta tracker and Jetpack Glance home screen widget.

## Features
- **Zero `INTERNET` permission** — all SMS parsing and ledger storage stay strictly on-device.
- **Configurable Bank SMS Rules** — custom sender IDs, income/expense/ignore keywords, optional amount regex, and a live **SMS Rule Tester**.
- **Monthly & Custom Billing Cycles** — calendar month (1st–last) or custom start day (`1..28`).
- **Jetpack Glance Home Screen Widget** — displays color-coded Net Delta, Total In, Total Out, period label, and manual refresh button.
- **Real-time + Periodic Sync** — `SMS_RECEIVED` BroadcastReceiver plus 3-hour WorkManager inbox reconciliation.

## Sideloading on Android 13+
After installing the APK, if `READ_SMS` / `RECEIVE_SMS` permissions appear restricted:
**App Info → ⋮ (top right) → Allow restricted settings**, then grant SMS permissions in the app.
