import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

marked.setOptions({
  gfm: true,
  breaks: true,
});

@Pipe({
  name: 'safeMarkdown',
  standalone: true,
})
export class SafeMarkdownPipe implements PipeTransform {
  private readonly sanitizer = inject(DomSanitizer);

  transform(value: string | null | undefined): SafeHtml {
    const src = (value ?? '').trim();
    if (!src) {
      return this.sanitizer.bypassSecurityTrustHtml('');
    }
    const raw = marked.parse(src, { async: false }) as string;
    const clean = DOMPurify.sanitize(raw, {
      USE_PROFILES: { html: true },
      ADD_TAGS: ['table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'colgroup', 'col'],
      ADD_ATTR: ['align', 'colspan', 'rowspan'],
    });
    return this.sanitizer.bypassSecurityTrustHtml(clean);
  }
}
