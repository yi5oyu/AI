<div align="center">

# **AI(Artificial Intelligence)**

`인공지능. 인간의 지능을 모방하여 기계가 학습, 추론, 문제 해결, 의사 결정 등의 능력을 갖추도록 하는 기술`

[AI Agentic 발전](#AI-Agentic-발전) • [LLM](#llmlarge-language-model) • [Agent](#agent) • [MCP](#mcpmodel-context-protocol) • [Skills](#skills) • [Antigravity](#antigravity)

</div>

## AI Agentic 발전

`AI 개발 패러다임 확장: Prompt(명령어) -> Context(정보 추가) -> Harness(시스템 설계)`

> https://bits-bytes-nn.github.io/insights/agentic-ai/2026/04/05/evolution-of-ai-agentic-patterns.html

### Prompt Engineering 

`AI 모델에게 어떤 말을 해야 하는가?`

1. CoT (Chain-of-Thought)

- "단계별로 생각" 문장 하나로 모델이 중간 추론 과정을 스스로 출력하게 만들어 논리·수학적 연산 성능 증가

> 프롬프트 한 줄 (Let's think step by step)로 산술·상식·상징적 추론 성능 향상            
> GSM8K(초등학교 수학) 벤치마크에서 PaLM 540B의 정확도가 17.9%에서 58.1%로 상승        

2. ReAct (Reasoning + Acting)

- 생각(Thought) -> 행동(Action) -> 관찰(Observation) 루프 구축해 모델이 외부 도구를 사용하는 패턴

> 외부 도구 사용해 환각 감소        
> 추론 과정 추적할 수 있음      

3. Self-Refine / Reflexion

- 인간이 글을 고쳐 쓰듯 모델이 자신의 출력을 스스로 비판하고 개선하도록 유도하는 자기 반성 패턴

> 피드백의 품질이 모델 자신의 능력에 종속되 한계있음         
> 다른 에이전트가 해야함  

### Context Engineering 

`컨텍스트 윈도우에 어떤 정보를 어떻게 넣을 것인가?`

> [!IMPORTANT]
> **프롬프트 엔지니어링의 한계**: 에이전트가 볼 수 있는 정보 불완전 (프롬프트가 소비하는 컨텍스트)      
> 정보를 어떻게, 어느 위치에, 얼마만큼의 크기로, 어떤 순서로 붙일지 설계    

1. Anthropic 4대 컨텍스트 전략

- 작성 (Write): 시스템 프롬프트를 명확하고 구조화된 형태로 작성
- 선택 (Select): 컨텍스트가 길어지면 중간에 있는 정보의 정확도가 급격히 떨어지는 문제를 막기 위해 전체 데이터 중 현재 질문에 꼭 필요한 정보만 골라 매핑
- 압축 (Compress): 이전 대화의 정보 보존율을 유지하며 요약문으로 토큰 사용량 축소
- 격리 (Isolate): 로그 분석 등 방대한 컨텍스트는 메인 창을 오염시키지 않도록 서브에이전트에게 격리하여 위임

2. 구글 ADK 컨텍스트 스택 아키텍처

- 저장과 표현 분리: 전체 히스토리(Session)는 원본 그대로 보관하고 매 턴마다 필요한 부분만 조립해 작업 컨텍스트(Working Context)로 생성
- 명시적 변환 파이프라인: "시스템 프롬프트 삽입 → 관련 히스토리 선별 → 도구 결과 요약 → 토큰 예산 확인"처럼 이름이 붙은 단계들이 정해진 순서로 실행 (재현 가능한 파이프라인으로 컨텍스트를 조립)
- 기본 스코핑: 서브에이전트에게 작업을 넘길 때 최소 권한 원칙(Least Privilege)을 적용해 필요한 파일과 문맥만 전달

3. KV-Cache 최적화

`컨텍스트 접두어의 토큰 하나만 바뀌어도 이후 전체 캐시가 무효화됨`

- 안정 접두어 (Stable Prefix): 시스템 프롬프트, 도구 정의 등 자주 변하지 않는 고정 정보를 컨텍스트 앞쪽에 배치
- 동적 접미어 (Variable Suffix): 최신 사용자 입력, 새로운 도구 출력 등 자주 바뀌는 정보를 뒤쪽에 배치

> 모델이 이전 계산을 재활용하는 비율(KV-cache hit rate) 극대화        
> 대규모 에이전트의 대기 시간(Latency) 문제를 해결    

### Harness Engineering

`AI 에이전트가 자율적으로 작동할 수 있는 통제·검증 환경을 어떻게 만들 것인가?`

> [!IMPORTANT]
> **컨텍스트 엔지니어링의 한계:** 아무리 컨텍스트를 정밀하게 제어해도 AI가 외부 도구(API, 파일 시스템 등)와 상호작용하는 순간 발생할 수 있는 문제(위험한 명령어 실행, 출력 포맷 파괴, 돌발적인 탈선)를 차단할 수 없음      
> AI를 통제할 수 있는 환경(실행 환경, 구조적 검증, 테스트 자동화)을 구축하고 AI의 행동을 사전에 정의된 제약 조건으로 제한하는 시스템 제어 아키텍처

1. Anthropic 3-에이전트 아키텍처

- 계획가 (Planner): 모호한 요구사항을 구체적인 제품 스펙으로 확장 (기술적 지시를 최소화. 에러 전이 방지)
- 생성가 (Generator): 한 번에 하나의 기능씩 구현하고 실행 후 컨텍스트를 주기적으로 리셋
- 평가가 (Evaluator): 브라우저 자동화 도구(Playwright) 등을 이용해 실제 E2E 테스트를 수행하고 기준 미달 시 생성가에게 반려

> 단독 실행 대비 인프라 비용 증가하지만 사전에 검증함으로써 품질 확보

2. OpenAI Codex 실전 시스템화 원칙

- 지식의 자산화: 에이전트가 보지 못하는 암묵적 규칙이나 지침을 마크다운 문서로 자산화해 제공
- 기계적 강제성: 커스텀 린터(Linter)와 구조적 테스트 빌드를 배치해 통과해야만 다음 단계로 넘어가게 통제
- 점진적 공개: 대량의 매뉴얼을 한 번에 주입하지 않고 전체 지도(Map)만 준 뒤 필요한 정보만 에이전트가 스스로 찾아보게 유도

3. Ralph 패턴

`PRD(제품 요구사항 문서)를 정의하고 AI 코딩 도구(Claude Code, Amp 등)를 루프 안에서 반복 실행해 PRD의 모든 항목이 완료될 때까지 실행`

- 컨텍스트 클린 리셋: 매 회차(Iteration) 루프가 돌 때마다 에이전트의 컨텍스트 윈도우를 완전히 초기화해 누적 오염 차단
- 외부 상태 저장소: 에이전트의 현재 작업 위치와 기억은 모델 내부가 아니라 파일 시스템(progress.txt, prd.json)과 Git 히스토리에 기록해 관리
- 연산적 피드백: 컴파일러 결과, 린트 에러 등 컴퓨터 시스템이 주는 명확한 피드백을 기반으로 인간 개입 없이 100% 자율 작동 환경 구현

---

사용자 질문이 입력되면 RAG 시스템은 벡터 DB에서 유사한 정보를 효율적으로 검색하고 이 정보를 LLM에게 전달해 답변 생성   
LLM이 최신 정보나 특정 도메인 지식을 반영한 정확하고 신뢰성 높은 답변을 생성하도록 함  

## LLM(Large Language Model)

`언어를 이해하고 생성하는 AI`

방대한 양의 텍스트 데이터를 학습하여 다양한 종류의 텍스트를 생성할 수 있음  

### RAG(Retrieval-Augmented Generation): 검색 증강 생성  

`학습한 데이터안에서 답변하는 LLM의 한계를 극복하기 위해 응답을 생성하기 외부 자료를 검색해 답변의 품질과 신뢰도를 높이는 AI 프레임워크`

- 검색 단계(Retrieval): 질문과 관련된 외부 데이터에서 정보 검색    
- 프롬프트 증강(Augmentation): 검색된 정보를 LLM의 프롬프트에 추가해, LLM이 답변 생성 시 참고할 수 있도록 함  
- 생성 단계(Generation): LLM이 기존의 학습 데이터와 새로 검색된 정보를 모두 활용해 정확하고 신뢰할 수 있는 답변 생성  

### 벡터 DB(Vector Database)

`데이터를 고차원의 벡터(Vector) 형태로 저장하고 벡터들 간의 유사성을 빠르고 효율적인 검색에 특화된 데이터베이스`

- 벡터 임베딩(Vector Embedding): 텍스트, 이미지, 오디오 등 다양한 종류(사용자의 검색어나 질문(쿼리)도 벡터로 변환됨)의 데이터를 수치적인 표현(벡터)으로 변환할 수 있음(비슷한 의미를 가진 단어나 문장은 벡터 공간에서 서로 가까운 위치에 있게 됨)    
- 벡터 저장 및 인덱싱: 벡터 간의 유사도를 빠르게 계산하고 검색할 수 있도록 특별한 인덱싱 기법(예: HNSW, IVF 등)을 사용    
- 유사도 검색: 질문(쿼리) 벡터와 데이터베이스에 저장된 벡터들 간의 유사도(거리)를 계산해 가장 유사한 벡터(가장 관련성 높은 데이터)들을 찾아냄    

## Agent

`자율형 AI 시스템`

LLM을 이용해 조금 더 능동적이고 자율적인 역할을 수행하는 시스템(목표 달성을 위해 외부 도구와 상호작용하며 실제 행동 수행)   
주어진 목표를 달성하기 위해 환경 인식, 스스로 판단(추론), 계획을 세우고, 필요한 도구(Tool)를 사용, 행동(Action)을 자율적으로 수행하는 인공지능 시스템  

### 작동 방식  

- 인식(Perception): 사용자의 입력, 외부 데이터, API 응답 등을 통해 환경 정보를 받아들여 현재 상황 파악함   
- 계획(Planning): 목표를 달성하기 위한 단계적인 실행 계획(복잡한 목표는 여러 개의 작은 하위 목표로 나누어 순차적 또는 병렬적으로 처리)을 수립함   
- 행동/도구 사용(Action/Tool): 계획에 따라 실제 행동 실행/다양한 도구 사용(웹 검색, 코드 실행, 데이터베이스 접근, API 호출)   
- 메모리(Memory): 작업 수행 중 필요한 정보(단기 기억)나 과거의 경험/지식(장기 기억)을 저장하고 활용(RAG, 벡터 DB가 장기 기억을 저장하고 검색하는 데 사용될 수 있음)   

> LLM: 사용자의 지시(목표)를 이해하고 작업을 하위 단계로 분해하며 어떤 도구나 정보가 필요한지 추론하고 전체적인 계획을 세우는 역할  
> 자율성(Autonomy): 사람의 개입을 최소화하면서 목표 달성을 위해 스스로 판단하고 작업을 진행하는 능력을 가짐  

### Agent SDK

`AI 에이전트를 빠르고 유연하게 만들 수 있는 프레임워크/라이브러리`

> LangChain, OpenAI Function Calling, LlamaIndex, CrewAI, AutoGen...

### Tool

`LLM이나 Agent가 외부와 상호작용, 특정 기능 실행할 수 있도록 연결된 함수, API, 플러그인, 데이터베이스 쿼리 등을 의미`

AI 에이전트가 자신의 목표를 달성하기 위해 사용할 수 있는 외부의 특정 기능, 서비스, 정보   
에이전트 개발 시 함수(Function)나 클래스(Class) 형태로 구현  

> 웹 검색 (Web Search), 코드 실행기 (Code Executor), 계산기 (Calculator), API 호출 (API Caller), 파일 시스템 접근 (File System Access)...

## [MCP(Model Context Protocol)](https://modelcontextprotocol.io/introduction)

`LLM 어플리케이션과 외부 데이터 소스 및 도구 간의 상호작용을 위한 개방형 표준 프로토콜`

개발자/서비스마다 다른 LLM의 컨텍스트 관리 방식을 표준화하고 다양한 컨텍스트 정보를 유연하게 다룰 수 있음(상호운용성↑, 복잡성↓)     
기존에 각 AI 애플리케이션과 외부 시스템을 연결하기 위해 개별적인 맞춤형 통합 코드를 개발해야 했기 때문에 발생한 문제(개발 복잡성 증가, 유지보수 어려움, 확장성 제한 등) 해결을 위해 등장     

> 클라이언트-서버(Client-Server) 아키텍처  
> AI 애플리케이션(host)이 하나 이상의 MCP 서버에 연결하여 외부 리소스와 상호작용할 수 있도록 구성  
> MCP Host - MCP Client - MCP Server  

### MCP Host

`전체 프로세스를 관리하고 연결을 조율하는 컨테이너`   

사용자가 실제로 조작하는 어플리케이션(Cursor, Claude 데스크탑 앱, VS Code...)   
UI, LLM 연결, MCP 서버와 직접 통신하는 클라이언트를 만들고 관리   

### MCP Client

`LLM을 대신하여 MCP 서버에 요청을 보내고 응답을 받아 LLM에게 전달하는 역할을 수행`

호스트 애플리케이션 내에서 실행되며 특정 MCP 서버와 1:1 연결 설정/관리 역할 수행    
MCP 서버가 제공하는 도구(Tools)목록 요청/도구 호출, 리소스(Resources) 접근, 프롬프트(Prompts) 기능 파악 등의 역할 수행   


`Stdio`: 명령줄 도구나 직접 통합을 위한 표준 입력/출력 기반 전송 방식(로컬에서 실행되는 서버 통신)   
`SSE(Server-Sent Events)`: 서버가 클라이언트에 실시간으로 데이터를 자동으로 푸시하여 업데이트를 보낼 수 있게 하는 표준 웹 기술(단방향 통신)

### MCP Server

`외부 데이터, 기능, LLM 상호작용을 위한 프롬프트를 MCP 클라이언트에 제공`

도구 (Tools), 리소스 (Resources) 및 프롬프트 (Prompts)와 같은 특정 기능을 표준화된 방식으로 LLM 애플리케이션에 노출하는 경량 프로그램

`주요 기능`

- Tools: 코드 실행이나 특정 기능 수행과 같이 서버가 제공하는 기능(MCP 서버는 사용 가능한 도구 목록, 각 도구의 이름, 설명 및 입력 스키마를 클라이언트에 제공)  
- Resources: LLM의 컨텍스트에 로드할 수 있는 데이터(데이터를 제공하는 데 사용)  
- Prompts: LLM과의 효과적인 상호 작용을 위한 재사용 가능한 템플릿  

### 통신 프로토콜

#### JSON-RPC(JavaScript Object Notation-Remote Procedure Call)

`JSON 형식을 사용하여 원격 프로시저 호출(RPC) 프로토콜`

클라이언트와 서버 간의 요청(Request), 응답(Response), 알림(Notification) 메시지를 표준화된 JSON 형식으로 교환할 수 있음  

> 원격 프로시저 호출(RPC): 네트워크로 연결된 다른 컴퓨터(다른 주소)에 있는 함수를 마치 자신의 프로그램 안에 있는 로컬 함수처럼 호출할 수 있게 해주는 기술  

#### 전송(Transport) 처리 방식

- Stdio(Standard Input/Output): 클라이언트와 서버가 동일한 시스템 내의 로컬 프로세스로 실행될 때 주로 사용    
- HTTP with SSE(Server-Sent Events)
  - 클라이언트 > 서버 요청: HTTP POST 요청 
  - 서버 > 클라이언트 메시지(응답/알림): SSE를 통해 지속적인 연결을 유지하며 전송(양방향 통신과 유사한 패턴을 구현할 수 있음)   

## [Skills](https://github.com/yi5oyu/AI/edit/main/Antigravity/Create_SKILL.md)

`AI 어시스턴트(에이전트)에게 특정 작업의 처리 방식을 가르치는 전문 지식 모듈`

### 실행 과정

- 레벨 1(항상 실행): SKILL.md의 YAML 프런트매터 (100 토큰 이내). 에이전트가 어떤 스킬이 존재하는지 검색하고 판단하기 위한 인덱스 역할
- 레벨 2(트리거 시 실행): SKILL.md의 Markdown 본문 (5000 토큰 이내). 사용자 요청에 의해 해당 스킬이 선택되면 실행되는 상세 지침
- 레벨 3(필요 시 실행): 스크립트, 템플릿 등 번들 파일. 본문 지침을 수행하는 과정에서 LLM이 판단하여 추가로 활용

### 폴더 구조

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

## [Antigravity](https://github.com/yi5oyu/AI/edit/main/Antigravity)

### 환경 설정

- 플러그인
   - Antigravity Quota(AGQ): 에이전트의 토큰 사용량 및 할당량 관리

- 브라우저 확장 프로그램(Chrome Extension)
   - Antigravity Browser Extension: 웹 기반 리소스/컨텍스트를 에이전트와 원활하게 연동

### 폴더 구조

```
# 전역 구조
~/.gemini/
├── GEMINI.md                            # 에이전트 전역 규칙
├── 📁 antigravity
│   ├── 📁 skills                        # 스킬
│   └── 📁 ...
├── 📁 antigravity-browser-profile
│   └── 📁 ...                    
└── 📁 rules                             # 세부 지침
    └── code-style.md                    # 코딩 컨벤션 관련 파일 관리  

# 프로젝트 구조
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



<!--

### 통신 예제 

1. UI(웹 브라우저): "서울 날씨 알려줘" 입력  
2. LLM 추론: LLM은 날씨 get_weather 툴을 {"city": "서울"} 파라미터로 호출해야 한다고 응답  
3. 툴 호출  
```json
{
  "jsonrpc": "2.0",
  "method": "get_weather",
  "params": { "city": "서울" },
  "id": "weather-req-001"
}
```
4. 툴 실행
```json
{
  "jsonrpc": "2.0",
  "result": { "condition": "맑음", "temperature": 15, "unit": "C" },
  "id": "weather-req-001"
}
```
5. 결과 처리/최종 응답 생성: 날씨 정보는 '맑음, 15°C' 임. 사용자에게 자연스러운 문장으로 답변 생성해줘 -> 최종 응답 텍스트 생성: 서울의 현재 날씨는 맑음이며, 기온은 15°C입니다. 
6. 최종 응답: UI와 연결된 SSE 커넥션(Content-Type: text/event-stream)을 통해 LLM이 생성한 답변을 작은 조각(청크)으로 나누어 스트리밍 전송.
```
# 첫 번째 조각
event: message
id: chunk-1
data: {"text": "서울의 "}

# 두 번째 조각
event: message
id: chunk-2
data: {"text": "현재 날씨는 "}
...
```
7. (UI)결과 표시

<img width="161" alt="{A54248C9-99B2-42FD-B4C7-2B9992027FB9}" src="https://github.com/user-attachments/assets/fc87df64-7beb-44ed-94d6-17c48476c9ad" />


<img width="500" alt="{D82532EB-1FE7-40D6-9EBD-D47F5F91D1F6}" src="https://github.com/user-attachments/assets/45408e6b-b23e-4c0c-97f0-6a0ae8de00c2" />


<img width="431" alt="{F7E684EC-9B08-4CF1-975E-46ACD32BAD2B}" src="https://github.com/user-attachments/assets/0f0ded15-031b-45d6-a302-ff472571c7ac" />
<img width="479" alt="{104298F9-BF7E-4B8F-9CBD-FA1C36BD329D}" src="https://github.com/user-attachments/assets/1a1e552f-027c-404d-a734-464a51ecef62" />
<img width="304" alt="{1902499D-1F60-4888-BB6F-325F0AA51B3B}" src="https://github.com/user-attachments/assets/3df56015-fb0f-4cb2-9b63-48ea8a57d8f2" />

-->


