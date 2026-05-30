// 轻量展示型组件集合（单文件导出多个 SFC 太啰嗦，这里用 render 风格的简单组件）
// 实际复用组件以独立 .vue 文件提供，见同目录。该文件保留给将来共享纯函数。
export const toneClass = {
  ok: 'bg-ok/12 text-ok',
  warn: 'bg-warn/14 text-warn',
  danger: 'bg-danger/12 text-danger',
  info: 'bg-info/12 text-info',
  neutral: 'bg-ink/8 text-ink-soft'
}
