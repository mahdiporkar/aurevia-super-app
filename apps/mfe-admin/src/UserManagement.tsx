import React,{useState}from'react';
import{Alert,Button,Card,Form,Input,Space,Switch,message}from'antd';
import{adminApi}from'./api';

type NewUser={username:string;firstName:string;lastName:string;email:string;
  initialPassword:string;enabled:boolean};
type CreatedUser=Omit<NewUser,'initialPassword'>&{id:string};
const required=[{required:true,message:'این فیلد الزامی است'}];

/** Passwords exist only in this form and its one same-origin backend request. */
export function UserManagement(){
  const[form]=Form.useForm<NewUser>();
  const[busy,setBusy]=useState(false);
  const[created,setCreated]=useState<CreatedUser>();
  const create=async(values:NewUser)=>{
    setBusy(true);setCreated(undefined);
    try{
      const user=await adminApi<CreatedUser>('/keycloak-users',{
        method:'POST',body:JSON.stringify(values)});
      setCreated(user);form.resetFields();message.success('کاربر ایجاد شد');
    }catch(error){message.error((error as Error).message)}
    finally{form.setFieldValue('initialPassword',undefined);setBusy(false)}
  };
  return <Card title="مدیریت کاربران">
    <Space direction="vertical" size={16} style={{width:'100%'}}>
      <Alert showIcon type="info" message="ایجاد کاربر در Keycloak"
        description="ایجاد حساب، مجوز دسترسی به برنامه‌ها را اعطا نمی‌کند. پس از اولین ورود کاربر، نقش‌ها و مجوزهای او را از بخش مجوزها تنظیم کنید."/>
      <Form form={form} layout="vertical" onFinish={create} initialValues={{enabled:true}}
        autoComplete="off" disabled={busy} preserve={false}>
        <Space wrap align="start">
          <Form.Item name="username" label="نام کاربری" rules={[...required,{max:255},
            {pattern:/^[^\s\x00-\x1f\x7f]+$/,message:'نام کاربری نباید فاصله داشته باشد'}]}>
            <Input autoComplete="off" maxLength={255}/></Form.Item>
          <Form.Item name="firstName" label="نام" rules={[...required,{max:255}]}>
            <Input maxLength={255}/></Form.Item>
          <Form.Item name="lastName" label="نام خانوادگی" rules={[...required,{max:255}]}>
            <Input maxLength={255}/></Form.Item>
          <Form.Item name="email" label="ایمیل" rules={[...required,{type:'email'},{max:254}]}>
            <Input type="email" autoComplete="off" maxLength={254}/></Form.Item>
          <Form.Item name="initialPassword" label="رمز عبور اولیه" rules={[...required,{max:1024}]}>
            <Input.Password autoComplete="new-password" maxLength={1024}/></Form.Item>
          <Form.Item name="enabled" label="فعال" valuePropName="checked"><Switch/></Form.Item>
        </Space>
        <div><Button type="primary" htmlType="submit" loading={busy}>ایجاد کاربر</Button></div>
      </Form>
      {created&&<Alert showIcon type="success" message={`کاربر ${created.username} ایجاد شد`}
        description={<span>شناسه پایدار کاربر: <code dir="ltr">{created.id}</code></span>}/>}
    </Space>
  </Card>;
}
