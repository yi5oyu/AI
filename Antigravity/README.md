# Antigravity


## Agent

### Rules

#### [Global Rules](https://github.com/yi5oyu/AI/blob/main/Antigravity/.gemini/GEMINI.md)

모든 워크스페이스(프로젝트 단위)에 적용되는 전역 규칙

`~/.gemini/GEMINI.md`

#### [Workflows](https://github.com/yi5oyu/AI/tree/main/Antigravity/project/.agents/rules)

반복적인 특정 작업에 특화된 절차/메뉴얼 정의

`.agent/rules or /Workflows`

호출: `@<파일명>` or `/<파일명>.md`

### Skills 

에이전트가 할 수 있는 일을 확장하는 재사용 가능한 지식 패키지

`https://github.com/sickn33/antigravity-awesome-skills`

모든 워크스페이스(전역): `~/.gemini/antigravity/skills/<skill-folder>/`  
워크스페이스 전용: `<workspace-root>/.agent/skills/<skill-folder>/`

```
Skills 폴더 구조

.agent/skills/my-skill/
├─── SKILL.md       # 메인 지침 (필수)
├─── scripts/       # 헬퍼 스크립트 (선택 사항)
├─── examples/      # 참조 구현 (선택 사항)
└─── resources/     # 템플릿 및 기타 자산 (선택 사항)
```
