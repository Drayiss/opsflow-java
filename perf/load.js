import http from 'k6/http';
import {check,sleep} from 'k6';
import {Trend} from 'k6/metrics';
const summaryLatency=new Trend('summary_latency',true);
export const options={vus:Number(__ENV.VUS||50),duration:__ENV.DURATION||'30s',summaryTrendStats:['avg','med','p(95)','p(99)'],thresholds:{http_req_failed:['rate==0'],checks:['rate==1']}};
const base=__ENV.BASE_URL||'http://host.docker.internal:8080';
export function setup(){
  const token=http.post(`${__ENV.OIDC_URL||'http://host.docker.internal:8180'}/realms/opsflow/protocol/openid-connect/token`,{client_id:'opsflow-load',grant_type:'password',username:'alice',password:'demo-password',scope:'openid'});
  if(token.status!==200)throw new Error(`Token request failed: ${token.status}`);
  const access=token.json('access_token');
  // Equal warm-up for both modes, including JWT key loading, JIT, pool initialization and cache.
  for(let i=0;i<50;i++)http.get(`${base}/api/organizations/cccccccc-cccc-cccc-cccc-cccccccccccc/summary`,{headers:{Authorization:`Bearer ${access}`},tags:{phase:'warmup'}});
  return {access};
}
export default function({access}){
  const result=http.get(`${base}/api/organizations/cccccccc-cccc-cccc-cccc-cccccccccccc/summary`,{headers:{Authorization:`Bearer ${access}`},tags:{phase:'measured'}});
  summaryLatency.add(result.timings.duration);
  check(result,{'authorized summary returned':r=>r.status===200 && r.json('total')===200000});
  sleep(0.05);
}
export function handleSummary(data){return { [__ENV.RESULT_FILE||'/results/result.json']:JSON.stringify(data,null,2) };}
