import { JoinForm } from "./join-form";

/**
 * 邀请注册页。
 *
 * Next 15 里 params 是 Promise，必须 await（T-04）。这里放在服务端组件里 await，
 * 再把手里的 token 传给客户端表单——客户端就不需要再解析一次路由。
 */
export default async function JoinPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return <JoinForm token={token} />;
}
