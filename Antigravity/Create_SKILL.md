<div align="center">

# **SKILLS**

`AI 어시스턴트에게 특정 작업을 처리하는 방법을 가르치는 전문 지식 모듈`

[폴더 구조](#폴더-구조) • [SKILL.md](#SKILLmd) • [번들 파일](#번들-파일) • [문서 포메팅](#문서-포메팅)

</div>

---

`모든 스킬은 YAML 프런트매터(frontmatter)가 포함된 SKILL.md 파일이 필요`

```
https://agentskills.io

https://github.com/forrestchang/andrej-karpathy-skills
https://github.com/sickn33/antigravity-awesome-skills
```

- 레벨 1: SKILL.md 메타데이터 (YAML frontmatter) 100 토큰 이내 작성 [항상 실행됨]
  - YAML 프런트매터(frontmatter): 에이전트가 스킬을 검색하기 위한 인덱스 (사용자 요청에 따라 description을 읽고 판단)
- 레벨 2: SKILL.md Body (Markdown) 5000 토큰 이내 작성 [스킬 트리거되면 실행]
- 레벨 3: 번들 파일 (text 파일, 스크립트, 데이터 ...) [LLM이 필요에 따라 실행]

## 폴더 구조

```
.agent/skills/my-skill/
├─── SKILL.md       # 메인 지침 (필수)
├─── scripts/       # 헬퍼 스크립트 (선택 사항) [python, bash or node Scripts]
│   ├─── run.py
│   └─── util.sh
├─── templates/     # 코드 템플릿 (선택 사항)
├─── references/    # 참조 문서 (선택 사항)
├─── assets/        # 정적 자산 (선택 사항) [이미지, 로고 등...]
├─── examples/      # 참조 구현 (선택 사항)
└─── resources/     # 템플릿 및 기타 자산 (선택 사항)
```

## SKILL.md

```
1. YAML 프런트매터(frontmatter)
 - 100 토큰 이내 작성 [항상 실행됨]
 - name: 스킬의 고유 식별자(소문자, 공백은 하이픈 사용). 제공되지 않으면 폴더 이름을 기본값으로 사용 [필수X]
 - description: 스킬이 무엇을 하고 언제 사용하는지에 대한 명확한 설명. 에이전트가 스킬을 적용할지 결정할 때 보는 내용 [5000 토큰 이내, 작성 스킬 트리거되면 실행, 필수O]
 - argument-hint: 실행하기 전에 사용자가 입력해야할 정보 내용 정의 [필수X]
 - risk: 이 스킬을 실행할 때 얼마나 주의해야 하는지를 사용자에게 알림 [필수X]
 - source: 스킬의 출처 [필수X]
 - tags: 카테고리 분류(스킬을 검색할 때 사용하는 키워드) [필수X]
 - date_added: 생성/등록 날짜 [필수X]
 - disable-model-invocation: LLM(모델)의 자의적인 판단 (true: 금지, false: 허용(디폴트)) [필수X]
 - priority: 스킬 호출 우선순위 [필수X]
 - triggers: 사용자의 입력 문구 중 이 스킬을 활성화할 키워드 목록 [필수X]
 - executable: 스킬이 활성화되었을 때 실제로 실행될 파일 경로 [필수X]
 - tech_stack: 프로젝트의 기술적 명세 [필수X]
 - rules: 코드 작성 시 반드시 지켜야 할 규칙 [필수X]
 - rules_path: 외부 규칙 파일의 경로 [필수X]

---
name: my-skill
description: 특정 작업 도움. X나 Y를 해야 할 때 사용
argument-hint: "[선택 사항: 집중할 영역]"
risk: "safe" # none | safe | critical | offensive
source: "community"
tags: ["java", "typescript"]
date_added: "2000-00-00"
disable-model-invocation: true
priority: 10
triggers:
  - "신규 API 엔드포인트 추가"
executable: "./scripts/<파일이름>.py"
tech_stack:
  java_version: 21
rules:
  - "모든 DTO는 Java Record를 사용함"
  - "예외 처리는 RFC 9457(Problem Details) 표준을 준수함"
rules_path: "./rules/convention.md"
---

2. SKILL.md Body (Markdown)
 - 5000 토큰 이내 작성 [스킬 트리거되면 실행]
 - Skill Title: 명확하고 설명적인 제목
 - Overview: 스킬의 존재 이유와 목적 (2~4문장)
 - When to Use This Skill: AI가 이 기술을 언제 활성화해야 하는지 도움
 - How It Works: 스킬의 핵심인 실행 단계와 구체적인 조치 사항 (핵심 지침)
 - Examples: AI에게 좋은 결과물이 무엇인지 보여주는 코드 스니펫
 - Best Practices: 해야 할 것과 하지 말아야 할 것 목록 (권장 사항)
 - Common Pitfalls: 자주 발생하는 문제와 해결책 (주의 사항)
 - Related Skills: 함께 쓰면 좋거나 대신 쓸 수 있는 다른 스킬들

# Skill Title

에이전트를 위한 상세 지침

## Overview

- ...할 때 사용
- 이것은 ...에 유용

## When to Use This Skill

- 시나리오 별 설명

## How It Works

### Step 1: [Action]

세부 지시 사항...

### Step 2: [Action]

세부 지시 사항...

## Examples

## Best Practices

## Common Pitfalls

## Related Skills

```

## 번들 파일

### Scripts

`스킬 실행에 보조 스크립트가 필요한 경우`

### Examples

`스킬을 실질적으로 보여주는 실제 예제 (기초 사용법, 고급 패턴, 전체 구현체 등...)`

### Templates

`재사용 가능한 코드 템플릿`

### References

`외부 문서나 API 레퍼런스, Best Practices, 트러블슈팅 가이드 등...`

## 문서 포메팅

- 코드 블록: 반드시 언어를 명시 (ex. ```javascript)
- 리스트: 일관된 기호를 사용 (- 또는 *)
- 강조: 중요한 용어는 굵게(**), 강조는 기울임(*), 명령어나 코드는 `백틱()\을 사용
