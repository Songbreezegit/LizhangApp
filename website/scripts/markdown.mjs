/** 仅支持文档使用的 Markdown 子集；所有源文本先转义，不执行原始 HTML。 */
export function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function safeLink(target) {
  // 相对站内路径只允许已存在的页面；外部链接只允许 HTTPS。
  if (/^\/(?:privacy|terms|help|contact)\/$/.test(target)) return target;
  if (/[\s\u0000-\u001f\u007f]/.test(target)) return null;
  try {
    const url = new URL(target);
    return url.protocol === 'https:' && !url.username && !url.password ? url.href : null;
  } catch {
    return null;
  }
}

function inline(source) {
  const token = /\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)/g;
  let html = '', offset = 0;
  for (const match of source.matchAll(token)) {
    html += escapeHtml(source.slice(offset, match.index));
    if (match[1] !== undefined) html += '<strong>' + escapeHtml(match[1]) + '</strong>';
    else if (match[2] !== undefined) html += '<code>' + escapeHtml(match[2]) + '</code>';
    else {
      const href = safeLink(match[4]);
      html += href
        ? '<a href="' + escapeHtml(href) + '" rel="noreferrer">' + escapeHtml(match[3]) + '</a>'
        : escapeHtml(match[3]);
    }
    offset = match.index + match[0].length;
  }
  return html + escapeHtml(source.slice(offset));
}

export function renderMarkdown(markdown) {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const output = [], contents = [];
  let paragraph = [], list = null, section = 0;
  const flushParagraph = () => {
    if (paragraph.length) output.push('<p>' + inline(paragraph.join(' ')) + '</p>');
    paragraph = [];
  };
  const closeList = () => {
    if (list) output.push('</' + list + '>');
    list = null;
  };
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) { flushParagraph(); closeList(); continue; }
    if (i === 0 && line.startsWith('# ')) continue;
    const heading = /^(#{1,3}) (.+)$/.exec(line);
    if (heading) {
      flushParagraph(); closeList();
      const level = Math.max(2, heading[1].length), id = 'section-' + (++section);
      output.push('<h' + level + ' id="' + id + '">' + inline(heading[2]) + '</h' + level + '>');
      if (level === 2) contents.push({ id, label: heading[2] });
      continue;
    }
    if (line.startsWith('|') && /^\|\s*:?-{3,}/.test((lines[i + 1] || '').trim())) {
      flushParagraph(); closeList();
      const cells = value => value.trim().replace(/^\||\|$/g, '').split('|').map(cell => cell.trim());
      const header = cells(line);
      output.push('<div class="table-wrap" tabindex="0" role="region" aria-label="文档信息表格"><table><thead><tr>' +
        header.map(cell => '<th scope="col">' + inline(cell) + '</th>').join('') +
        '</tr></thead><tbody>');
      i++;
      while ((lines[i + 1] || '').trim().startsWith('|')) {
        const row = cells(lines[++i]);
        if (row.length !== header.length) throw new Error('文档表格列数不一致，请修正文档源。');
        output.push('<tr>' + row.map(cell => '<td>' + inline(cell) + '</td>').join('') + '</tr>');
      }
      output.push('</tbody></table></div>');
      continue;
    }
    const item = /^(?:([-*]) |(\d+)\. )(.+)$/.exec(line);
    if (item) {
      flushParagraph();
      const type = item[1] ? 'ul' : 'ol';
      if (list !== type) { closeList(); list = type; output.push('<' + type + '>'); }
      output.push('<li>' + inline(item[3]) + '</li>');
      continue;
    }
    closeList();
    paragraph.push(line);
  }
  flushParagraph(); closeList();
  if (!output.length) throw new Error('离线文档为空，不能生成网站正文。');
  return { html: output.join('\n'), contents };
}
