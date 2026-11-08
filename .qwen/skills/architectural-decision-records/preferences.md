# ADR Preferences

preferred-style: custom

Project template lives in `openspec/adr/ADR-TEMPLATE.md` and is the single source for new ADRs.
Structure (Russian headings, no YAML frontmatter):

- H1 title: `{NNNN}. {короткое название решения}`
- Header fields: **Статус** (`Принято / Предложено / Отклонено / Заменено на ADR-NNNN`), **Дата**, **Автор(ы)**
- `## Контекст` — problem and constraints; do not duplicate `openspec/context/`, link file and section instead
- `## Решение` — imperative, no filler
- `## Альтернативы` — numbered list: **{Вариант}:** — почему не подошёл
- `## Последствия (Consequences)` — **Плюсы:** and **Минусы / Риски:** (incl. what to monitor/configure)

Rules: one decision per ADR, minimal text, known facts only, unknowns marked rather than invented;
a superseded ADR keeps its file, its Статус becomes `Заменено на ADR-NNNN`.
