# 해외주식 절세 시나리오 비교 AX — Codex 인수인계

다른 노트북에서 저장소를 열고 작업을 이어갈 때 먼저 읽는 문서입니다. 상세한 계산 가정과 실행 방법은 루트 `README.md`를 기준으로 확인하세요.

## 프로젝트 소개

우리 WON 청년 IT 아카데미 해커톤용 React + Spring Boot 웹 프로토타입입니다. 가상 해외주식 계좌의 올해 손익을 바탕으로 양도소득세 절세 시나리오를 비교하고, 사용자가 장기 보유·매도 제외 등의 의사를 대화로 전달하면 확인된 조건으로 다시 계산합니다. 실제 계좌 접속, 주문, 신고 기능은 없습니다.

## 현재 사용자 흐름

1. **내 자산** — 가상 계좌 3개와 보유 종목 6개의 평가액·손익, 현재 예상 세금을 봅니다.
2. **분석 시작** — 전체 보유 종목을 Java가 계산하고, OpenAI 연결이 있으면 기사·공시를 웹에서 조사합니다. 두 요청은 화면에서 동시에 시작합니다.
3. **기업 자료** — 종목별 원문 링크, AI 요약, 성장 요인과 위험을 확인합니다.
4. **대화** — “테슬라는 장기 보유할래”처럼 말하면 AI가 매도 조건 초안을 제안하고 필요한 내용을 재확인합니다. 사용자가 조건을 적용하기 전까지 확정 조건을 바꾸지 않습니다.
5. **재시뮬레이션** — 확인한 조건으로 Java가 다시 계산합니다. AI 연결이 있으면 출처에 근거한 전략을 추가로 평가합니다.
6. **비교·보고서** — 보유 유지와 매도 대안의 세금·잔여 자산·매도 수량·근거를 비교하고 선택한 안의 보고서를 봅니다.

## 계산과 AI의 역할

- 가상 계좌·보유 수량·세무 규칙은 로컬 시연 데이터입니다. DB와 실계좌 연결은 없습니다.
- 손익 합산·수수료·세액·매도 수량은 Java `BigDecimal` 계산이 기준입니다. AI가 계산 숫자를 직접 결정하지 않습니다.
- OpenAI Responses API는 웹 검색, 기사·기업 자료 요약, 자연어 조건 해석, 근거 기반 전략 제안에 사용합니다. 검색 결과에는 출처 URL과 ID를 연결하고 서버에서 ID를 검증합니다.
- AI 키가 없거나 호출에 실패하면 Java의 기본 계산은 사용할 수 있지만, 최신 기업 자료와 AI 대화·전략 해설은 제한됩니다. 현재 화면은 실패 상태를 표시하거나 기본 계산 결과로 진행할 수 있습니다.
- 대화 상태와 AI 결과 캐시는 백엔드 메모리이며 서버를 재시작하면 사라집니다. 대화 문맥은 최근 메시지만 보유하고 30분 후 만료됩니다.
- 세금·환율·가격·매도 비용은 `README.md`에 적힌 시연 가정입니다. 결과는 실제 세무 신고액이나 주가 예측이 아닙니다.

## 프로젝트 구조

```text
frontend/                       React 19 + Vite 7, npm 프로젝트
  src/App.jsx                   화면 단계·상태·API 요청 연결
  src/ReferenceViews.jsx        자산·분석·시나리오·보고서 화면
  src/LiveEventExplorer.jsx     종목별 기업 자료와 원문
  src/ui.jsx                    재사용 UI와 API 함수
  vite.config.js                5173 개발 서버의 /api → 8080 프록시
backend/                        Java 17+ / Spring Boot 3.5, 독립 Gradle 프로젝트
  src/main/java/com/woori/tax/
    ApiController.java           REST API 진입점
    DemoData.java                가상 계좌와 보유 종목
    PortfolioSimulationService.java  전체 포트폴리오 계산
    LiveResearchService.java     웹 검색 결과·종목별 분석
    LiveAssessmentService.java   자료를 반영한 전략 평가
    AssistantService.java        대화 조건 추출·검증
    ConversationService.java     메모리 대화 문맥
    OpenAiService.java           선택적 외부 API 연결·캐시
  src/main/resources/           세무 규칙·시연 데이터·저장된 근거 JSON
docs/presentation/             발표용 아키텍처·사용 흐름·요청 흐름 이미지
```

프론트와 백엔드는 별도 실행합니다. Gradle은 `backend` 안에만 있습니다. 메인 브랜치는 `main`입니다.

## 주요 API

- `GET /api/portfolio` — 가상 계좌와 현재 예상 세금
- `POST /api/simulation` — 확인된 조건의 Java 시나리오 계산
- `POST /api/research/live` — 기업 자료 검색·분석
- `POST /api/conditions/extract` — 자연어를 조건 초안으로 해석
- `POST /api/simulation/assess` — 계산값과 출처를 사용해 전략 평가
- `DELETE /api/conversations/{id}` — 대화 문맥 삭제

## IDE 실행

1. IntelliJ에서 `backend` 폴더를 Gradle 프로젝트로 열고 JDK 17 또는 21을 선택합니다. `TaxApplication.java`의 실행 버튼으로 시작합니다. Working directory는 `backend`입니다.
2. VS Code 또는 IDE에서 `frontend` 폴더를 열고 최초 1회 의존성을 설치한 뒤 `package.json`의 `dev` 스크립트를 실행합니다.
3. `http://127.0.0.1:5173/`으로 접속합니다. 백엔드 API는 8080이며, Vite가 `/api`를 프록시합니다.

## 다른 노트북에서 작업할 때

- 저장소: `https://github.com/magici4n/woori-ax-hackathon` — `main`에서 이어갑니다. 다른 브랜치를 임의로 삭제하지 마세요.
- **사용자의 명시적 지시: 테스트나 실행 확인을 위해 유료 AI API를 호출하지 마세요.** 프론트 빌드, Java 계산 테스트, mock/stub 기반 검증을 사용합니다. 브라우저에서 AI 동작을 누르는 시연도 크레딧이 소모될 수 있습니다.
- API 키는 로컬의 `backend/src/main/resources/application.properties` 또는 백엔드 실행 환경에만 둡니다. 이 파일은 Git에서 제외됩니다. 키를 문서·로그·채팅·커밋에 복사하지 마세요. 다른 노트북에 키가 자동 복사되지 않습니다.
- `README.md`의 실행 방법과 계산 가정을 먼저 확인하고, 현재 소스와 문서가 다르면 소스를 기준으로 판단합니다.
- 사용자가 명시적으로 요청하지 않는 한 Git push를 하지 않습니다.

## 참고 문서

`README.md`는 제품·계산·실행 기준, `docs/DEMO.md`는 시연 순서, `docs/VERIFICATION.md`는 기존 검증 기록, `docs/SOURCES.md`는 저장 자료의 출처입니다. `docs/presentation/`에는 발표에 넣을 16:9 PNG와 편집 가능한 SVG가 있습니다.
