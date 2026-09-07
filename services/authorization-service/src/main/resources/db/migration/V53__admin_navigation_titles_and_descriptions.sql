-- Publish a new immutable ADMIN artifact. V48/0.2.0 remains available for rollback.
WITH admin_panel AS (
  SELECT p.id AS panel_id,p.active_artifact_id
  FROM panel p WHERE p.code='ADMIN'
), source AS (
  SELECT a.* FROM ui_module_artifact a JOIN admin_panel p ON p.active_artifact_id=a.id
), menu_data AS (
  SELECT jsonb_build_array(
    jsonb_build_object('id','operator-guide-menu','routeId','operator-guide','title','راهنمای راهبری','description','آموزش فیلدها، قواعد و سناریوهای کار با پنل مدیریت','icon','book','order',10),
    jsonb_build_object('id','ou-access-ous-menu','routeId','ou-access-ous','title','ساختار سازمانی','description','مشاهده OUهای همگام‌شده از Directory و اعضای سازمان','icon','apartment','order',20),
    jsonb_build_object('id','ou-access-groups-menu','routeId','ou-access-groups','title','گروه‌های دسترسی','description','ساخت گروه محاسباتی با قواعد EXACT یا SUBTREE روی OUها','icon','team','order',21),
    jsonb_build_object('id','ou-access-applications-menu','routeId','ou-access-applications','title','دسترسی برنامه‌ها','description','اعطای دسترسی مشاهده Microfrontend به گروه‌های سازمانی','icon','appstore','order',22),
    jsonb_build_object('id','ou-access-explain-menu','routeId','ou-access-explain','title','تحلیل دسترسی کاربر','description','ردیابی مسیر کاربر، OU، گروه و برنامه برای توضیح تصمیم دسترسی','icon','audit','order',23),
    jsonb_build_object('id','access-studio-menu','routeId','access-studio','title','استودیوی مجوزها','description','مدیریت درخت منابع، عملیات و Grantهای OpenFGA','icon','safety','order',30),
    jsonb_build_object('id','panels-menu','routeId','panels','title','مدیریت میکروفرانت‌ها','description','ثبت Panel، انتشار Artifact و مدیریت Manifest و Navigation','icon','appstore','order',40),
    jsonb_build_object('id','proxy-targets-menu','routeId','proxy-targets','title','مقصدهای سرویس','description','تعریف Gateway، مسیر پایه، محدودیت پاسخ و Health Check','icon','api','order',50),
    jsonb_build_object('id','proxy-routes-menu','routeId','proxy-routes','title','مسیرهای پروکسی','description','اتصال namespace ورودی Microfrontend به مقصد سرویس','icon','branches','order',51),
    jsonb_build_object('id','proxy-operations-menu','routeId','proxy-operations','title','عملیات مسیرها','description','تعریف Method، Path و Resource/Action موردنیاز هر API','icon','control','order',52),
    jsonb_build_object('id','outbound-connections-menu','routeId','outbound-connections','title','اتصال‌های خروجی','description','ثبت Originهای مجاز برای ارتباط امن با سرویس‌های بیرونی و Legacy','icon','link','order',60),
    jsonb_build_object('id','outbound-auth-menu','routeId','outbound-auth','title','احراز هویت سرویس‌ها','description','تعریف روش ارسال یا دریافت توکن بدون ذخیره مقدار Secret','icon','key','order',70),
    jsonb_build_object('id','integration-test-menu','routeId','integration-test','title','آزمایش اتصال','description','اجرای تست امن End-to-End اتصال، توکن و پاسخ سرویس مقصد','icon','experiment','order',80),
    jsonb_build_object('id','superset-instances-menu','routeId','superset-instances','title','محیط‌های گزارش‌گیری','description','مدیریت Instanceهای Public و Operation در Apache Superset','icon','cloud-server','order',90),
    jsonb_build_object('id','identity-menu','routeId','identity','title','هویت‌ها و نقش‌ها','description','مشاهده گروه‌های همگام، ساخت نقش و تخصیص آن به کاربران','icon','idcard','order',100),
    jsonb_build_object('id','logs-api-menu','routeId','logs-api','title','گزارش درخواست‌های API','description','جست‌وجوی درخواست‌ها، خطاها، زمان پاسخ و Correlation ID','icon','file-search','order',110),
    jsonb_build_object('id','logs-audit-menu','routeId','logs-audit','title','گزارش رویدادهای راهبری','description','مشاهده تغییرات مدیریتی، عامل، هدف و نتیجه هر عملیات','icon','audit','order',111),
    jsonb_build_object('id','superset-menu','routeId','superset','title','دسترسی گزارش‌ها','description','تخصیص سطح دسترسی داشبوردها و گزارش‌های Superset','icon','dashboard','order',120)
  ) AS menus
)
INSERT INTO ui_module_artifact(id,panel_id,artifact_version,remote_entry_url,remote_name,
  exposed_module,contract_version,schema_version,integrity,manifest_snapshot,
  validation_status,created_by)
SELECT gen_random_uuid(),s.panel_id,'0.3.0',s.remote_entry_url,s.remote_name,s.exposed_module,
  s.contract_version,s.schema_version,s.integrity,jsonb_set(s.manifest_snapshot,'{menus}',m.menus),
  'VALID','migration-v53'
FROM source s CROSS JOIN menu_data m
ON CONFLICT(panel_id,artifact_version) DO NOTHING;

UPDATE panel p SET active_artifact_id=a.id,semantic_version=a.artifact_version,
  version=p.version+1,updated_at=now()
FROM ui_module_artifact a
WHERE p.code='ADMIN' AND a.panel_id=p.id AND a.artifact_version='0.3.0';

INSERT INTO schema_version(component,version) VALUES('admin-navigation-tooltips','1')
ON CONFLICT(component) DO UPDATE SET version=excluded.version,updated_at=now();
