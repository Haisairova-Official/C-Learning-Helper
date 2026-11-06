export function aiFailureMessage(code: string | null): string {
  const messages: Record<string, string> = {
    AUTHENTICATION: 'AI 服务暂不可用，请联系教师或管理员。',
    QUOTA_EXCEEDED: 'AI 服务额度不足，请联系教师或管理员。',
    RATE_LIMITED: 'AI 服务当前繁忙，请稍后重试。',
    TIMEOUT: '本次分析等待超时，可以稍后重新分析。',
    INVALID_RESPONSE: '本次未能生成有效指导，请重试。',
    UNSAFE_RESPONSE: '本次未能生成有效指导，请重试。',
    INTERRUPTED: '本次分析已中断，请重新发起。',
  };
  return messages[code ?? ''] ?? '暂时无法提供指导，请稍后重试。';
}
