# رسالتي (Resalaty) — ملخص التعديلات ودليل التشغيل

يشرح هذا الملف كل ما نُفّذ، وما يلزمك في **Android Studio** وفي **خادم PHP (api.php)** لإكمال الميزتين الجديدتين (فحص النسخة + الإعلانات).

## ✅ ملخص ما تم (المطالب العشرة + إضافات)
1. **تسجيل الدخول أولاً:** `MainActivity` أصبحت شاشة الإطلاق (LAUNCHER) بدل `ProfileActivity` — لن يدخل التطبيق دون Credentials. (AndroidManifest.xml, MainActivity.kt)
2. **فحص النسخة وإجبار التحديث:** عند الفتح يتصل بـ `check_version`؛ إذا كان إصدار التطبيق أقدم وforce_update=true يظهر حوار إلزامي يفتح رابط التحديث. (MainActivity.kt, RetrofitClient.kt) — يحتاج endpoint بالخادم (أدناه).
3. **الرئيسية = إعلانات:** بطاقات فيها حاوية صورة + وصف توضيحي بالأسفل، الضغط يفتح صفحة المقال. (DailyReportActivity.kt, AnnouncementDetailActivity.kt, item_announcement.xml) — يحتاج endpoint (أدناه).
   - **إزالة Profile:** حُذفت من المانيفست والتنقل، ووظائفها انتقلت للإعدادات.
4. **النوتش وأبعاد الشاشة:** Edge-to-edge مع WindowInsets يراعي شريط الحالة/النوتش (شاومي) والحواف السفلية. (BaseActivity.kt, themes.xml)
5. **السايدبار من اليمين + Light/Night بداخله:** الدرج يفتح من نفس جهة الأيقونة، ومبدّل الوضع الليلي في رأسه. (activity_base.xml, nav_header.xml, BaseActivity.kt)
6. **منع تكدّس الصفحات + أنيميشن:** الوجهات الرئيسية بنسخة واحدة (REORDER_TO_FRONT/SINGLE_TOP) مع أنيميشن انزلاق/تلاشٍ. (BaseActivity.kt, res/anim/*)
7. **RTL ثابت:** على مستوى التطبيق كله عبر ResalatyApp + ThemeManager.
8. **لا تكرار بين السايدبار والشريط السفلي:** أعيد تنظيم القائمتين.
9. **الرسائل والإشعارات في السايدبار:** كانت غير موصولة في معالج القائمة، تم ربطها بـ MessagesActivity وNotificationsActivity.
10. **الإعدادات:** 5 ثيمات + Light/Dark + معلومات الطالب + حالة الحساب (طالب/معلم) + التواصل + من نحن + فريق التطوير. (SettingsActivity.kt)
- **خلفية النقاط 45°:** نمط Dots-grid في app_background.xml/dot_grid.xml.

## 🧭 التنقل الجديد
- **الشريط السفلي:** الرئيسية · الجدول · المواد · التقارير.
- **سايدبار طالب:** ساعات دراستي · محاضرات أونلاين · الاختبارات الذكية · التقييمات · كشف الحساب · الرسائل · الإشعارات · الإعدادات · خروج.
- **سايدبار معلم:** الحسابات المالية · مدفوعات الطلاب · محاضرات أونلاين · الرسائل · الإشعارات · الإعدادات · خروج.

## 🎨 الثيمات
النيلي (افتراضي) · الأخضر الزمردي · البنفسجي الملكي · الأزرق المحيطي · العنابي الفاخر + الوضع الليلي. يُطبَّق على شريط الأدوات/الحالة/رأس السايدبار/التبويب النشط.
> لانتشار اللون لكل عنصر داخلي، استبدل `@color/indigo` بـ `?attr/colorPrimary` في تلك التخطيطات.

## 🛠️ Android Studio
1. Sync Gradle.
2. **مهم:** ارفع `versionCode` في `app/build.gradle.kts` مع كل إصدار (التطبيق يقارنه بقيمة الخادم لإجبار التحديث).
3. Clean ثم Rebuild ثم تشغيل (يفضّل جهاز فيه Notch).
- عدّل روابط التواصل في `SettingsActivity.buildContacts()` (واتساب/فيسبوك/الموقع) ونصوص strings: `about_us_text`, `dev_team_text`.

## 🌐 PHP المطلوب في api.php

### check_version
```php
case 'check_version':
    $latest_version_code = 2;        // أحدث versionCode منشور
    $latest_version_name = "1.1";
    $force_update        = true;     // true = إجبار
    $update_url = "https://play.google.com/store/apps/details?id=com.example.studntapp";
    echo json_encode([
      "status"=>"success",
      "latest_version_code"=>$latest_version_code,
      "latest_version_name"=>$latest_version_name,
      "force_update"=>$force_update,
      "update_url"=>$update_url,
      "message"=>"يتوفّر إصدار جديد، يرجى التحديث."
    ]);
    break;
```

### get_announcements
```sql
CREATE TABLE announcements (
  id INT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  summary VARCHAR(500) NULL,    -- الوصف تحت الصورة
  image VARCHAR(255) NULL,      -- مسار/رابط الصورة
  content TEXT NULL,            -- نص المقال
  article_url VARCHAR(255) NULL,-- رابط خارجي اختياري
  tag VARCHAR(50) DEFAULT 'إعلان',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
```php
case 'get_announcements':
    $rows=[];
    $res=$conn->query("SELECT id,title,summary,image,content,article_url,tag,
        DATE_FORMAT(created_at,'%Y-%m-%d') AS created_at
        FROM announcements ORDER BY created_at DESC LIMIT 30");
    while($r=$res->fetch_assoc()){ $r['id']=(int)$r['id']; $rows[]=$r; }
    echo json_encode(["status"=>"success","data"=>$rows]);
    break;
```
> image نسبي (uploads/x.jpg) يضيف له التطبيق BASE_URL تلقائياً، أو رابط كامل http يُستخدم كما هو. إن وُجد content يُعرض داخل التطبيق، وإلا يُفتح article_url خارجياً.

## 🔎 تحقق
- فُحصت كل مراجع الموارد برمجياً ولا يوجد مفقود في كود التطبيق.
- لم تتح هذه البيئة لتشغيل Gradle؛ يرجى Rebuild في Android Studio لاعتماد R class النهائي.

---

## تحديث الإشعارات: التحويل إلى Firebase Cloud Messaging (FCM)

تم استبدال الخدمة الأمامية الدائمة (Soketi/Pusher) — التي كانت تُبقي «إشعاراً ثابتاً» وتستنزف البطارية — بنظام **FCM** يسلّم الإشعارات فورياً عبر Google بلا اتصال دائم وبلا إشعار ثابت.

### تغييرات التطبيق (تمّت)
- `FcmService.kt`: استقبال الإشعارات وعرضها وتوجيهها (رسائل/تنبيهات).
- إلغاء الخدمة الأمامية `NotificationService` (أصبحت فارغة) وإزالة صلاحيات `FOREGROUND_SERVICE` و`WAKE_LOCK`.
- تسجيل رمز الجهاز تلقائياً بعد الدخول عبر `register_fcm_token`.
- إضافة مكتبة Firebase ومُلحق `google-services` في ملفات Gradle.

### مطلوب منك قبل البناء (إلزامي)
1. أنشئ مشروعاً في https://console.firebase.google.com ثم أضف تطبيق Android باسم الحزمة: `com.example.studntapp`.
2. حمّل ملف **google-services.json** وضعه داخل مجلّد `app/` (بجانب build.gradle.kts). **بدون هذا الملف لن يكتمل البناء.**
3. من إعدادات المشروع في Firebase فعّل Cloud Messaging واحتفظ ببيانات حساب الخدمة (Service Account) لإرسال الإشعارات من السيرفر.

### تغييرات السيرفر (PHP) المطلوبة
أضف جدولاً لحفظ الرموز:
```sql
CREATE TABLE fcm_tokens (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  token VARCHAR(255) NOT NULL,
  platform VARCHAR(20) DEFAULT 'android',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_token (token)
);
```
أضف معالج الإجراء في `api.php`:
```php
case 'register_fcm_token':
    $uid = intval($_POST['user_id'] ?? 0);
    $tok = trim($_POST['token'] ?? '');
    if ($uid && $tok) {
        $stmt = $pdo->prepare(
          "INSERT INTO fcm_tokens (user_id, token, platform) VALUES (?, ?, 'android')
           ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), updated_at = NOW()");
        $stmt->execute([$uid, $tok]);
        echo json_encode(['status' => 'success']);
    } else {
        echo json_encode(['status' => 'error', 'message' => 'بيانات ناقصة']);
    }
    break;
```
وعند إرسال رسالة/تنبيه، أرسل دفعة FCM إلى رموز المستخدم عبر HTTP v1 API
(تحتاج مكتبة google/apiclient وملف حساب الخدمة). مثال مبسّط لإرسال إلى رمز واحد:
```php
function send_fcm($accessToken, $projectId, $deviceToken, $title, $body, $type) {
  $url = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send";
  $payload = [ 'message' => [
      'token' => $deviceToken,
      'notification' => [ 'title' => $title, 'body' => $body ],
      'data' => [ 'type' => $type, 'title' => $title, 'body' => $body ]
  ]];
  $ch = curl_init($url);
  curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [ "Authorization: Bearer $accessToken", "Content-Type: application/json" ],
    CURLOPT_POSTFIELDS => json_encode($payload),
    CURLOPT_RETURNTRANSFER => true,
  ]);
  $res = curl_exec($ch); curl_close($ch); return $res;
}
```
ملاحظة: مفتاح `type` يجب أن يكون `"message"` للرسائل أو `"notification"` للتنبيهات
ليوجّه التطبيق المستخدم للشاشة الصحيحة.
