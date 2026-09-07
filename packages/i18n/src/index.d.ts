import type { Locale } from '@aurevia/contracts';
declare const messages: {
    readonly 'fa-IR': {
        readonly appName: "ابر اپلیکیشن اورویا";
        readonly login: "ورود";
        readonly logout: "خروج";
        readonly loading: "در حال بارگذاری…";
        readonly accessDenied: "دسترسی مجاز نیست";
        readonly remoteUnavailable: "سامانه در دسترس نیست";
        readonly language: "English";
    };
    readonly 'en-US': {
        readonly appName: "Aurevia Super App";
        readonly login: "Sign in";
        readonly logout: "Sign out";
        readonly loading: "Loading…";
        readonly accessDenied: "Access denied";
        readonly remoteUnavailable: "Application unavailable";
        readonly language: "فارسی";
    };
};
export declare const direction: (locale: Locale) => "rtl" | "ltr";
export declare const t: (locale: Locale, key: keyof (typeof messages)["en-US"]) => "Access denied" | "ابر اپلیکیشن اورویا" | "ورود" | "خروج" | "در حال بارگذاری…" | "دسترسی مجاز نیست" | "سامانه در دسترس نیست" | "English" | "Aurevia Super App" | "Sign in" | "Sign out" | "Loading…" | "Application unavailable" | "فارسی";
export {};
