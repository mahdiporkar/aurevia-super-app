import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
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
import type { HostRuntime, MicroFrontendProps, RemoteContext, RemoteModule } from "@aurevia/contracts";
import { createJsonHttpClient } from "@aurevia/http-client";
import { Link, Navigate, Route, Routes, useParams } from "react-router-dom";
import { evaluateSHPolicy, SHAction, SHManifestProvider, SHRouteGuard } from "@aurevia/sh-core-ui";
/** Shell-facing component contract; `mount` below is retained only for legacy hosts. */
export const contractVersion = "1.0" as const;
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
    details: "جزئیات",
    back: "بازگشت به فهرست",
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
    details: "Details",
    back: "Back to list",
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
async function request<T>(runtime:HostRuntime,path:string,init?:RequestInit):Promise<T>{if(init?.method==='POST')return runtime.http.post<T,unknown>(path,JSON.parse(String(init.body??'{}')));if(init?.method==='PUT')return runtime.http.put<T,unknown>(path,JSON.parse(String(init.body??'{}')));return runtime.http.get<T>(path)}
function HrApplication({ context,runtime }: { context: RemoteContext;runtime:HostRuntime }) {
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
        request<ListResponse<Employee>>(runtime,"/employees"),
        request<ListResponse<Department>>(runtime,"/departments"),
        request<ListResponse<Position>>(runtime,"/positions"),
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
    await request(runtime,id ? `/employees/${id}` : "/employees", {
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
                        <Space>
                          {evaluateSHPolicy(context.manifest,false,'page:hr.employee.detail','view').allowed&&
                            <SHAction resource="business:hr.employee" action="view">
                              <Link to={encodeURIComponent(row.id)}><Button>{copy.details}</Button></Link>
                            </SHAction>}
                          <SHAction resource="business:hr.employee" action="update">
                            <Button onClick={() => open(row)}>{copy.edit}</Button>
                          </SHAction>
                        </Space>
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
function HrReferencePage({ context,runtime, kind }: { context: RemoteContext;runtime:HostRuntime; kind: "departments" | "positions" }) {
  const [rows, setRows] = useState<Array<Department | Position>>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    request<ListResponse<Department | Position>>(runtime,`/${kind}`)
      .then((result) => setRows(result.items))
      .catch((reason) => setError(reason instanceof Error ? reason.message : String(reason)))
      .finally(() => setLoading(false));
  }, [kind]);
  const fa = context.locale === "fa-IR";
  const title = kind === "departments" ? (fa ? "واحدهای سازمانی" : "Departments") : (fa ? "سمت‌های سازمانی" : "Positions");
  if (loading) return <Card><Spin /></Card>;
  if (error) return <Alert type="error" showIcon message={error} />;
  return <Space direction="vertical" size={18} style={{ width: "100%" }}>
    <div><Typography.Title level={3}>{title}</Typography.Title><Typography.Text type="secondary">{fa ? "داده آزمایشی دریافت‌شده از سرویس عملیاتی HR" : "Demo data loaded from the operational HR service"}</Typography.Text></div>
    <Row gutter={[16, 16]}><Col xs={24} md={12}><Card><Statistic title={fa ? "تعداد رکورد" : "Records"} value={rows.length} /></Card></Col><Col xs={24} md={12}><Card><Statistic title={fa ? "وضعیت سرویس" : "Service status"} value={fa ? "فعال" : "Online"} /></Card></Col></Row>
    <Card><Table rowKey="id" dataSource={rows} pagination={false} columns={[
      { title: "ID", dataIndex: "id" },
      { title: fa ? "عنوان" : "Title", dataIndex: "name" },
      ...(kind === "departments" ? [{ title: fa ? "واحد مکانی" : "Organization unit", dataIndex: "orgUnit", render: (value: string) => <Tag color="blue">{value}</Tag> }] : []),
      { title: fa ? "وضعیت" : "Status", render: () => <Tag color="success">{fa ? "فعال" : "Active"}</Tag> },
    ]} /></Card>
  </Space>;
}
function EmployeeDetails({context,runtime}:{context:RemoteContext;runtime:HostRuntime}){
  const{id}=useParams(),copy=messages[context.locale];
  const[employee,setEmployee]=useState<Employee>(),[loading,setLoading]=useState(true),[error,setError]=useState(''),[attempt,setAttempt]=useState(0);
  useEffect(()=>{if(!id){setError('شناسه پرسنل معتبر نیست');setLoading(false);return}let active=true;setLoading(true);setError('');request<Employee>(runtime,`/employees/${encodeURIComponent(id)}`).then(value=>{if(active)setEmployee(value)}).catch(reason=>{if(active)setError(reason instanceof Error?reason.message:String(reason))}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[id,runtime,attempt]);
  if(loading)return <Card><Spin tip={copy.loading}/></Card>;
  if(error)return <Alert type="error" showIcon message={copy.error} description={error} action={<Button onClick={()=>setAttempt(value=>value+1)}>{copy.retry}</Button>}/>;
  if(!employee)return <Empty description={copy.empty}/>;
  return <Card title={copy.details} extra={<Link to="../personal">{copy.back}</Link>}>
    <Descriptions bordered column={{xs:1,md:2}} items={[
      {key:'id',label:'ID',children:employee.id},
      {key:'name',label:copy.name,children:employee.name},
      {key:'department',label:copy.department,children:employee.department??employee.departmentId??'—'},
      {key:'position',label:copy.position,children:employee.positionId??'—'},
      {key:'orgUnit',label:copy.branch,children:employee.orgUnit},
      {key:'salary',label:'حقوق',children:<SHAction resource="field:hr.employee.salary-amount" action="view" mode="hide"><span>{employee.salary==null?'—':new Intl.NumberFormat(context.locale).format(employee.salary)}</span></SHAction>},
    ]}/>
  </Card>
}
function HrWorkspace({context,runtime}:{context:RemoteContext;runtime:HostRuntime}){const fa=context.locale==='fa-IR',canView=(resource:string)=>evaluateSHPolicy(context.manifest,false,resource,'view').allowed;return <Space direction="vertical" size={18} style={{width:'100%'}}><Space><Link to="personal">{fa?'کارکنان':'Employees'}</Link>{canView('page:hr.departments')&&<Link to="departments">{fa?'واحدها':'Departments'}</Link>}{canView('page:hr.positions')&&<Link to="positions">{fa?'سمت‌ها':'Positions'}</Link>}</Space><Routes><Route index element={<Navigate to="personal" replace/>}/><Route path="personal" element={<SHRouteGuard resource="page:hr.employee.list" action="view"><HrApplication context={context} runtime={runtime}/></SHRouteGuard>}/><Route path="personal/:id" element={<SHRouteGuard resource="page:hr.employee.detail" action="view"><EmployeeDetails context={context} runtime={runtime}/></SHRouteGuard>}/><Route path="departments" element={<SHRouteGuard resource="page:hr.departments" action="view"><HrReferencePage context={context} runtime={runtime} kind="departments"/></SHRouteGuard>}/><Route path="positions" element={<SHRouteGuard resource="page:hr.positions" action="view"><HrReferencePage context={context} runtime={runtime} kind="positions"/></SHRouteGuard>}/><Route path="*" element={<Alert type="warning" message="صفحه HR یافت نشد"/>}/></Routes></Space>}
export function App({runtime,manifest}:{runtime:HostRuntime;manifest:MicroFrontendProps['manifest']}){const context:RemoteContext={locale:runtime.theme.locale,manifest,correlationId:()=>crypto.randomUUID()};return <SHManifestProvider initial={manifest}><HrWorkspace context={context} runtime={runtime}/></SHManifestProvider>}
export const plugin={contractVersion:'1.0' as const,App};
export const mount: RemoteModule["mount"] = (element, context) => {
  const root = createRoot(element);
  const client=createJsonHttpClient({basePath:'/hr-micro/api/v1'});
  const legacyRuntime={mode:'embedded',moduleKey:'mfe-hr',routePrefix:'hr',http:{get:<T,>(p:string,o?:{headers?:Record<string,string>;signal?:AbortSignal})=>client.get<T>(p,o),post:<T,B>(p:string,b:B,o?:{headers?:Record<string,string>;signal?:AbortSignal})=>client.post<T,B>(p,b,o),put:<T,B>(p:string,b:B,o?:{headers?:Record<string,string>;signal?:AbortSignal})=>client.put<T,B>(p,b,o)},navigation:{navigate:()=>{},getModuleBasePath:()=>'/hr'},session:{getCurrentUser:()=>null,subscribe:()=>()=>{}},notifications:{success:()=>{},error:()=>{}},events:{emit:()=>{},subscribe:()=>()=>{}},sharedState:{get:()=>undefined,subscribe:()=>()=>{}},theme:{locale:context.locale,direction:context.locale==='fa-IR'?'rtl':'ltr'}} satisfies HostRuntime;
  root.render(<App runtime={legacyRuntime} manifest={context.manifest}/>);
  return () => root.unmount();
};
