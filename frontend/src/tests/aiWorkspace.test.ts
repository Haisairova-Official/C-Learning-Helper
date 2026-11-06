import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Analysis, Problem } from '../api/types';
const mocks=vi.hoisted(()=>({analyse:vi.fn(),analysis:vi.fn()}));
vi.mock('../api',()=>({api:mocks,isMock:false}));
import { analyseWorkspace, clearWorkspaceAi, workspaceAi } from '../aiWorkspace';
const problem={id:'p',versionId:'v'} as Problem;
const done:Analysis={id:'analysis',status:'COMPLETED',codeHash:'hash',summary:'检查边界。',findings:[],errorCode:null,replyStyle:'TINO'};
const deferred=<T>()=>{let resolve!:(value:T)=>void;const promise=new Promise<T>(r=>resolve=r);return{promise,resolve};};
beforeEach(()=>{clearWorkspaceAi();vi.clearAllMocks();workspaceAi.active=true;workspaceAi.ready=true;workspaceAi.enabled=true;});
describe('workspace AI lifecycle',()=>{
  it('blocks disabled, not-yet-loaded and empty-code requests',async()=>{
    workspaceAi.enabled=false;await analyseWorkspace('a',problem,'int x;','GUIDED');
    workspaceAi.enabled=true;workspaceAi.ready=false;await analyseWorkspace('a',problem,'int x;','GUIDED');
    workspaceAi.ready=true;await analyseWorkspace('a',problem,' ','GUIDED');
    expect(mocks.analyse).not.toHaveBeenCalled();
  });
  it('uses the server-recorded style and finishes the current request',async()=>{
    mocks.analyse.mockResolvedValue({id:'analysis',status:'RUNNING',codeHash:'hash'});mocks.analysis.mockResolvedValue(done);
    await analyseWorkspace('a',problem,'int x;','GUIDED');
    expect(workspaceAi.analysis).toEqual(done);expect(workspaceAi.style).toBe('TINO');expect(workspaceAi.busy).toBe(false);
  });
  it('cannot restore an old response after leaving the question or switching accounts',async()=>{
    const queued=deferred<{id:string;status:'RUNNING';codeHash:string}>();mocks.analyse.mockReturnValue(queued.promise);
    const pending=analyseWorkspace('a',problem,'int x;','TINO');clearWorkspaceAi();
    queued.resolve({id:'old',status:'RUNNING',codeHash:'hash'});await pending;
    expect(workspaceAi.analysis).toBeUndefined();expect(workspaceAi.active).toBe(false);expect(workspaceAi.style).toBe('GUIDED');expect(mocks.analysis).not.toHaveBeenCalled();
  });
  it('ignores a late poll without overwriting the next question state',async()=>{
    const result=deferred<Analysis>();mocks.analyse.mockResolvedValue({id:'old',status:'RUNNING',codeHash:'hash'});mocks.analysis.mockReturnValue(result.promise);
    const pending=analyseWorkspace('a',problem,'int x;','TINO');await vi.waitFor(()=>expect(mocks.analysis).toHaveBeenCalled());
    clearWorkspaceAi();workspaceAi.active=true;workspaceAi.ready=true;workspaceAi.enabled=true;workspaceAi.problemTitle='下一题';
    result.resolve(done);await pending;
    expect(workspaceAi.problemTitle).toBe('下一题');expect(workspaceAi.analysis).toBeUndefined();expect(workspaceAi.error).toBe('');
  });
});
