/** URL syntax for approved internal service names as well as LAN/public hosts. */
export declare function isServiceUrl(value: unknown, originOnly?: boolean): boolean;
export declare function serviceUrlRule(originOnly?: boolean): {
    validator: (_rule: unknown, value: unknown) => Promise<void>;
};
