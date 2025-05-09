# AI(Artificial Intelligence)

`인공지능. 인간의 지능을 모방하여 기계가 학습, 추론, 문제 해결, 의사 결정 등의 능력을 갖추도록 하는 기술`

## LLM을 사용한 답변 워크플로우

사용자 질문이 입력되면 RAG 시스텀은 벡터 DB에서 유사한 정보를 효율적으로 검색하고 이 정보를 LLM에게 전달해 답변 생성   
LLM이 최신 정보나 특정 도메인 지식을 반영한 정확하고 신뢰성 높은 답변을 생성하도록 함

### LLM(Large Language Model)

`언어를 이해하고 생성하는 AI`

방대한 양의 텍스트 데이터를 학습하여 다양한 종류의 텍스트를 생성할 수 있음

### RAG(Retrieval-Augmented Generation): 검색 증강 생성  

`학습한 데이터안에서 답변하는 LLM의 한계를 극복하기 위해 응답을 생성하기 외부 자료를 검색해 답변의 품질과 신뢰도를 높이는 AI 프레임워크`

검색 단계(Retrieval): 질문과 관련된 외부 데이터에서 정보 검색
프롬프트 증강(Augmentation): 검색된 정보를 LLM의 프롬프트에 추가해, LLM이 답변 생성 시 참고할 수 있도록 함
생성 단계(Generation): LLM이 기존의 학습 데이터와 새로 검색된 정보를 모두 활용해 정확하고 신뢰할 수 있는 답변 생성

### 벡터 DB(Vector Database)

`데이터를 고차원의 벡터(Vector) 형태로 저장하고 벡터들 간의 유사성을 빠르고 효율적인 검색에 특화된 데이터베이스`

벡터 임베딩(Vector Embedding): 텍스트, 이미지, 오디오 등 다양한 종류(사용자의 검색어나 질문(쿼리)도 벡터로 변환됨)의 데이터를 수치적인 표현(벡터)으로 변환할 수 있음(비슷한 의미를 가진 단어나 문장은 벡터 공간에서 서로 가까운 위치에 있게 됨)
벡터 저장 및 인덱싱: 벡터 간의 유사도를 빠르게 계산하고 검색할 수 있도록 특별한 인덱싱 기법(예: HNSW, IVF 등)을 사용
유사도 검색: 질문(쿼리) 벡터와 데이터베이스에 저장된 벡터들 간의 유사도(거리)를 계산해 가장 유사한 벡터(가장 관련성 높은 데이터)들을 찾아냄

## Agent

LLM을 이용해 조금 더 능동적이고 자율적인 역할을 수행하는 시스템(목표 달성을 위해 외부 도구와 상호작용하며 실제 행동 수행)   
주어진 목표를 달성하기 위해 환경 인식, 스스로 판단(추론), 계획을 세우고, 필요한 도구(Tool)를 사용, 행동(Action)을 자율적으로 수행하는 인공지능 시스템

`작동 방식`  

인식(Perception): 사용자의 입력, 외부 데이터, API 응답 등을 통해 환경 정보를 받아들여 현재 상황 파악함   
계획(Planning): 목표를 달성하기 위한 단계적인 실행 계획(복잡한 목표는 여러 개의 작은 하위 목표로 나누어 순차적 또는 병렬적으로 처리)을 수립함   
행동/도구 사용(Action/Tool): 계획에 따라 실제 행동 실행/다양한 도구 사용(웹 검색, 코드 실행, 데이터베이스 접근, API 호출)   
메모리(Memory): 작업 수행 중 필요한 정보(단기 기억)나 과거의 경험/지식(장기 기억)을 저장하고 활용(RAG, 벡터 DB가 장기 기억을 저장하고 검색하는 데 사용될 수 있음)   

> LLM: 사용자의 지시(목표)를 이해하고 작업을 하위 단계로 분해하며 어떤 도구나 정보가 필요한지 추론하고 전체적인 계획을 세우는 역할
> 자율성(Autonomy): 사람의 개입을 최소화하면서 목표 달성을 위해 스스로 판단하고 작업을 진행하는 능력을 가짐

Agent SDK

`AI 에이전트를 빠르고 유연하게 만들 수 있는 프레임워크/라이브러리`

LangChain, OpenAI Function Calling, LlamaIndex, CrewAI, AutoGen...

Tool

`LLM이나 Agent가 외부와 상호작용, 특정 기능 실행할 수 있도록 연결된 함수, API, 플러그인, 데이터베이스 쿼리 등을 의미`

AI 에이전트가 자신의 목표를 달성하기 위해 사용할 수 있는 외부의 특정 기능, 서비스, 정보   
에이전트 개발 시 함수(Function)나 클래스(Class) 형태로 구현

웹 검색 (Web Search), 코드 실행기 (Code Executor), 계산기 (Calculator), API 호출 (API Caller), 파일 시스템 접근 (File System Access)...

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
Tools: 코드 실행이나 특정 기능 수행과 같이 서버가 제공하는 기능(MCP 서버는 사용 가능한 도구 목록, 각 도구의 이름, 설명 및 입력 스키마를 클라이언트에 제공)
Resources: LLM의 컨텍스트에 로드할 수 있는 데이터(데이터를 제공하는 데 사용)
Prompts: LLM과의 효과적인 상호 작용을 위한 재사용 가능한 템플릿

### JSON-RPC(JavaScript Object Notation-Remote Procedure Call)

`JSON 형식을 사용하여 원격 프로시저 호출(RPC) 프로토콜`

클라이언트와 서버 간의 요청(Request), 응답(Response), 알림(Notification) 메시지를 표준화된 JSON 형식으로 교환할 수 있음

> 원격 프로시저 호출(RPC): 네트워크로 연결된 다른 컴퓨터(다른 주소)에 있는 함수를 마치 자신의 프로그램 안에 있는 로컬 함수처럼 호출할 수 있게 해주는 기술

### 전송(Transport) 처리 방식

`Stdio(Standard Input/Output)`

클라이언트와 서버가 동일한 시스템 내의 로컬 프로세스로 실행될 때 주로 사용    

`HTTP with SSE (Server-Sent Events)`

클라이언트 > 서버 요청: HTTP POST 요청, 서버 > 클라이언트 메시지(응답/알림): SSE를 통해 지속적인 연결을 유지하며 전송(양방향 통신과 유사한 패턴을 구현할 수 있음)   

## 통신 예제 

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



