import React from 'react';
import { Alert, Card, Descriptions, Space, Typography } from 'antd';

const documentUrl = 'https://github.com/mahdiporkar/aurevia-super-app/blob/main/docs/operator-admin-form-field-guide-fa.md';

export function OperatorGuide() {
  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    <Alert showIcon type="info" message="مرجع تمام فرم‌ها و فیلدهای میکرو راهبری"
      description="سند مرجع شامل الزام هر فیلد، قالب معتبر، مثال، وابستگی شرطی، اثر امنیتی، خطاهای رایج و سناریوهای کامل Legacy/OAuth2/Superset است." />
    <Card title="راهنمای راهبر">
      <Typography.Paragraph>
        <Typography.Link href={documentUrl} target="_blank" rel="noopener noreferrer">
          بازکردن راهنمای جامع field-by-field در مخزن Git
        </Typography.Link>
      </Typography.Paragraph>
      <Descriptions column={1} bordered size="small" items={[
        { key: 'identifiers', label: 'شناسه‌های پایدار', children: 'code، slug، canonical key و reference را پس از مصرف تغییر ندهید.' },
        { key: 'secrets', label: 'Secret و Token', children: 'فقط secret:// و connection:// ثبت می‌شوند؛ مقدار credential یا token نباید وارد فرم شود.' },
        { key: 'routing', label: 'Route', children: 'Target، Route و Operation را جدا تعریف و Preview/Resolution Test را پیش از فعال‌سازی اجرا کنید.' },
        { key: 'access', label: 'دسترسی', children: 'Grant به Role/Group بر استثنای فردی مقدم است و وضعیت Outbox باید APPLIED شود.' },
        { key: 'conflict', label: 'VERSION_CONFLICT', children: 'صفحه را بازخوانی کنید؛ رکورد هم‌زمان توسط راهبر دیگری تغییر کرده است.' },
      ]} />
    </Card>
  </Space>;
}
