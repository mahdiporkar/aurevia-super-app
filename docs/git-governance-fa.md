# راهنمای Git و حاکمیت مخزن

## مالکیت و دسترسی

`CODEOWNERS` مالک پیش‌فرض و نواحی امنیتی را `@mahdiporkar` تعیین می‌کند. در GitHub، branch protection برای `main` باید این گزینه‌ها را اجباری کند:

- pull request پیش از merge؛
- حداقل یک approval و approval از CODEOWNER برای نواحی امنیتی؛
- dismiss stale approvals؛
- status checkهای `frontend`, `backend`, `configuration`؛
- conversation resolution؛
- جلوگیری از force-push و deletion؛
- امضای commit/tag برای release در صورت فراهم‌بودن کلید سازمانی.

ساخت collaborator از داخل commit ممکن نیست. مالک repository باید در `Settings → Collaborators and teams` نام حساب را اضافه و کمترین نقش لازم (`Read`, `Triage`, `Write`, `Maintain`, `Admin`) را انتخاب کند. token یا password نباید برای این کار در repository ذخیره شود.

## شاخه و commit

```text
main                 نسخه همیشه قابل release
feature/<topic>      قابلیت
fix/<topic>          رفع نقص
docs/<topic>         مستندات
chore/<topic>        ابزار و نگهداری
```

commitها از Conventional Commits پیروی می‌کنند: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `security`. scope نمونه: `bff`, `authz`, `shell`, `admin`, `infra`.

## کیفیت و release

GitHub Actions و GitLab CI هر دو typecheck/build فرانت، test جاوا، اعتبار Compose و dependency audit را اجرا می‌کنند. merge فقط پس از سبزشدن checkها انجام می‌شود. release با tag امضاشده SemVer، changelog، SBOM و artifact ساخته‌شده در CI منتشر می‌شود.

## secrets و incident

اگر secret وارد Git شد، حذف commit کافی نیست: ابتدا credential rotate/revoke، سپس incident ثبت و در صورت نیاز history پاک‌سازی می‌شود. فایل `.env` ignore است و `.env.example` فقط نام متغیرها و مقدار غیرواقعی دارد.
