// getRandomValues also works on LAN HTTP origins, unlike randomUUID.
// A 128-bit hexadecimal identifier satisfies the API idempotency-key contract.
export function newClientId(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('');
}
