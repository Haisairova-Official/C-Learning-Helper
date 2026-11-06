import { post, request } from './http';
import type { AiReplyStyle, Assignment, Page, Role, TeacherProblem } from './types';
export interface ManagedUser { tinoUnlocked:boolean; id:string; username:string; displayName:string; role:Role; studentNumber:string|null; nickname:string; bio:string; aiReplyStyle:AiReplyStyle; enabled:boolean; avatarUrl:string|null }
export interface CreateUser { username:string; displayName:string; role:Role; studentNumber:string; password:string }
export type EditUser = Pick<ManagedUser,'displayName'|'studentNumber'|'nickname'|'bio'|'aiReplyStyle'>;
export interface RosterRow { rowNumber:number; studentNumber:string; name:string; username:string; className:string }
export interface RosterCandidate extends RosterRow { action:'NEW'|'EXISTING'|'ALREADY_MEMBER'|'INVALID'; issues:string[] }
export interface RosterPreview { rows:RosterCandidate[]; warnings:string[] }
export interface RosterResult { created:number; added:number; unchanged:number; studentIds:string[] }
export interface Library { id:string; title:string; problemCount:number }
export interface LibraryDetail { id:string; title:string; problems:TeacherProblem[] }
export interface MutedStudent { studentId:string; name:string; studentNumber:string|null; mutedAt:string }
export interface ChatMessage { id:string; authorId:string; authorName:string; authorAvatarUrl?:string|null; role:Role; body:string; createdAt:string; retracted:boolean }
export interface EditAssignment { title:string; opensAt:string; closesAt:string; aiEnabled:boolean; chatEnabled:boolean }
const id = encodeURIComponent;
export const management = {
  assignment: (assignmentId:string) => request<Assignment>(`/teacher/assignments/${id(assignmentId)}`),
  editAssignment: (assignmentId:string,input:EditAssignment) => request<Assignment>(`/teacher/assignments/${id(assignmentId)}`,{method:'PUT',body:JSON.stringify(input)}),
  users: (page=1,q='') => request<Page<ManagedUser>>(`/admin/users?page=${page}&pageSize=20&q=${id(q)}`),
  user: (userId:string) => request<ManagedUser>(`/admin/users/${id(userId)}`),
  createUser: (input:CreateUser) => post<ManagedUser>('/admin/users',input),
  editUser: (userId:string,input:EditUser) => request<ManagedUser>(`/admin/users/${id(userId)}`,{method:'PUT',body:JSON.stringify(input)}),
  uploadAvatar: (userId:string,file:File) => {const body=new FormData();body.set('file',file);return request<ManagedUser>(`/admin/users/${id(userId)}/avatar`,{method:'POST',body});},
  removeAvatar: (userId:string) => request<ManagedUser>(`/admin/users/${id(userId)}/avatar`,{method:'DELETE'}),
  previewRoster: (classId:string,input:string|File) => {const url=`/classes/${id(classId)}/students/import/preview`;if(typeof input==='string') return post<RosterPreview>(url,{text:input});const body=new FormData();body.set('file',input);return request<RosterPreview>(url,{method:'POST',body});},
  validateRoster: (classId:string,rows:RosterRow[]) => post<RosterPreview>(`/classes/${id(classId)}/students/import/validate`,{rows}),
  commitRoster: (classId:string,rows:RosterRow[],initialPassword:string,key:string) => post<RosterResult>(`/classes/${id(classId)}/students/import/commit`,{rows,initialPassword},key),
  libraries: (page=1,q='') => request<Page<Library>>(`/teacher/libraries?page=${page}&pageSize=100&q=${id(q)}`),
  library: (libraryId:string) => request<LibraryDetail>(`/teacher/libraries/${id(libraryId)}`),
  createLibrary: (title:string) => post<Library>('/teacher/libraries',{title}),
  renameLibrary: (libraryId:string,title:string) => request<void>(`/teacher/libraries/${id(libraryId)}`,{method:'PUT',body:JSON.stringify({title})}),
  saveLibraryProblem: (libraryId:string,p:TeacherProblem) => p.id ? request<TeacherProblem>(`/teacher/libraries/${id(libraryId)}/problems/${id(p.id)}`,{method:'PUT',body:JSON.stringify(p)}) : post<TeacherProblem>(`/teacher/libraries/${id(libraryId)}/problems`,p),
  addLibraryProblems: (libraryId:string,ids:string[]) => post<void>(`/teacher/libraries/${id(libraryId)}/members`,ids),
  messages: (assignmentId:string,problemId:string,after='0') => request<{items:ChatMessage[];nextCursor:string;muted:boolean}>(`/assignments/${id(assignmentId)}/problems/${id(problemId)}/chat?after=${id(after)}`),
  mutedStudents: (assignmentId:string) => request<MutedStudent[]>(`/assignments/${id(assignmentId)}/chat/mutes`),
  muteStudent: (assignmentId:string,studentId:string) => post<void>(`/assignments/${id(assignmentId)}/chat/mutes`,{studentId}),
  unmuteStudent: (assignmentId:string,studentId:string) => request<void>(`/assignments/${id(assignmentId)}/chat/mutes/${id(studentId)}`,{method:'DELETE'}),
  retractMessage: (assignmentId:string,problemId:string,messageId:string) => request<void>(`/assignments/${id(assignmentId)}/problems/${id(problemId)}/chat/messages/${id(messageId)}`,{method:'DELETE'}),
  sendMessage: (assignmentId:string,problemId:string,body:string,key:string) => post<ChatMessage>(`/assignments/${id(assignmentId)}/problems/${id(problemId)}/chat`,{body},key),
};
