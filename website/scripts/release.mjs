// 官网的发行信息只来自 site.config.json；页面不另存版本、下载地址或校验值。
export function loadRelease(value) {
  if (!value || typeof value !== 'object' || !/^\d+\.\d+\.\d+$/.test(value.versionName) ||
      !Number.isSafeInteger(value.versionCode) || value.versionCode < 1 ||
      !Number.isSafeInteger(value.sizeBytes) || value.sizeBytes < 1 ||
      !/^[a-f0-9]{64}$/.test(value.sha256) || !/^\d+\.\d+$/.test(value.minAndroidVersion)) {
    throw new Error('发行资料缺少有效的稳定版版本、文件大小、摘要或 Android 要求。');
  }
  const repository = 'https://github.com/Songbreezegit/LizhangApp';
  const tag = 'v' + value.versionName;
  const filename = `LiZhang-${tag}-${value.versionCode}-release.apk`;
  if (value.apkFilename !== filename ||
      value.apkUrl !== `${repository}/releases/download/${tag}/${filename}` ||
      value.releaseUrl !== `${repository}/releases/tag/${tag}` ||
      value.latestReleaseUrl !== `${repository}/releases/latest` ||
      value.checksumUrl !== `${repository}/releases/download/${tag}/SHA256SUMS.txt`) {
    throw new Error('发行版本、正式 APK 文件名和 GitHub 发行地址不一致。');
  }
  return Object.freeze({ ...value, sizeMiB: (value.sizeBytes / 1024 / 1024).toFixed(2) });
}
