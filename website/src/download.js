// 仅下载页加载；没有浏览器剪贴板能力时，校验值仍可直接选中复制。
const button = document.querySelector('[data-copy-checksum]');
const checksum = document.getElementById('apk-sha256');
const status = document.getElementById('checksum-status');

if (button && checksum && status && navigator.clipboard?.writeText) {
  button.disabled = false;
  button.addEventListener('click', async () => {
    button.disabled = true;
    try {
      await navigator.clipboard.writeText(checksum.textContent.trim());
      status.textContent = '校验值已复制';
    } catch {
      status.textContent = '请长按或选中校验值复制';
    } finally {
      button.disabled = false;
    }
  });
}
