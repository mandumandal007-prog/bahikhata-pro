# BahiKhata Pro — Khatabook se bhi zyada tools (Material 3)

Native Android app (Kotlin + Jetpack Compose + Material 3). Udhaar khata, cashbook, stock, GST bill + PDF,
reports, calculators, PIN lock, backup/restore, dark mode aur **login + cloud sync**.

## APK download

**Releases** section me sabse naya `bahikhata-pro.apk` download karein → phone me kholein →
"Install unknown apps" allow karein. Android 8.0+ chahiye. Har naya update purane ke upar install hota hai (data safe).

Har push par GitHub Actions (`.github/workflows/build-apk.yml`) signed release APK banata hai aur Release me daal deta hai.

## Features

**Khata / Udhaar** — Customer/Supplier, Diye (You Gave) / Mile (You Got), running balance, payment mode
(Cash/UPI/Bank/Card/Cheque), note, date, due date, opening balance, search + filter, entry edit/delete,
Call, WhatsApp reminder, PDF statement share.

**Cashbook** — Cash In/Out, category, mode, month-wise total, din-wise list, cash in hand.

**Stock** — item, unit, qty, kharid/bikri rate, low-stock alert, +/- aur stock in/out, stock value.

**GST Bill** — multiple items (stock se bhi chun sakte hain), GST % (CGST/SGST), discount, Paid/Baaki,
PDF bill share, WhatsApp text, stock se apne aap ghatana, cashbook/khata me apne aap entry.

**Reports** — Aaj / 7 din / Is mahine / Pichhla mahina / Sab: khata, cashbook, bills, GST, 7 din ka chart,
kharch category-wise, sabse zyada baaki, kam stock, report share.

**Calculators** — GST (jodo/nikalo), Simple + Compound interest (saalana ya rupaya-sainkda mahina), EMI,
Discount (double discount), Profit/Loss + margin, Rate per unit / kaun sasta.

**Extra** — PIN lock, JSON backup/restore/share, Dark/Light/System theme, Material You colours (Android 12+),
Indian number format (₹1,23,456.78), Hinglish labels.

## Login + cloud sync (Firebase)

Login karne par saara data Firestore me `users/{uid}/...` ke andar save hota hai (local-first: pehle phone me,
phir net aate hi cloud me). App delete karke dobara install karein → same email se login → data wapas.

Firebase config build ke time aata hai — **repo me koi key nahi rakhi gayi**:

1. Firebase console → project → *Add app* → Android → package `com.bhaikhata.app` → `google-services.json` download. (App id / installed package: `com.bhaikhata.app`; Kotlin source package stays `com.bahikhata.app`.)
2. Authentication → Sign-in method → **Email/Password** ON.
3. Firestore Database → Create (production mode) → Rules:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid}/{doc=**} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```
4. `google-services.json` ka poora content GitHub repo secret **GOOGLE_SERVICES_JSON** me daalein
   (ya file `app/google-services.json` commit karein) → workflow dobara chalayein.

Bina config ke bhi app poora chalta hai (sirf offline) aur "Cloud login setup pending" dikhata hai.

## Local build

Android Studio (Ladybug+) me folder kholein → Run. Ya `./gradlew assembleRelease` (JDK 17).

## Project structure

```
app/src/main/java/com/bahikhata/app/
  MainActivity.kt
  data/   Models, Db (SQLite), Repo, Cloud (Firebase login + sync)
  util/   money format, PDF, share/WhatsApp
  ui/     Theme, AppRoot (navigation, PIN, login), Components,
          KhataScreens, CashStock, InvoiceScreens, ReportsTools, SettingsScreen, AuthPin
```

## License

MIT


## Design (v1.0.37+)
iOS-style UI: large collapsing titles, translucent tab bar, inset grouped lists, iOS switches, segmented controls, sheets, action sheets and alerts, slide transitions, dark mode. Party photos (stored only on the phone). Settings: text size, font style, accent colour, currency/number format, haptics, hide amounts.
