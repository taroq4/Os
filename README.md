# 🛡️ BlockAds - حاجب إعلانات TikTok / Snapchat / Instagram

## كيفية البناء والتثبيت

### الطريقة الأولى: Android Studio (الأسهل)
1. افتح Android Studio
2. اختر `File → Open` وافتح مجلد `BlockAds`
3. انتظر مزامنة Gradle
4. اضغط `▶ Run` أو `Build → Build APK(s)`
5. ستجد الـ APK في: `app/build/outputs/apk/debug/`

### الطريقة الثانية: GitHub Actions (تلقائية)
1. أنشئ repository جديد على GitHub
2. ارفع هذه الملفات بالكامل
3. اذهب إلى `Actions` → ستجد workflow جاهز
4. اضغط `Run workflow` أو ادفع أي تغيير
5. حمّل الـ APK من `Artifacts`

### الطريقة الثالثة: Command Line
```bash
cd BlockAds
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## كيف يعمل التطبيق

```
عند الضغط على "تفعيل الحماية":
┌─────────────────────────────────────────────────┐
│  1. إنشاء نفق VPN محلي (لا بيانات تغادر جهازك) │
│  2. اعتراض استعلامات DNS                        │
│  3. حجب 300+ نطاق إعلاني                        │
│  4. إسقاط حزم QUIC/HTTP3 (إعلانات TikTok)       │
│  5. فلترة JSON responses لـ TikTok + Instagram  │
└─────────────────────────────────────────────────┘
```

## نسبة الفعالية المتوقعة
| التطبيق | الفعالية |
|---------|---------|
| TikTok | ~85-95% |
| Instagram | ~80-90% |
| Snapchat | ~70-80% |

## الأذونات المطلوبة
- `INTERNET` - للاتصال بالشبكة
- `FOREGROUND_SERVICE` - للتشغيل المستمر
- `VPN` - يُطلب عند أول تشغيل

