@echo off
chcp 65001 >nul
cd /d "%~dp0"
where node >nul 2>&1
if errorlevel 1 (
    echo 未找到 Node.js。请先安装 Node.js 18 或以上，再双击本脚本。
    pause
    exit /b 1
)
echo 正在启动本机审核预览，请保持此窗口打开。
echo 等待出现“服务已启动”后，再打开下方显示的浏览器地址。
node scripts\preview.mjs %*
echo.
echo 预览服务已停止，或启动失败。请查看上方提示；此时不要把回环地址当作可用官网。
pause
