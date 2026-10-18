import { createServer } from 'node:http';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const dist = resolve(dirname(fileURLToPath(import.meta.url)), '../dist');
if (!existsSync(resolve(dist, 'index.html'))) throw new Error('请先执行网站静态构建。');
const port = Number(process.argv[2] || 4173);
if (process.argv.length > 3 || !Number.isInteger(port) || port < 1024 || port > 65535) {
  throw new Error('只能指定一个 1024～65535 范围内的本机预览端口。');
}

// 仅提供生成目录中明确列出的公开页面和资产，不暴露文档源、凭据或任意磁盘文件。
const routes = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ...['privacy', 'terms', 'help', 'contact'].map(page => ['/' + page + '/', [page + '/index.html', 'text/html; charset=utf-8']]),
  ['/assets/style.css', ['assets/style.css', 'text/css; charset=utf-8']],
  ['/assets/logo-cat.png', ['assets/logo-cat.png', 'image/png']],
  ['/assets/home-cat.png', ['assets/home-cat.png', 'image/png']],
  ['/robots.txt', ['robots.txt', 'text/plain; charset=utf-8']]
]);
const server = createServer((request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.setHeader('Referrer-Policy', 'no-referrer');
  response.setHeader('Content-Security-Policy', "default-src 'none'; img-src 'self'; style-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    response.writeHead(405, { Allow: 'GET, HEAD' }); response.end(); return;
  }
  let path;
  try { path = new URL(request.url, 'http://127.0.0.1').pathname; }
  catch { response.writeHead(400); response.end(); return; }
  if (/^\/(?:privacy|terms|help|contact)(?:\/index\.html)?$/.test(path)) {
    const target = '/' + path.split('/')[1] + '/';
    response.writeHead(308, { Location: target }); response.end(); return;
  }
  if (path === '/index.html') path = '/';
  const entry = routes.get(path);
  response.statusCode = entry ? 200 : 404;
  if (!entry) response.setHeader('X-Robots-Tag', 'noindex, nofollow');
  response.setHeader('Content-Type', entry ? entry[1] : 'text/html; charset=utf-8');
  try {
    const data = readFileSync(resolve(dist, entry ? entry[0] : '404.html'));
    response.setHeader('Content-Length', data.length);
    response.end(request.method === 'HEAD' ? undefined : data);
  } catch {
    response.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('预览文件无法读取，请重新构建。');
  }
});
server.on('error', error => {
  console.error(error.code === 'EADDRINUSE' ? '预览端口已被占用，请换一个端口。' : '本机预览启动失败：' + error.message);
  console.error('服务未成功启动；请处理上述错误后再打开浏览器。');
  process.exitCode = 1;
});
server.listen(port, '127.0.0.1', () => {
  console.log('服务已启动。请在这台 Windows 电脑的浏览器打开：http://127.0.0.1:' + port + '/');
  console.log('仅监听本机回环地址，不记录访问日志。保持此窗口打开，按 Ctrl+C 停止。');
  console.log('这不是公网官网，也不代表浏览器或 HTTP 验收通过。');
});
