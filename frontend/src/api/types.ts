export type Role = 'STUDENT' | 'TEACHER' | 'ADMIN';
export interface Actor { id: string; role: Role }
export type AiReplyStyle = 'CONCISE' | 'GUIDED' | 'DETAILED' | 'TINO';
export interface Profile { tinoUnlocked?:boolean; username: string; displayName: string; nickname: string; bio: string; avatarUrl: string | null; aiReplyStyle: AiReplyStyle; updatedAt: string; classNames: string[]; studentNumber: string | null }
export type ProfileUpdate = Pick<Profile, 'nickname' | 'bio' | 'aiReplyStyle'>;
export interface Page<T> { items: T[]; page: number; pageSize: number; total: number }
export interface Sample { input: string; output: string; explanation?: string }
export type ProblemType = 'PROGRAM' | 'FUNCTION';
export type Language = 'C17' | 'CPP17';
export interface Problem { id: string; versionId: string; title: string; statement: string; inputDescription: string; outputDescription: string; samples: Sample[]; timeLimitMs: number; memoryLimitMb: number; codeLimitBytes: number; maxScore: number; problemType: ProblemType; language: Language; functionSignature: string; starterCode: string }
export interface TestCase { id?: string; key?: string; input: string | null; expectedOutput: string | null; visibility: 'PUBLIC' | 'HIDDEN'; score: number | null; comparisonMode: 'EXACT' | 'TOKENS' }
export interface TeacherProblem extends Problem { referenceAnswer: string; judgeTemplate: string; testCases: TestCase[]; state: 'DRAFT' | 'PUBLISHED' }
export interface ProblemProgress { status: 'UNSUBMITTED' | 'PENDING' | 'SYSTEM_ERROR' | 'WRONG' | 'PARTIAL' | 'ACCEPTED'; bestScore: number | null; pending: boolean }
export type AssignmentProblem = Pick<Problem, 'id' | 'versionId' | 'title' | 'maxScore'> & { progress?: ProblemProgress | null };
export interface Assignment { selectionMode?:'FULL'|'SHARED_RANDOM'|'PER_STUDENT_RANDOM'; questionCount?:number; poolSize?:number; aiEnabled?:boolean; chatEnabled?:boolean; id: string; title: string; className: string; opensAt: string; closesAt: string; problems: AssignmentProblem[]; completedCount: number; score: number; maxScore: number; state: 'DRAFT' | 'PUBLISHED' }
export type TaskStatus = 'QUEUED' | 'COMPILING' | 'RUNNING' | 'FINISHED' | 'SYSTEM_ERROR';
export type Verdict = 'ACCEPTED' | 'PARTIAL_ACCEPTED' | 'WRONG_ANSWER' | 'PRESENTATION_ERROR' | 'COMPILATION_ERROR' | 'RUNTIME_ERROR' | 'TIME_LIMIT_EXCEEDED' | 'MEMORY_LIMIT_EXCEEDED' | 'OUTPUT_LIMIT_EXCEEDED' | 'SYSTEM_ERROR';
export interface SubmissionRequest { assignmentId: string; problemId: string; problemVersionId: string; sourceCode: string; language: Language }
export interface Submission { id: string; status: TaskStatus; submittedAt?: string; verdict: Verdict | null; score: number | null; maxScore: number; compileMessage: string; caseResults: { caseIndex: number; status: Verdict; score: number; timeMs: number; memoryKb: number }[]; sourceCode?: string | null }
export interface Finding { startLine: number | null; endLine: number | null; category: string; reason: string; hint: string }
export interface Analysis { id: string; status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'; codeHash: string; summary: string | null; findings: Finding[]; errorCode: string | null; providerMode?: 'MOCK' | 'SCHOOL' | 'DEEPSEEK'; promptVersion?: string; replyStyle?:AiReplyStyle }
export interface SourceLocation { startLine: number; endLine: number }
export interface ParseIssue { code: string; severity: 'INFO' | 'WARNING' | 'ERROR'; field: string; sourceLocation: SourceLocation; message: string }
export interface Candidate { candidateId: string; title: string; statement: string; inputDescription: string; outputDescription: string; samples: { key: string; input: string | null; output: string | null; explanation?: string | null }[]; referenceAnswer: string; privateNotes: string; unresolvedContent: string; testCases: TestCase[]; maxScore: number | null; timeLimitMs: number; memoryLimitMb: number; codeLimitBytes: number; sourceLocation: SourceLocation; issues: ParseIssue[]; needsReview: boolean; problemType: ProblemType; language: Language; functionSignature: string; starterCode: string; judgeTemplate: string }
export interface ImportJob { id: string; status: 'QUEUED' | 'PARSING' | 'READY' | 'FAILED' | 'COMMITTED'; parserVersion: string; candidates: Candidate[]; issues: ParseIssue[] }
export interface Classroom { id: string; name: string; memberCount: number }
export interface Member { id: string; name: string; studentNumber: string | null }
export interface ResultRow { studentId: string; name: string; completedCount: number; totalCount: number; score: number; maxScore: number }
export interface AssignmentDraft { libraryId?:string; selectionMode?:'FULL'|'SHARED_RANDOM'|'PER_STUDENT_RANDOM'; drawCount?:number; aiEnabled?:boolean; chatEnabled?:boolean; title: string; classId: string; problemIds: string[]; opensAt: string; closesAt: string }
export interface Api {
  profile(): Promise<Profile>; saveProfile(input: ProfileUpdate): Promise<Profile>; uploadAvatar(file: File): Promise<Profile>; removeAvatar(): Promise<Profile>;
  aiProvider(): Promise<{ providerMode: 'MOCK' | 'SCHOOL' | 'DEEPSEEK' | 'DISABLED'; configured: boolean }>;
  me(): Promise<Actor>; login(username: string, password: string): Promise<Actor>; logout(): Promise<void>;
  assignments(): Promise<Page<Assignment>>; assignment(id: string): Promise<Assignment>; problem(assignmentId: string, problemId: string): Promise<Problem>;
  submit(input: SubmissionRequest, key: string): Promise<{ id: string; status: TaskStatus; submittedAt: string }>;
  submission(id: string): Promise<Submission>; submissions(assignmentId: string, problemId: string): Promise<Page<Submission>>;
  analyse(input: Omit<SubmissionRequest, 'language'>): Promise<Pick<Analysis, 'id' | 'status' | 'codeHash'>>; analysis(id: string): Promise<Analysis>;
  teacherProblems(): Promise<Page<TeacherProblem>>; saveProblem(input: TeacherProblem): Promise<TeacherProblem>;
  parse(input: string | File): Promise<{ id: string; status: ImportJob['status'] }>; importJob(id: string): Promise<ImportJob>;
  commit(id: string, title: string, candidates: Candidate[], key: string): Promise<{ problemSetId: string; problemIds: string[] }>;
  classes(): Promise<Page<Classroom>>; members(id: string, page?: number): Promise<Page<Member>>; createClass(name: string): Promise<Classroom>; addMember(classId: string, studentId: string): Promise<void>;
  teacherAssignments(): Promise<Page<Assignment>>; publish(input: AssignmentDraft): Promise<Assignment>;
  results(id: string): Promise<Page<ResultRow>>;
}
