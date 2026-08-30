# راهنمای انتشار Demo روی سرور Linux

این Runbook برای بالا آوردن همه اجزای Aurevia روی **یک سرور Linux و برای نمایش/آزمایش محدود** است. خروجی این روش Production نیست. Keycloak در حالت توسعه، دیتابیس‌ها تک‌نمونه، Gateway نمونه، و شبکه/گواهی‌ها مخصوص Demo هستند.

## خروجی مورد انتظار

پس از پایان کار این اجزا اجرا می‌شوند:

- Shell و چهار MFE شامل Admin، HR، Finance و Reports؛
- Java BFF و Authorization Service؛
- Keycloak و PostgreSQL آن؛
- OpenFGA کامل نسخه pin‌شده و PostgreSQL آن؛
- Redis برای session، Token Vault و cache؛
- HR و Finance آزمایشی و Operation Gateway نمونه؛
- Public Superset assets و Operation Superset با گزارش‌های نمونه.

مسیر ساده و امن برای مشاهده Demo از رایانه ادمین، SSH tunnel است. با این روش URLهای `localhost` موجود در Compose و Realm بدون تغییر کار می‌کنند و پورت‌های Demo را روی اینترنت باز نمی‌کنیم.

## ۱. پیش‌نیاز سرور

پیشنهاد حداقل:

```text
Ubuntu Server 24.04 LTS
8 vCPU
16 GB RAM
100 GB SSD
```

نصب ابزارهای پایه:

```bash
sudo apt update
sudo apt install -y ca-certificates curl git jq openssl
```

Docker Engine و Docker Compose Plugin را از repository رسمی Docker نصب کنید، سپس:

```bash
docker version
docker compose version
```

یک کاربر deployment بسازید:

```bash
sudo adduser aurevia
sudo usermod -aG docker aurevia
sudo install -d -o aurevia -g aurevia /opt/aurevia
sudo -iu aurevia
```

برای اعمال عضویت گروه Docker یک login جدید لازم است.

## ۲. دریافت نسخه مشخص پروژه

```bash
cd /opt/aurevia
git clone <REPOSITORY_URL> aurevia-super-app
cd aurevia-super-app
git checkout <APPROVED_TAG_OR_COMMIT>
```

برای Demo قابل تکرار نیز از tag یا commit مشخص استفاده کنید، نه HEAD متغیر یک branch.

## ۳. Build فرانت‌اند

Dockerfileهای Java در Compose build می‌شوند، اما Nginx فایل‌های `dist` فرانت را از host mount می‌کند. بنابراین قبل از `compose up` باید همه workspaceها build شوند.

Node.js 22.x و npm 10.9.2 را نصب و بررسی کنید:

```bash
node --version
npm --version
npm ci
npm run typecheck
npm test
npm run build
```

این مسیرها باید موجود باشند:

```bash
test -f apps/shell/dist/index.html
test -f apps/mfe-admin/dist/remoteEntry.js
test -f apps/mfe-hr/dist/remoteEntry.js
test -f apps/mfe-finance/dist/remoteEntry.js
test -f apps/mfe-reports/dist/remoteEntry.js
```

برای اجرای تست Java روی host به JDK 21 نیاز است:

```bash
chmod +x mvnw
./mvnw verify
```

## ۴. ساخت تنظیمات و Secretهای Demo

```bash
cp .env.example .env
chmod 600 .env
```

مقادیر تصادفی بسازید:

```bash
openssl rand -base64 32
openssl rand -base64 36
```

همه `change-me`ها را عوض کنید. نمونه ساختار:

```env
POSTGRES_AUTH_PASSWORD=<RANDOM>
OPENFGA_DB_PASSWORD=<RANDOM>
REDIS_PASSWORD=<RANDOM>

TOKEN_VAULT_KEY_ID=demo-v1
TOKEN_VAULT_KEY_BASE64=<32_BYTE_BASE64_KEY>

KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<RANDOM>
KEYCLOAK_DB_PASSWORD=<RANDOM>
OIDC_CLIENT_ID=aurevia-bff
OIDC_CLIENT_SECRET=<MATCH_REALM_CLIENT_SECRET>

OPENFGA_STORE_ID=<CREATED_IN_STEP_6>
OPENFGA_MODEL_ID=<CREATED_IN_STEP_6>

AUTH_INTERNAL_USER=bff
AUTH_INTERNAL_PASSWORD=<RANDOM>

OPERATION_SUPERSET_SECRET_KEY=<RANDOM>
OPERATION_SUPERSET_DB_PASSWORD=<RANDOM>
OPERATION_SUPERSET_ADMIN_PASSWORD=<RANDOM>
SUPERSET_LOAD_EXAMPLES=yes
SUPERSET_REMOTE_USER_ROLE=Gamma
```

نکته: `OIDC_CLIENT_SECRET` باید دقیقاً با secret کلاینت `aurevia-bff` در `infra/keycloak/realm-aurevia.json` یا Keycloak Admin Console هماهنگ باشد. تغییر فقط یک سمت باعث `Invalid credentials` می‌شود.

## ۵. محدودکردن دسترسی شبکه

در Demo راه دور، پورت‌ها را در firewall عمومی باز نکنید. فقط SSH مجاز باشد:

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw enable
```

Compose، OpenFGA و Keycloak را روی loopback منتشر می‌کند؛ پورت‌های Shell/MFE در Compose فعلی host binding عمومی دارند، اما firewall بالا دسترسی اینترنتی را می‌بندد.

## ۶. Bootstrap کردن OpenFGA

ابتدا فقط دیتابیس، migration و OpenFGA را اجرا کنید:

```bash
docker compose --env-file .env \
  -f infra/docker-compose/compose.yml \
  up -d openfga-db openfga-migrate openfga
```

سلامت endpoint محلی را بررسی کنید:

```bash
curl -fsS http://127.0.0.1:8080/healthz
```

با نسخه pin‌شده OpenFGA CLI یک Store به نام `aurevia-demo` بسازید و مدل canonical را منتشر کنید:

```bash
export FGA_API_URL=http://127.0.0.1:8080
fga store create --name aurevia-demo
fga model write --store-id <STORE_ID> --file infra/openfga/model.fga
```

دو شناسه خروجی را در `.env` قرار دهید:

```env
OPENFGA_STORE_ID=<STORE_ID>
OPENFGA_MODEL_ID=<MODEL_ID>
```

مدل را پیش از انتشار با تست‌های موجود بررسی کنید:

```bash
fga model test --tests infra/openfga/model-tests.yaml
```

اگر syntax نسخه CLI متفاوت بود، `fga store create --help` و `fga model write --help` همان نسخه pin‌شده را مبنا قرار دهید. Store یا Model فرضی موجود در `.env.example` قابل استفاده نیست.

## ۷. اجرای کل Stack

```bash
docker compose --env-file .env --profile superset \
  -f infra/docker-compose/compose.yml \
  up -d --build
```

Superset init در اجرای اول migration، ساخت admin و در صورت خالی بودن دیتابیس، بارگذاری مثال‌ها را انجام می‌دهد. این مرحله ممکن است چند دقیقه طول بکشد.

وضعیت:

```bash
docker compose --env-file .env --profile superset \
  -f infra/docker-compose/compose.yml ps
```

لاگ کلی:

```bash
docker compose --env-file .env --profile superset \
  -f infra/docker-compose/compose.yml logs --tail=200
```

سرویس‌های اصلی باید `healthy` یا `running` باشند و init containerهای موفق می‌توانند `exited (0)` باشند.

## ۸. اتصال مرورگر با SSH Tunnel

روی رایانه شخصی ادمین، نه روی سرور، اجرا کنید:

```bash
ssh -N \
  -L 8443:127.0.0.1:8443 \
  -L 8180:127.0.0.1:8180 \
  -L 3001:127.0.0.1:3001 \
  -L 3002:127.0.0.1:3002 \
  -L 3003:127.0.0.1:3003 \
  -L 3004:127.0.0.1:3004 \
  aurevia@<SERVER_IP>
```

سپس باز کنید:

```text
http://localhost:8443
```

این روش عمداً نام `localhost` را حفظ می‌کند؛ بنابراین OIDC authorization URI، CSP محلی و Remote Entryهای bootstrap با Compose فعلی سازگار می‌مانند.

## ۹. Smoke Test

```text
[ ] Login به Keycloak و بازگشت به Shell موفق است.
[ ] GET /api/v1/me/manifest برابر 200 است.
[ ] پنل‌های Admin، HR، Finance و Reports مطابق grant دیده می‌شوند.
[ ] remoteEntry.js هر چهار MFE برابر 200 و JavaScript است.
[ ] درخواست HR/Finance از BFF و Gateway عبور می‌کند.
[ ] OpenFGA برای کاربر مجاز ALLOW و کاربر غیرمجاز DENY می‌دهد.
[ ] Superset dashboard نمونه باز می‌شود و login دوم نمی‌خواهد.
[ ] Dashboard بدون grant برابر 403 است.
[ ] access/refresh token در localStorage، URL یا log دیده نمی‌شود.
```

دستورهای سریع بررسی:

```bash
curl -I http://127.0.0.1:8443/
curl -I http://127.0.0.1:3001/remoteEntry.js
docker logs --tail 100 aurevia-bff
docker logs --tail 100 aurevia-operation-superset-1
```

نام دقیق container ممکن است بر اساس Compose تغییر کند؛ در آن صورت خروجی `docker compose ps` را مبنا قرار دهید.

## ۱۰. توقف، بروزرسانی و پاک‌سازی

توقف بدون حذف داده:

```bash
docker compose --env-file .env --profile superset \
  -f infra/docker-compose/compose.yml down
```

بروزرسانی کنترل‌شده:

```bash
git fetch --tags
git checkout <NEW_APPROVED_TAG>
npm ci
npm run build
./mvnw verify
docker compose --env-file .env --profile superset \
  -f infra/docker-compose/compose.yml up -d --build
```

از `down -v` استفاده نکنید مگر این‌که هدف، حذف غیرقابل‌بازگشت تمام دیتابیس‌ها و داده Demo باشد.

## ۱۱. خطاهای متداول

| خطا | علت محتمل |
|---|---|
| `Invalid credentials` بعد از callback | ناهماهنگی `OIDC_CLIENT_SECRET` با Keycloak |
| Manifest برابر 401/302 | session ایجاد نشده یا callback/issuer اشتباه است |
| منوی MFE خالی است | user فاقد `application:aurevia/<panel>/can_view` است |
| `remoteEntry.js` لود نمی‌شود | build فرانت انجام نشده یا tunnel پورت MFE برقرار نیست |
| Authorization Service بالا نمی‌آید | Store ID یا Model ID واقعی نیست |
| Superset دیر آماده می‌شود | `load-examples` در اجرای اول هنوز فعال است |
| Superset dashboard برابر 403 | asset منتشر نشده یا grant `view` وجود ندارد |

## محدودیت این روش

این روش برای ارائه، QA و آموزش مناسب است؛ نه داده واقعی یا کاربر Production. `start-dev` در Keycloak، دیتابیس تک‌نسخه، Basic Auth داخلی، Gateway نمونه، نبود mTLS و imageهای محلی دلایل اصلی این محدودیت‌اند.
