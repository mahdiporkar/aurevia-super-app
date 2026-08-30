import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from "antd";
import type { RemoteContext, RemoteModule } from "@aurevia/contracts";
import { SHAction, SHManifestProvider, SHRouteGuard } from "@aurevia/sh-core-ui";
export const contractVersion = "1" as const;
type Employee = {
  id: string;
  name: string;
  department?: string;
  departmentId?: string;
  positionId?: string;
  orgUnit: string;
  salary?: number;
};
type Department = { id: string; name: string; orgUnit: string };
type Position = { id: string; name: string };
type ListResponse<T> = { items: T[]; enforcedScope?: { orgUnit?: string } };
const messages = {
  "fa-IR": {
    title: "منابع انسانی",
    description: "صفحه کارکنان با کنترل مستقل صفحه، عملیات و فیلد حقوق در OpenFGA",
    employees: "کارکنان",
    departments: "واحدهای سازمانی",
    positions: "سمت‌ها",
    visible: "کارکنان قابل مشاهده",
    units: "واحدهای مجاز",
    scope: "محدوده داده",
    add: "افزودن کارمند",
    edit: "ویرایش",
    retry: "تلاش مجدد",
    loading: "در حال دریافت اطلاعات…",
    error: "دریافت اطلاعات منابع انسانی ناموفق بود",
    empty: "داده‌ای در محدوده مجاز شما وجود ندارد",
    name: "نام و نام خانوادگی",
    department: "دپارتمان",
    position: "سمت",
    branch: "واحد سازمانی",
    status: "وضعیت",
    active: "فعال",
    save: "ذخیره",
    cancel: "انصراف",
    scopeInfo: "این محدوده در سرویس عملیاتی اعمال شده است.",
    saved: "اطلاعات کارمند ذخیره شد",
    required: "این فیلد الزامی است",
  },
  "en-US": {
    title: "Human Resources",
    description: "Employee page with independent OpenFGA page, action, and salary-field controls",
    employees: "Employees",
    departments: "Departments",
    positions: "Positions",
    visible: "Visible employees",
    units: "Allowed units",
    scope: "Data scope",
    add: "Add employee",
    edit: "Edit",
    retry: "Retry",
    loading: "Loading HR data…",
    error: "Could not load Human Resources data",
    empty: "No data exists in your allowed scope",
    name: "Full name",
    department: "Department",
    position: "Position",
    branch: "Organization unit",
    status: "Status",
    active: "Active",
    save: "Save",
    cancel: "Cancel",
    scopeInfo: "This scope was enforced by the operational service.",
    saved: "Employee saved",
    required: "This field is required",
  },
} as const;
let csrf: { headerName: string; token: string } | undefined;
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? "GET";
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Correlation-ID": crypto.randomUUID(),
    ...((init?.headers as Record<string, string>) ?? {}),
  };
  if (method !== "GET" && method !== "HEAD") {
    const token: { headerName: string; token: string } = csrf
      ? csrf
      : await fetch("/api/v1/csrf", { credentials: "same-origin" }).then(
          async (response) => {
            if (!response.ok) throw new Error(`CSRF HTTP ${response.status}`);
            return (await response.json()) as {
              headerName: string;
              token: string;
            };
          },
        );
    csrf = token;
    headers[token.headerName] = token.token;
  }
  const response = await fetch(`/hr-micro/api/v1${path}`, {
    ...init,
    credentials: "same-origin",
    headers,
  });
  if (!response.ok)
    throw new Error((await response.text()) || `HTTP ${response.status}`);
  return response.json() as Promise<T>;
}
function HrApplication({ context }: { context: RemoteContext }) {
  const copy = messages[context.locale];
  const [employees, setEmployees] = useState<Employee[]>([]),
    [departments, setDepartments] = useState<Department[]>([]),
    [positions, setPositions] = useState<Position[]>([]),
    [scope, setScope] = useState("—"),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [editor, setEditor] = useState<Employee | null | undefined>();
  const [form] = Form.useForm();
  const load = async () => {
    setLoading(true);
    setError("");
    try {
      const [e, d, p] = await Promise.all([
        request<ListResponse<Employee>>("/employees"),
        request<ListResponse<Department>>("/departments"),
        request<ListResponse<Position>>("/positions"),
      ]);
      setEmployees(e.items);
      setDepartments(d.items);
      setPositions(p.items);
      setScope(e.enforcedScope?.orgUnit ?? d.enforcedScope?.orgUnit ?? "—");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : String(reason));
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, []);
  const departmentNames = useMemo(
    () => new Map(departments.map((item) => [item.id, item.name])),
    [departments],
  );
  const open = (employee?: Employee) => {
    setEditor(employee ?? null);
    form.setFieldsValue(employee ?? { orgUnit: scope === "—" ? "" : scope });
  };
  const save = async (values: Record<string, string>) => {
    const id = editor?.id;
    await request(id ? `/employees/${id}` : "/employees", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(values),
    });
    message.success(copy.saved);
    setEditor(undefined);
    form.resetFields();
    await load();
  };
  if (loading)
    return (
      <Card>
        <Spin tip={copy.loading} />
      </Card>
    );
  if (error)
    return (
      <Alert
        showIcon
        type="error"
        message={copy.error}
        description={error}
        action={<Button onClick={() => void load()}>{copy.retry}</Button>}
      />
    );
  const empty = <Empty description={copy.empty} />;
  return (
    <Space direction="vertical" size={18} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={3} style={{ marginBottom: 4 }}>{copy.employees}</Typography.Title>
        <Typography.Text type="secondary">{copy.description}</Typography.Text>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title={copy.visible} value={employees.length} />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic
              title={copy.units}
              value={new Set(departments.map((item) => item.orgUnit)).size}
            />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title={copy.scope} value={scope} />
          </Card>
        </Col>
      </Row>
      <Alert
        showIcon
        type="info"
        message={`${copy.scope}: ${scope}`}
        description={copy.scopeInfo}
      />
      <Card
                extra={
                  <SHAction
                    resource="business:hr.employee"
                    action="create"
                    mode="disable"
                  >
                    <Button type="primary" onClick={() => open()}>
                      {copy.add}
                    </Button>
                  </SHAction>
                }
              >
                <Table
                  rowKey="id"
                  dataSource={employees}
                  locale={{ emptyText: empty }}
                  columns={[
                    { title: copy.name, dataIndex: "name" },
                    {
                      title: copy.department,
                      render: (_, row) =>
                        row.department ??
                        departmentNames.get(row.departmentId ?? "") ??
                        "—",
                    },
                    { title: copy.branch, dataIndex: "orgUnit" },
                    {
                      title: copy.status,
                      render: () => <Tag color="success">{copy.active}</Tag>,
                    },
                    {
                      title: "",
                      render: (_, row) => (
                        <SHAction resource="business:hr.employee" action="update">
                          <Button onClick={() => open(row)}>{copy.edit}</Button>
                        </SHAction>
                      ),
                    },
                  ]}
                />
      </Card>
      <Modal
        open={editor !== undefined}
        title={editor ? copy.edit : copy.add}
        okText={copy.save}
        cancelText={copy.cancel}
        onCancel={() => setEditor(undefined)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={save}>
          <Form.Item
            name="name"
            label={copy.name}
            rules={[{ required: true, message: copy.required }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="departmentId"
            label={copy.department}
            rules={[{ required: true, message: copy.required }]}
          >
            <Select
              options={departments.map((item) => ({
                value: item.id,
                label: item.name,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="positionId"
            label={copy.position}
            rules={[{ required: true, message: copy.required }]}
          >
            <Select
              options={positions.map((item) => ({
                value: item.id,
                label: item.name,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="orgUnit"
            label={copy.branch}
            rules={[{ required: true, message: copy.required }]}
          >
            <Input readOnly />
          </Form.Item>
          <Form.Item name="salary" label="حقوق">
            <SHAction
              resource="field:hr.employee.salary-amount"
              action="view"
              mode="readOnly"
            >
              <Input />
            </SHAction>
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
export const mount: RemoteModule["mount"] = (element, context) => {
  const root = createRoot(element);
  root.render(
    <SHManifestProvider initial={context.manifest}>
      <SHRouteGuard resource="page:hr.employee.list" action="view">
        <HrApplication context={context} />
      </SHRouteGuard>
    </SHManifestProvider>,
  );
  return () => root.unmount();
};
