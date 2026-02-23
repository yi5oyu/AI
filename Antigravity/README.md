# Antigravity

`환경설정`

- 플러그인
  - Antigravity Quota (AGQ)
- 크롬 Extension
  - Antigravity Browser Extension

```
.gemini/
├── GEMINI.md                            # 에이전트 전역 규칙
├── 📁 antigravity
│   ├── 📁 skills                        # 스킬
│   └── 📁 ...
├── 📁 antigravity-browser-profile
│   └── 📁 ...                    
└── 📁 rules                             # 세부 지침
    └── code-style.md                    # 코딩 컨벤션 관련 파일 관리  

project(root)/                           # 프로젝트 루트
├── GEMINI.md                            # 프로젝트 기술 스택/핵심 규칙
├── HANDOFF.md                           # 현재 작업 상태/세션 간 인수인계서
├── .agents/                                
│   ├── 📁 rules/                        # 프로젝트 규칙
│   |   └── ....md             
│   └── 📁 workflows/                    # 워크플로우 규칙
│       └── ....md 
└── 📁 docs/                             # 세부 참조 문서
    ├── prd.md                 
    └── git-settings.md        
```

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
2. 복잡한 문제나 코딩 작업을 작은 단위로 나누기(컨텍스트 오염 방지)

```
하나의 채팅 창(세션)에서 너무 많은 주제를 다루지 않기
작업 주제별로 채팅 탭을 완전히 분리
```

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

5. Git Worktree 활용

```
별도의 로컬 폴더를 가지는 브랜치를 생성해 기존 프로젝트 환경과 완전히 분리된 곳에서 안전하게 새로운 아키텍처나 라이브러리 도입을 테스트
```

6. 계획에 시간을 투자, 프로토타입은 빠르게 만들기

```
코드를 작성하기 전에 폴더 구조나 사용할 컴포넌트, 기술 스택을 에이전트와 먼저 논의
작동만 하는 조잡한 프로토타입을 빠르게 짜보게 한 뒤 구조를 확정
```

7. 자가 검증 유도

```
결과물을 곧바로 적용하지말고 AI 스스로 결과물을 의심하게 만듬
```

## Agent Manager
