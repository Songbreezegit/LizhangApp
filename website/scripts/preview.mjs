import http from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { root } from './build.mjs';
const dist = path.join(root,'dist');
const port = Number(process.env.LIZHANG_PREVIEW_PORT || 4173);
const types = {'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.json':'application/json; charset=utf-8','.txt':'text/plain; charset=utf-8'};
const server=http.createServer(async (req,res) => {
  res.setHeader('X-Content-Type-Options','nosniff');
  res.setHeader('Referrer-Policy','no-referrer');
  res.setHeader('Content-Security-Policy',"default-src 'none'; style-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
  res.setHeader('Cache-Control','no-store');
  if (!['GET','HEAD'].includes(req.method)) {res.writeHead(405,{Allow:'GET, HEAD'});res.end();return;}
  let file;
  try {
    const pathname = decodeURIComponent(new URL(req.url,'http://localhost').pathname);
    file = path.resolve(dist,'.'+pathname);
    if (file !== dist && !file.startsWith(dist+path.sep)) {res.writeHead(403);res.end();return;}
    if ((await stat(file)).isDirectory()) file=path.join(file,'index.html');
    const body=await readFile(file);
    res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream'});
    res.end(req.method==='HEAD'?undefined:body);
  } catch {res.writeHead(404,{'Content-Type':'text/html; charset=utf-8'});res.end(req.method==='HEAD'?undefined:await readFile(path.join(dist,'404.html')));}
}).listen(port,'127.0.0.1',() => console.log(`礼账本地预览：http://127.0.0.1:${port}（仅本机访问）`));
// 保持工具会话可控；Ctrl+C 停止本次预览。
process.stdin.resume();
process.stdin.on('data',(data)=>{if(data.includes(3)) server.close(()=>process.exit(0));});
