import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { Alert, Button, Card, Col, Empty, Form, InputNumber, Modal, Row, Space, Spin, Statistic, Table, Tag, Typography, message } from "antd";
import type { RemoteContext, RemoteModule } from "@aurevia/contracts";
import { SHAction, SHManifestProvider, SHRouteGuard } from "@aurevia/sh-core-ui";

export const contractVersion = "1" as const;
type Payment = { id: string; amount: number; maker: string; status: string };
type ListResponse<T> = { items: T[] };
const messages = {
  "fa-IR": { title: "مدیریت پرداخت‌ها", description: "صف پرداخت عملیاتی با کنترل دسترسی مستقل در OpenFGA", payments: "صف پرداخت", pending: "در انتظار تأیید", total: "مبلغ کل صف", amount: "مبلغ", status: "وضعیت", maker: "ایجادکننده", create: "پرداخت جدید", approve: "تأیید", reject: "رد", retry: "تلاش مجدد", loading: "در حال دریافت پرداخت‌ها…", error: "دریافت اطلاعات پرداخت ناموفق بود", empty: "پرداختی برای نمایش وجود ندارد", save: "ذخیره", cancel: "انصراف", saved: "عملیات با موفقیت انجام شد", required: "مبلغ الزامی است" },
  "en-US": { title: "Payment Management", description: "Operational payment queue protected independently by OpenFGA", payments: "Payment queue", pending: "Pending approval", total: "Total queued amount", amount: "Amount", status: "Status", maker: "Maker", create: "New payment", approve: "Approve", reject: "Reject", retry: "Retry", loading: "Loading payments…", error: "Could not load payment data", empty: "No payments to display", save: "Save", cancel: "Cancel", saved: "Operation completed", required: "Amount is required" },
} as const;
let csrf: { headerName: string; token: string } | undefined;

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? "GET";
  const headers: Record<string, string> = { "Content-Type": "application/json", "X-Correlation-ID": crypto.randomUUID(), ...((init?.headers as Record<string, string>) ?? {}) };
  if (method !== "GET" && method !== "HEAD") {
    const token: { headerName: string; token: string } = csrf ?? await fetch("/api/v1/csrf", { credentials: "same-origin" }).then(async response => {
      if (!response.ok) throw new Error(`CSRF HTTP ${response.status}`);
      return await response.json() as { headerName: string; token: string };
    });
    csrf = token;
    headers[token.headerName] = token.token;
  }
  const response = await fetch(`/finance-micro/api/v1${path}`, { ...init, credentials: "same-origin", headers });
  if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
  return response.json() as Promise<T>;
}

function PaymentPage({ context }: { context: RemoteContext }) {
  const copy = messages[context.locale];
  const [payments, setPayments] = useState<Payment[]>([]), [loading, setLoading] = useState(true), [error, setError] = useState(""), [dialog, setDialog] = useState(false);
  const [form] = Form.useForm();
  const load = async () => {
    setLoading(true); setError("");
    try { setPayments((await request<ListResponse<Payment>>("/payments")).items); }
    catch (reason) { setError(reason instanceof Error ? reason.message : String(reason)); }
    finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  const mutate = async (path: string) => {
    try { await request(path, { method: "POST", body: "{}" }); message.success(copy.saved); await load(); }
    catch (reason) { message.error(reason instanceof Error ? reason.message : String(reason)); }
  };
  const create = async (values: { amount: number }) => {
    try { await request("/payments", { method: "POST", body: JSON.stringify(values) }); message.success(copy.saved); setDialog(false); form.resetFields(); await load(); }
    catch (reason) { message.error(reason instanceof Error ? reason.message : String(reason)); }
  };
  const money = (value: number) => new Intl.NumberFormat(context.locale).format(value);
  if (loading) return <Card><Spin tip={copy.loading} /></Card>;
  if (error) return <Alert showIcon type="error" message={copy.error} description={error} action={<Button onClick={() => void load()}>{copy.retry}</Button>} />;
  return <Space direction="vertical" size={18} style={{ width: "100%" }}>
      <div><Typography.Title level={3} style={{ marginBottom: 4 }}>{copy.title}</Typography.Title><Typography.Text type="secondary">{copy.description}</Typography.Text></div>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}><Card><Statistic title={copy.pending} value={payments.filter(item => item.status === "PENDING_APPROVAL").length} /></Card></Col>
        <Col xs={24} md={12}><Card><Statistic title={copy.total} value={payments.reduce((sum, item) => sum + item.amount, 0)} formatter={value => money(Number(value))} /></Card></Col>
      </Row>
      <Card title={copy.payments} extra={<SHAction resource="finance.payment" action="create" mode="disable"><Button type="primary" onClick={() => setDialog(true)}>{copy.create}</Button></SHAction>}>
        <Table rowKey="id" dataSource={payments} locale={{ emptyText: <Empty description={copy.empty} /> }} columns={[
          { title: "ID", dataIndex: "id" },
          { title: copy.amount, dataIndex: "amount", render: money },
          { title: copy.maker, dataIndex: "maker" },
          { title: copy.status, dataIndex: "status", render: value => <Tag color="processing">{value}</Tag> },
          { title: "", render: (_, row) => <Space><SHAction resource="finance.payment" action="approve"><Button type="primary" onClick={() => void mutate(`/payments/${row.id}/approve`)}>{copy.approve}</Button></SHAction><SHAction resource="finance.payment" action="reject"><Button danger onClick={() => void mutate(`/payments/${row.id}/reject`)}>{copy.reject}</Button></SHAction></Space> },
        ]} />
      </Card>
      <Modal open={dialog} title={copy.create} okText={copy.save} cancelText={copy.cancel} onCancel={() => setDialog(false)} onOk={() => form.submit()}>
        <Form form={form} layout="vertical" onFinish={create}><Form.Item name="amount" label={copy.amount} rules={[{ required: true, message: copy.required }]}><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Form>
      </Modal>
    </Space>;
}

export const mount: RemoteModule["mount"] = (element, context) => {
  const root = createRoot(element);
  root.render(<SHManifestProvider initial={context.manifest}><SHRouteGuard resource="page:finance.payments" action="view"><PaymentPage context={context} /></SHRouteGuard></SHManifestProvider>);
  return () => root.unmount();
};
