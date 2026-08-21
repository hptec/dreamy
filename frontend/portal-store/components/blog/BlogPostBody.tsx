import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize'

/**
 * Blog 正文渲染共用组件（2026-08-20）
 * - react-markdown Server Component 渲染（SSR 友好、SEO 友好、无客户端 bundle）
 * - remark-gfm: 表格/删除线/任务列表
 * - rehype-sanitize: XSS 白名单（运营可能粘贴 HTML，必须消毒）
 * - 图片自动加 loading=lazy
 */
const sanitizeSchema = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    img: [...(defaultSchema.attributes?.img ?? []), ['loading'], ['alt'], ['className']],
    a: [...(defaultSchema.attributes?.a ?? []), ['target'], ['rel']],
  },
}

interface Props {
  content: string
}

export function BlogPostBody({ content }: Props) {
  return (
    <div className="prose-blog mt-8 text-lg leading-relaxed text-ink-soft">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeSanitize, sanitizeSchema]]}
        components={{
          img: (props) => (
            // eslint-disable-next-line @next/next/no-img-element
            <img {...props} loading="lazy" className="rounded-sm" alt={props.alt ?? ''} />
          ),
          a: (props) => (
            <a {...props} target={props.href?.startsWith('http') ? '_blank' : undefined} rel="noopener noreferrer" className="text-gold-deep underline" />
          ),
          h2: (props) => <h2 {...props} className="mt-10 font-display text-2xl font-medium text-ink" />,
          h3: (props) => <h3 {...props} className="mt-8 font-display text-xl font-medium text-ink" />,
          p: (props) => <p {...props} className="mt-5" />,
          blockquote: (props) => (
            <blockquote {...props} className="border-l-2 border-gold pl-4 italic text-ink-faint" />
          ),
          code: (props) => <code {...props} className="rounded bg-canvas px-1.5 py-0.5 text-sm" />,
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
