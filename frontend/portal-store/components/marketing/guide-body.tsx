import ReactMarkdown from 'react-markdown'

export function GuideBody({ body }: { body: string }) {
  return (
    <div className="prose prose-sm max-w-none text-ink-soft">
      <ReactMarkdown>{body}</ReactMarkdown>
    </div>
  )
}
