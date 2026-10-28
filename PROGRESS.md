
## v1.0.42 Round 4 (1 AM) — usability & speed
- Fixed: Reports Baaki uses balanceDue; quotation no. = max+1; Settings switch bill_qr.
- Added Speed.kt: QuickEntrySheet, BulkReminderSheet, TipsCarousel, SwipeActionsRow, EmptyHint.
- Khata home: balance filter pills + sort sheet + count/total line, search by amount, bulk reminder row, ⚡ FAB, saved toast, animateItem.
- BUILD1 green 1:39. Added haptic+animateItem+empty hint on party screen; rebuilding.
- BUILD2 green 1:41. PUSHING commit42 — check commits before re-pushing!
- PUSHED 9219449 at 1:42. Do NOT push again. NEXT: wait CI, download, verify, changelog.
- CHANGELOG Round 4 appended 1:42. NEXT: CI poll only.
- ALL DONE 1:53: CI 42 green, release v1.0.42, APK verified (com.bhaikhata.app, 42, same cert as v41).

## v1.0.43 Round 5 (3 AM) — final polish
- 3:36 edits: safe amount input (cleanAmountInput, toAmount rejects inf/neg), chart guard n==0, 'Sab hisaab saaf 🎉' empty states for Lena/Dena filters, 'App share karein' row in Settings. BUILD8 started.
- 3:39 BUILD8 green (VERSION_CODE=43). PUSHING commit43 — check commits before re-pushing!
- PUSHED 06dfac1 at 3:40. Do NOT push again. NEXT: CI poll, download, verify, changelog.
- ALL DONE 3:51: CI 43 green, release v1.0.43, APK verified (com.bhaikhata.app, 43, same cert as v42), copied to latest. Changelog Round 5 appended.

## v1.0.44 (8:20 AM) — direct SMS
- 8:56 edits: util/Sms.kt (SmsSender w/ sent-receipt, ReminderLog in settings, SmsTexts, EntrySms), util/AutoRemind.kt (WorkManager, off by default), ui/SmsUi.kt (rememberSmsSender, whatsappStatement, new BulkReminderSheet w/ SMS auto mode), party SMS tile, swipe menu, overdue SMS btn, bill SMS, Settings SMS section + SIM picker, manifest SEND_SMS/READ_PHONE_STATE. BUILD9 started.
- BUILD9 green 8:57. PUSHED 3bcb0f3 at 8:58. Do NOT push again. NEXT: CI poll, download, verify, changelog.
- ALL DONE 9:14: CI 44 green, release v1.0.44, APK verified (com.bhaikhata.app, 44, same cert as v43), copied to latest. Changelog appended.

## v1.0.45 (9:36 AM) — app lock upgrade + 14 business tools
- 9:50 edits: AuthPin (androidx.biometric, 5-try lockout), Settings app-lock rows, util/Pro.kt (Trash file, Calc, CSV, DailySummary worker, VoiceParse, VisitingCard, QR), ui/ProScreens.kt (hub + 13 screens), NumField calculator, Repo trash hooks + rowsJson/restoreRows/dueEntries. No schema change. BUILD10 started.
- BUILD10 green 9:47 (twice). PUSHING commit45 at 9:48 — check commits before re-pushing!
