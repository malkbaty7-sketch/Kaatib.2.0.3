# دليل بناء وتحميل ملف APK لتطبيق "كاتب" عبر GitHub

تم تجهيز التطبيق بالكامل مع مفاتيح التوقيع الرسمية (Release & Debug Keystores) وملفات العمل التلقائي (GitHub Actions Workflows) لبناء ملف **APK موقع وجاهز للتثبيت** مباشرة من GitHub بدون الحاجة لأي إعدادات معقدة.

---

## 1. الملفات والمفاتيح المضافة في المشروع

1. **مفتاح التوقيع الرسمي للإصدار النهائي (Release Keystore):**
   - **ملف المفتاح:** `my-upload-key.jks` (بالإضافة إلى نسخة Base64 في `my-upload-key.jks.base64`)
   - **اسم المفتاح (Key Alias):** `upload`
   - **كلمة مرور المخزن (Store Password):** `katib2026`
   - **كلمة مرور المفتاح (Key Password):** `katib2026`
   - **بصمة الشهادة (SHA-1):** `5F:CD:A4:46:36:03:01:55:81:2E:A4:68:FF:CB:93:A3:E1:73:86:B0`
   - **بصمة الشهادة (SHA-256):** `3B:8F:22:05:DD:52:4F:FA:13:DD:23:7E:96:12:46:E2:21:3D:D4:5E:C8:68:71:55:92:33:DF:AE:BA:25:3B:3F`

2. **مفتاح التوقيع التجريبي (Debug Keystore):**
   - محفوظ في `debug.keystore.base64` ويتم فك تشفيره تلقائياً أثناء البناء على GitHub.

3. **ملفات GitHub Actions المحدثة (متوافقة مع JDK 21 و Gradle 9.3.1 و AGP 9.1.1):**
   - `.github/workflows/build_apk.yml`: يبني نسخة **Release APK موقعة** (`katib-release-signed-apk`).
   - `.github/workflows/build_debug_apk.yml`: يبني نسخة **Debug APK** (`katib-debug-apk`).

---

## 2. خطوات رفع المشروع وتحميل ملف الـ APK من GitHub

1. **رفع التحديثات إلى GitHub:**
   - من قائمة الإعدادات في AI Studio، اضغط على **Push to GitHub** أو قم بتصدير المشروع كملف ZIP ورفعه إلى مستودعك `Katib-2.0.2`.
2. **تشغيل عملية البناء (GitHub Actions):**
   - بمجرد دفع الكود إلى الفرع `main` أو `master`، سيبدأ البناء تلقائياً.
   - يمكنك أيضاً الدخول إلى تبويب **Actions** في مستودعك على GitHub، واختيار **Build Signed Android APK** ثم الضغط على **Run workflow**.
3. **تحميل ملف الـ APK:**
   - بعد انتهاء العملية بنجاح (علامة صح خضراء ✅)، افتح تفاصيل عملية البناء (Workflow Run).
   - انزل إلى قسم **Artifacts** في أسفل الصفحة.
   - اضغط على **`katib-release-signed-apk`** لتحميل ملف الـ APK الموقع (`katib-release-signed.apk`) وتثبيته مباشرة على هاتفك.

---

## 3. (اختياري) إضافة مفتاح Gemini API في GitHub Secrets

يعمل البناء تلقائياً بدون أي Secrets إضافية، ولكن إذا أردت تضمين مفتاح `GEMINI_API_KEY` داخل نسخة الـ APK المبنية من GitHub:
1. اذهب إلى مستودعك في GitHub -> **Settings** -> **Secrets and variables** -> **Actions**.
2. اضغط **New repository secret**.
3. أضف الاسم `GEMINI_API_KEY` وضع قيمة مفتاحك.
