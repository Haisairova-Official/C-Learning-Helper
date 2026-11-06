import { describe,expect,it } from 'vitest';
import { advanceTinoSequence,tinoSequence } from '../lib/tino';
import { profileValidation } from '../lib/profile';
import type { AiReplyStyle } from '../api/types';
const style:Record<string,AiReplyStyle>={'1':'CONCISE','2':'GUIDED','3':'DETAILED'};
function feed(input:string){return [...input].reduce((previous,digit)=>advanceTinoSequence(previous,style[digit]!), '');}
describe('Tino unlock',()=>{
  it('requires the whole sequence, including repeated clicks on the same option',()=>{
    expect(feed('1233211232')).not.toBe(tinoSequence);
    expect(feed('12332112323')).toBe(tinoSequence);
    expect(feed('12321212323')).not.toBe(tinoSequence);
  });
  it('recovers after a mistaken click without merging separated fragments',()=>{
    expect(feed('23112332112323')).toBe(tinoSequence);
    expect(feed('12332123123')).not.toBe(tinoSequence);
  });
  it('only accepts the hidden style once the current account is unlocked',()=>{
    const input={nickname:'原昵称',bio:'原简介',aiReplyStyle:'TINO' as const};
    expect(profileValidation(input)).not.toBe('');
    expect(profileValidation(input,true)).toBe('');
  });
});
