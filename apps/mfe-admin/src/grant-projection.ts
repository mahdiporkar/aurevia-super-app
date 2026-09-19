export type GrantProjection = {
  projection_status?: string;
  projection_error?: string | null;
  expires_at?: string | null;
};

const states:Record<string,{label:string;color:string}> = {
  APPLIED:{label:'اعمال شده',color:'green'},
  PENDING:{label:'در انتظار اعمال',color:'gold'},
  RETRYING:{label:'در حال تلاش مجدد',color:'orange'},
  FAILED:{label:'همگام‌سازی ناموفق',color:'red'},
  REVOKED:{label:'لغو شده در OpenFGA',color:'default'},
  UNKNOWN:{label:'وضعیت همگام‌سازی نامشخص',color:'default'},
  EXPIRED:{label:'منقضی شده',color:'default'},
};

export function grantProjection(grant:GrantProjection,now=Date.now()) {
  const expired=!!grant.expires_at&&Date.parse(grant.expires_at)<=now;
  const status=expired?'EXPIRED':grant.projection_status&&states[grant.projection_status]
    ?grant.projection_status:'UNKNOWN';
  return {status,...states[status]!,error:grant.projection_error,
    pending:!expired&&(status==='PENDING'||status==='RETRYING')};
}
