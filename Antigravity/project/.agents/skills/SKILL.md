<!-- 
모든 스킬은 YAML 프런트매터(frontmatter)가 포함된 SKILL.md 파일이 필요

레벨 1: SKILL.md 메타데이터 (YAML frontmatter) 100 토큰 이내 작성 [항상 실행됨]
레벨 2: SKILL.md Body (Markdown) 5000 토큰 이내 작성 [스킬 트리거되면 실행]
레벨 3: 번들 파일 (text 파일, 스크립트, 데이터 ...) [LLM이 필요에 따라 실행]

name: 스킬의 고유 식별자(소문자, 공백은 하이픈 사용). 제공되지 않으면 폴더 이름을 기본값으로 사용 [필수X]
description: 스킬이 무엇을 하고 언제 사용하는지에 대한 명확한 설명. 에이전트가 스킬을 적용할지 결정할 때 보는 내용 [필수O]

.agent/skills/my-skill/
├─── SKILL.md       # 메인 지침 (필수)
├─── scripts/       # 헬퍼 스크립트 (선택 사항) [python, bash or node Scripts]
│   ├── run.py
│   └── util.sh
├─── references/    # 문서 or 템플릿 (선택 사항)
├─── assets/        # 정적 자산 (선택 사항) [이미지, 로고 등...]
├─── examples/      # 참조 구현 (선택 사항)
└─── resources/     # 템플릿 및 기타 자산 (선택 사항)

YAML 프런트매터(frontmatter): 에이전트가 스킬을 검색하기 위한 인덱스 (사용자 요청에 따라 description을 읽고 판단)
scripts: LLM이 직겁 수행하기 어려운 복잡한 연산이나 시스템 호출을 스크립트로 실행
--> 

---
name: my-skill
description: 특정 작업 도움. X나 Y를 해야 할 때 사용
---

# 나의 스킬 (My Skill)

에이전트를 위한 상세 지침

## 언제 이 스킬을 사용하는가

- ...할 때 사용
- 이것은 ...에 유용

## 사용 방법

에이전트가 따라야 할 단계별 안내, 컨벤션, 패턴.
