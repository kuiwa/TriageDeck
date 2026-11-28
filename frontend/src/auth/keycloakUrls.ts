import { userManager } from "./userManager";

/**
 * 顶部账号入口的跳转地址（浏览器直接打开 Keycloak 页面）。
 *
 * 为什么不应用内直调 API：
 * - Admin API 明确禁止前端调用（AGENTS §2.1.6）；
 * - Account REST API 虽然存在，但 Keycloak 26 的实际响应不带 CORS 头
 *   （preflight 有、真实 GET/POST 没有），从 :8080 跨源直连会被浏览器拦截。
 * 因此资料/密码统一交给 Keycloak 账户中心，应用内不接触这些字段。
 */

const authority = (userManager.settings.authority ?? "").replace(/\/+$/, "");
const realm = /\/realms\/([^/]+)$/.exec(authority)?.[1] ?? "triagedeck";
const serverBaseUrl = authority.replace(/\/realms\/[^/]+$/, "") || window.location.origin;

/** 账户中心首页 = 个人信息（firstName / lastName / email） */
export const KEYCLOAK_ACCOUNT_PROFILE_URL = `${authority}/account/`;

/** 账户中心的「Signing in」页：修改密码 */
export const KEYCLOAK_ACCOUNT_PASSWORD_URL = `${authority}/account/account-security/signing-in`;

/** Keycloak 管理控制台（需要 realm 管理员权限，普通账号进去会被 Keycloak 拒绝） */
export const KEYCLOAK_ADMIN_CONSOLE_URL = `${serverBaseUrl}/admin/${realm}/console/`;
