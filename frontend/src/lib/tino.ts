import type { AiReplyStyle } from '../api/types';
export const tinoSequence = '12332112323';
const digits:Partial<Record<AiReplyStyle,string>> = {CONCISE:'1',GUIDED:'2',DETAILED:'3'};
export function advanceTinoSequence(previous:string,style:AiReplyStyle):string {
  const digit=digits[style];
  if(!digit)return '';
  const candidate=(previous+digit).slice(-tinoSequence.length);
  for(let length=candidate.length;length>0;length--){
    const suffix=candidate.slice(-length);
    if(tinoSequence.startsWith(suffix))return suffix;
  }
  return '';
}
export const tinoStyle={value:'TINO',title:'小侦探Tino',description:'和小侦探一起发现线索，认真核对代码中的问题。'} as const;
