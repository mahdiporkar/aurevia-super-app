import type {Locale} from '@aurevia/contracts';
const messages={
  'fa-IR':{appName:'ابر اپلیکیشن اورویا',login:'ورود',logout:'خروج',loading:'در حال بارگذاری…',accessDenied:'دسترسی مجاز نیست',remoteUnavailable:'سامانه در دسترس نیست',language:'English'},
  'en-US':{appName:'Aurevia Super App',login:'Sign in',logout:'Sign out',loading:'Loading…',accessDenied:'Access denied',remoteUnavailable:'Application unavailable',language:'فارسی'}
} as const;
export const direction=(locale:Locale)=>locale==='fa-IR'?'rtl':'ltr';
export const t=(locale:Locale,key:keyof typeof messages['en-US'])=>messages[locale][key];
