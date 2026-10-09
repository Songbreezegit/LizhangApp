// 仅验证本机服务，不用于浏览器渲染或公网验收。
import assert from 'node:assert/strict';
const port=Number(process.env.LIZHANG_PREVIEW_PORT||4173);
const base=`http://127.0.0.1:${port}`;
for (const route of ['/','/privacy/','/terms/','/help/','/faq/','/contact/','/assets/style.css','/assets/ledger.svg','/robots.txt','/missing/']) {
  const response=await fetch(base+route,{signal:AbortSignal.timeout(5000)});
  assert.equal(response.status,route==='/missing/'?404:200,route);
  assert.equal(response.headers.get('x-content-type-options'),'nosniff');
  assert.match(response.headers.get('content-security-policy'),/frame-ancestors 'none'/);
  if(route.endsWith('/') && route!=='/missing/') assert.match(await response.text(),/noindex,nofollow/);
  console.log(`${route} ${response.status}`);
}
assert.equal((await fetch(base,{method:'POST'})).status,405);
const head=await fetch(base+'/privacy/',{method:'HEAD'});
assert.equal(head.status,200);
assert.equal(await head.text(),'');
console.log('本机 HTTP 内容、响应头、HEAD 与方法限制检查通过。');
