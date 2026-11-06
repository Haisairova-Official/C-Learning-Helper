import type { Api } from './types';
import { realApi } from './real';
export const isMock = import.meta.env.DEV && import.meta.env.VITE_USE_MOCK === 'true';
const service = isMock ? import('./mock').then(m => m.mockApi) : Promise.resolve(realApi);
// The mock module is eliminated from production builds by Vite's DEV constant.
export const api: Api = {
  profile: () => service.then(s => s.profile()), saveProfile: input => service.then(s => s.saveProfile(input)),
  uploadAvatar: file => service.then(s => s.uploadAvatar(file)), removeAvatar: () => service.then(s => s.removeAvatar()),
  aiProvider: () => service.then(s => s.aiProvider()),
  me: () => service.then(s => s.me()), login: (u, p) => service.then(s => s.login(u, p)), logout: () => service.then(s => s.logout()),
  assignments: () => service.then(s => s.assignments()), problem: (a, p) => service.then(s => s.problem(a, p)),
  assignment: id => service.then(s => s.assignment(id)),
  submit: (i, k) => service.then(s => s.submit(i, k)), submission: id => service.then(s => s.submission(id)), submissions: (a, p) => service.then(s => s.submissions(a, p)),
  analyse: i => service.then(s => s.analyse(i)), analysis: id => service.then(s => s.analysis(id)),
  teacherProblems: () => service.then(s => s.teacherProblems()), saveProblem: i => service.then(s => s.saveProblem(i)),
  parse: i => service.then(s => s.parse(i)), importJob: id => service.then(s => s.importJob(id)), commit: (id, t, c, k) => service.then(s => s.commit(id, t, c, k)),
  createClass: name => service.then(s => s.createClass(name)), addMember: (c, u) => service.then(s => s.addMember(c, u)),
  classes: () => service.then(s => s.classes()), members: (id, page) => service.then(s => s.members(id, page)), teacherAssignments: () => service.then(s => s.teacherAssignments()), publish: i => service.then(s => s.publish(i)), results: id => service.then(s => s.results(id)),
};
