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

## Tips

1. 컨텍스트와 프롬프트 관리(HANDOFF.md 사용)

```
지금까지의 아키텍처, 구현 진행 상황, 시도한 방법, 다음 단계를 HANDOFF.md 파일로 정리

새 세션에서 만들어진 HANDOFF.md 파일을 읽히면 작업을 이어갈 수 있음
```
2. 복잡한 문제나 코딩 작업을 작은 단위로 나누기

3. TDD 활용

```
k6 같은 부하 테스트 스크립트를 먼저 작성해 에이전트가 스스로 테스트를 돌려가며 병목 구간을 찾고 고치도록 유도
```

4. 복잡해진 코드 단순화와 통제권 유지

```
불필요한 추상화나 과도한 로직을 추가하여 코드를 오버엔지니어링하는 경향이 있음.    
코드가 정상적으로 작동하더라도 로직을 확인하고 심플하고 명확하게 리팩토링 요구(코드의 통제권을 유지)    
트랜잭션 처리나 동시성 제어처럼 시스템의 핵심적인 부분은 코드   
```

5. 
