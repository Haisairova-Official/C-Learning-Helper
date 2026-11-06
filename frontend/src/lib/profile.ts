import type { Profile, ProfileUpdate, Role } from '../api/types';
import { label } from './format';

export function normalizeProfile(value: Profile): Profile {
  return { ...value, classNames: Array.isArray(value.classNames) ? value.classNames : [], studentNumber: value.studentNumber ?? null };
}

export function accountSubtitle(role: Role | undefined, profile: Profile | null, fullClassList = false): string {
  if (!profile) return '账号资料加载中';
  if (role === 'STUDENT') {
    const names = profile.classNames ?? [];
    const classes = fullClassList ? names.join('、') : names.length > 1 ? `${names[0]} 等 ${names.length} 个班级` : names[0];
    return `${classes || '未加入班级'} · ${profile.studentNumber?.trim() || '学号未录入'}`;
  }
  return `${label(role || '')} · ${profile.username}`;
}

export const avatarMaxBytes = 512 * 1024;
export const replyStyles = [
  { value: 'CONCISE', title: '简洁', description: '指出关键问题，简要说明修改方向。' },
  { value: 'GUIDED', title: '引导', description: '通过提问，引导分析错误原因。' },
  { value: 'DETAILED', title: '详细', description: '详细说明错误原因、相关概念和排查步骤。' },
] as const;

export function profileValidation(input: ProfileUpdate, tinoUnlocked=false): string {
  if ([...input.nickname.trim()].length > 32) return '昵称最多 32 个字符。';
  if ([...input.bio].length > 500) return '个人简介最多 500 个字符。';
  if (!(tinoUnlocked && input.aiReplyStyle === 'TINO') && !replyStyles.some(style => style.value === input.aiReplyStyle)) return '请选择一种 AI 回复风格。';
  return '';
}

export function avatarValidation(file: Pick<File, 'type' | 'size'>): string {
  if (!['image/png', 'image/jpeg'].includes(file.type)) return '请选择 PNG 或 JPEG 图片。';
  if (!file.size) return '图片文件为空，请重新选择。';
  if (file.size > avatarMaxBytes) return '图片不能超过 512 KiB，请压缩后再上传。';
  return '';
}

// Only the authenticated avatar route is accepted. Blob URLs are generated
// in memory by the explicitly enabled development mock only.
export function avatarSource(value: string | null | undefined, mock = false): string | undefined {
  if (!value) return undefined;
  if (/^\/api\/profile\/avatar(?:\?v=[a-zA-Z0-9._~%:+-]+)?$/.test(value)) return value;
  if (mock && value.startsWith('blob:')) return value;
  return undefined;
}
