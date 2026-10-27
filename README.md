# WON 절세 시나리오

여러 증권사의 해외주식 손익을 확인한 뒤 기업 자료를 살펴보고 AI와 투자 의사를 정리합니다. 그 조건으로 시나리오를 비교하고 선택한 안의 계산 보고서를 확인하는 우리 WON 청년 IT 아카데미 해커톤 프로토타입입니다.

React 19 + Vite 7 / Java 17 이상 + Spring Boot 3.5 / OpenAI Responses API를 사용합니다. 데이터베이스와 별도 검색 서버 없이 메모리의 가상 계좌와 작은 JSON 자료집으로 실행합니다. OpenAI 웹 검색으로 최신 기업 자료를 조회합니다. 계산은 전부 Java `BigDecimal`로 수행하며 OpenAI 키 없이도 자산 조회, 계산, 수동 조건 설정, 저장된 근거 조회가 동작합니다.

## 프로젝트를 여는 방법

프론트엔드와 백엔드는 독립 프로젝트입니다. **Gradle은 `backend` 안에만 있으며 React를 빌드하거나 포함하지 않습니다.** 백엔드는 API만 제공하고, 브라우저는 React 개발 서버의 **5173 포트**로 접속합니다.

```text
woori-ax-hackathon/
├─ backend/                 Java + Spring Boot, 독립 Gradle 프로젝트
│  ├─ build.gradle
│  ├─ settings.gradle
│  ├─ gradlew / gradlew.bat
│  ├─ gradle/wrapper/
│  └─ src/
└─ frontend/                React + Vite, 독립 npm 프로젝트
   ├─ package.json
   └─ src/
```

### 백엔드 실행 — IntelliJ 기준

1. **`backend` 폴더**를 Gradle 프로젝트로 엽니다. 배포판은 **Wrapper**, Gradle JVM은 설치된 **JDK 17 또는 21**을 선택합니다.
2. `src/main/java/com/woori/tax/TaxApplication.java`를 열고 main 옆 초록색 실행 버튼을 누릅니다.
3. 실행 설정의 Working directory는 `backend` 폴더로 고정합니다. API는 `http://127.0.0.1:8080/api/portfolio`에서 확인할 수 있습니다.
4. 종료할 때는 IDE 실행 창의 빨간색 중지 버튼을 누릅니다.

`build.gradle`은 Spring Boot·Java·라이브러리·테스트를, `settings.gradle`은 백엔드 프로젝트 이름을 관리합니다. Gradle 창의 `build`와 `test`도 백엔드만 처리합니다. 이전에 루트 전체를 Gradle 프로젝트로 열었다면 닫고 **backend 폴더를 다시 가져오세요.**

### 프론트엔드 실행 — IntelliJ 기준

1. 별도 창에서 **`frontend` 폴더**를 엽니다. 또는 IDE에서 이 폴더의 `package.json`을 엽니다.
2. 최초 의존성이 없다면 `package.json`에서 `npm install`을 실행합니다.
3. `scripts`의 **`dev` 작업**을 실행합니다. IDE에서 줄 옆의 실행 버튼이나 npm 실행 설정을 이용합니다.
4. **http://127.0.0.1:5173/** 으로 접속합니다. React 실행 창과 백엔드 실행 창을 함께 켜두세요.

화면 파일을 저장하면 Vite가 변경 내용을 반영합니다. `/api` 요청은 `frontend/vite.config.js`의 프록시를 통해 8080 백엔드로 전달합니다. 8080 루트 주소에는 React 화면이 없습니다. 백엔드 포트를 바꾼다면 Vite의 프록시 주소도 함께 수정하세요.

### 선택: 터미널 명령

IDE 버튼 대신 명령으로 실행하려는 경우에만 사용합니다. JDK 17 이상, Node.js 22.12 이상과 최초 설치 시 인터넷 연결이 필요합니다. Gradle 8.14.3은 Wrapper가 준비하며 공식 SHA256으로 배포판을 확인합니다.

```text
백엔드 폴더에서:  gradlew.bat bootRun
프론트 폴더에서:  npm install (최초 1회), npm run dev
```

백엔드 JAR을 만들려면 `backend`에서 `gradlew.bat build`를 실행합니다. 결과는 `backend/build/libs/tax-scenario-0.1.0.jar`이며 **API 서버만 포함합니다.** 프론트엔드 배포 빌드는 `frontend`에서 `npm run build`를 실행하면 `frontend/dist`에 생성됩니다. 현재 프론트 실행 방법은 Vite 개발 서버 기준입니다.

## OpenAI 연결 (선택)

키 없이도 계산과 직접 조건 설정이 동작합니다. 실제 OpenAI 호출은 유료 크레딧을 소모합니다. 키를 프론트엔드나 채팅, Git에 넣지 말고 백엔드의 환경변수로 설정한 다음 재시작하세요.

`.env.example`은 변수 목록 예시입니다. Spring Boot가 `.env` 파일을 자동으로 읽는 구성은 아닙니다. IDE를 쓰면 Run Configuration의 환경변수에 설정합니다.

IDE의 `TaxApplication` 실행 설정에서 **Environment variables**에 `OPENAI_API_KEY=발급받은키`를 등록하세요. 모델을 바꾸려면 같은 곳에서 `OPENAI_MODEL`을 지정합니다. 저장 후 백엔드를 재시작하고, React 화면의 ‘AI 사용 상태’에서 설정 여부를 확인합니다. 로컬에서 `backend/src/main/resources/application.properties`의 `app.openai.key`를 사용할 수도 있지만, 이 파일은 Git에서 제외되며 실제 키를 공유하면 안 됩니다. 이전에 노출된 Gemini 키는 Google AI Studio에서 폐기하세요.

기본 모델은 `gpt-6.1-sol`입니다. [공식 가격표](https://developers.openai.com/api/docs/pricing) 확인일은 2026-10-27입니다. 표준 입력 $2 / 출력 $10(각 100만 토큰), 웹 검색 도구 호출당 $0.01을 앱의 추정 단가로 설정합니다. 검색 결과 입력 토큰도 모델 요금으로 계산됩니다. ChatGPT 구독과 OpenAI API 크레딧은 별개입니다.

실제 모델 접근 권한과 잔액은 [OpenAI Platform](https://platform.openai.com/)에서 확인해야 합니다. 키 없이 진행하거나 API가 실패하면 Java의 계산 시나리오를 표시합니다. 코드 전환 검증에는 모의 응답만 사용했고 유료 API 실호출은 하지 않았습니다.

[실제 호출·토큰 한도](https://developers.openai.com/api/docs/guides/rate-limits)는 조직·프로젝트·모델에 따라 다릅니다.

해커톤 안내문상 팀 선결제 후 청구는 불가하므로 API 크레딧 결제와 비용 처리는 운영매니저의 안내를 따르세요. OpenAI Platform에서 자동 충전 상태와 실제 사용액을 확인하세요.

모델 변경은 `OPENAI_MODEL`, `AI_INPUT_USD_PER_MILLION`, `AI_OUTPUT_USD_PER_MILLION`을 함께 설정합니다. 기본 출력 상한은 5,000토큰이며 100~6,000을 허용합니다. 추론 수준은 `medium`입니다. JSON 응답은 Responses API의 JSON 모드를 사용하고 기존 Java 검증을 유지합니다. 웹 검색 요청은 실제 검색을 강제하고 최대 4회 도구 호출로 제한합니다.

## AI 사용량과 비용

- 화면 오른쪽 아래 `AI 사용 상태` 또는 `GET /api/ai/usage`에서 누적 호출 시도, 실제 응답 토큰, 비용 추정치를 확인합니다.
- 실행한 디렉터리의 `data/ai-usage-openai.json`에 누적 기록을 남깁니다. Gradle `bootRun`과 IDE의 Working directory, JAR 실행 위치를 모두 `backend`로 고정하세요. 기록 파일은 Git에서 제외됩니다. 이전 Gemini 사용량과 섞지 않습니다.
- 표시액은 설정된 모델·검색 단가에 따른 추정치이며 실제 청구액이 아닙니다. 실제 사용량·청구는 [OpenAI Platform](https://platform.openai.com/)에서 확인합니다.
- 기본 앱 한도는 누적 40회, 최대 20달러입니다. 호출 전 UTF-8 입력 바이트 수·출력 상한·검색 도구 최대 4회를 기준으로 비용을 예약합니다. 실패 시 예약액을 유지하고 자동 재시도하지 않습니다. 검색 결과가 늘린 입력 토큰 때문에 실제 사용액은 예약액을 초과할 수 있습니다. 기록 파일이 손상되거나 기록할 수 없으면 AI 호출을 중단합니다.
- 동일 모델·입력·조건·자료 버전은 메모리 캐시를 사용합니다. 재시작하면 캐시는 비워지지만 사용량 기록은 유지됩니다. 시나리오 생성 시 검색과 전략 제안을 요청할 수 있습니다. AI 실패 시 자동 재시도하지 않으며 Java 계산 결과와 저장한 기업 자료는 계속 표시합니다.
- 한 프로세스가 자기 호출을 제한하는 시연용 장치입니다. 같은 API 키를 쓰는 외부 앱·다른 프로세스의 지출, 잘못 입력한 모델 가격은 통제하지 못합니다. OpenAI 프로젝트의 지출 한도도 설정하세요. 누적 기록을 삭제하면 앱 한도가 초기화됩니다.

## 구현 범위와 계산 가정

- 시연 계좌 2개, 보유 4종목 / 5개 매수 건. 실제 연결을 하지 않으며 계좌 입력·수정·주문·신고 기능은 없습니다.
- 2026년 한국 거주 개인의 일반적인 미국 상장주식 시연. 확정 원화 실현손익 A +600만원 / B -100만원을 합산해 출발 손익 +500만원, 예상 세금 55만원으로 구성했습니다.
- 기본공제 250만원, 국세 20% + 지방세 2%의 단순 합산 세율 22%를 가정합니다. 세액공제·특례와 실제 신고 절사를 제외한 **시연 추정치**입니다. 국세청 자료에는 이전 귀속연도 안내도 포함되며 2026년 개별 세무 검토를 완료했다는 의미가 아닙니다.
- 원화 취득가액은 매수 당시 환율과 과거 매수 비용을 포함한 저장값입니다. 현재 환율로 다시 환산하지 않습니다. 가상 매도 수수료만 새로 차감합니다.
- 계좌 A 매도 비용 0.25%, B 0.20%는 실증권사 요율이 아닌 가정 합산 요율입니다. 원가 안분은 소수 12자리, 표시 원화와 예상 세액은 원 단위 HALF_UP입니다.
- 같은 종목은 모든 계좌 합산에 조건 적용. 매도 배분은 계좌 A → B 순서로 고정합니다. 계좌 배분 최적화나 증권사별 다회 매수 원가 계산은 지원하지 않습니다.
- 기본 화면은 전체 종목을 함께 계산합니다. 각 종목의 한도를 지킨 정수 수량 중 원화 세무 손실이 발생하는 조합과 보유 유지를 탐색합니다(최대 10만 조합). 손익을 합산한 뒤 세액을 한 번 계산합니다.
- 보유 유지는 매도 0, 일부 절세는 최대 감소액의 절반 이상(목표 지정 시 해당 목표)을 충족하는 최소 매도 평가액, 절세 우선은 최소 세액에 도달하는 최소 매도 평가액을 선택합니다. 동일 평가액이면 수수료가 낮은 조합을 선택합니다. 결과가 같으면 중복을 표시하며, 불가능한 목표 때문에 조건을 완화하지 않습니다.
- 전체 잔여 주식 가격이 함께 -10%·0%·+10% 변하는 가정을 비교합니다. 예측·확률이 아닙니다. 환율은 1,380원 고정, 현금은 달러로 보유하고 수익률 0%, 환전 비용 0원입니다. 기존 단일 종목 API는 별도로 유지합니다.
- 전체 자산 = 매도 후 현금의 원화 환산액 + 전체 잔여 주식 평가액 − 당해 전체 예상 세금. 비용은 현금에 반영되어 다시 빼지 않습니다. 미래 잔여 주식의 잠재 세금과 추가 매도는 제외합니다.
- 결제일을 2026-10-28로 가정해 해당 연도에 포함합니다. 연말 거래 마감일을 임의로 계산하지 않습니다. 실제 결제 환율·휴장·증권사 원가·필요경비·귀속연도는 별도 검토 대상입니다.

## 프로젝트 구조와 데이터 흐름

```text
frontend/
  src/main.jsx                 React 시작점
  src/App.jsx                  자산 → 계산 → 기업 자료 → 대화 → 시나리오 → 보고서 흐름과 API 연결
  src/ReferenceViews.jsx       자산·분석·기업 자료·시나리오 선택·보고서 화면
  src/reference-flow.css       첨부 화면 흐름에 맞춘 반응형 레이아웃과 인쇄 스타일
  src/PortfolioResults.jsx     이전 결과 화면 구성 요소 (현재 메인 화면에서는 미사용)
  src/simulation-flow.css      자산/시뮬레이션 화면과 반응형 배치
  src/ui.jsx                   조건 폼·시나리오 그래프·근거 목록 등 재사용 UI
  src/conversation-flow.css    대화 중심 화면과 단계별 배치
  src/woori-theme.css          우리투자증권 참고 테마와 반응형 화면
  src/ServiceLayout.jsx        브랜드 헤더·5단계 이용 안내
  vite.config.js               개발 포트와 API 프록시
backend/
  build.gradle                 Spring Boot·Java·라이브러리·테스트 설정
  settings.gradle              백엔드 프로젝트 이름
  gradlew / gradlew.bat         백엔드 Gradle Wrapper
  gradle/wrapper/              Gradle 버전·체크섬·Wrapper JAR
  src/main/java/com/woori/tax/
    TaxApplication.java        Spring Boot 시작점
    Models.java                계좌·조건·시나리오·자료 API 모델
    DemoData.java              단일 매수 가상 계좌와 확정 원화 손익
    ScenarioService.java       검증, BigDecimal 계산, 정수 수량 탐색, 가격 가정
    PortfolioSimulationService.java 전체 종목의 손실 실현 조합과 세 가지 전략 계산
    ApiController.java         REST API 진입점과 입력 오류 처리
    EvidenceService.java       로컬 자료집의 종목·날짜·자료 종류 필터
    OpenAiService.java         Responses API 호출, 웹 인용 추출, 캐시, 누적 호출·비용 제한
    AssistantService.java      선택 종목·최근 대화 전달, 확인 답변·조건 제안 검증
    ConversationService.java   대화별 메모리 상태·확인 질문·만료·초기화
  src/main/resources/
    tax-rule.json              연도·세율·공제·환율·시연 가정
    evidence.json              원문 URL·발행일·수집일·분류·종목·요약
    research-notes.json        4개 기업의 출처 연결된 성장·위험·보유 기회 요약
    application.properties     환경변수 연결
  src/test/java/.../           핵심 계산·조건·근거·키 미설정 API 검증
docs/                          행사 요구사항, 시연 순서, 검증 기록
```

자산 조회 → Java 기본 분석 → 확인된 기업 자료 탐색 → 모달에서 AI와 보유 의사·매도 범위 대화 → 세 가지 시나리오 비교 → 선택한 안의 보고서 확인 순서입니다. 보고서는 브라우저 인쇄 기능으로 PDF 저장할 수 있습니다. 주문 실행과 별도 PDF 생성 서버는 없습니다. 대화 뒤에도 조건을 수정해 다시 계산할 수 있으며, AI 연결이 불가능하면 대화 화면의 직접 조건 수정이나 기본 조건으로 계산할 수 있습니다. 화면 선택 종목과 합산 수량, 대화 초안, 전체 자산 시뮬레이션 요약, 기업 요약, 최근 대화, 확인 질문을 OpenAI에 전달합니다. 발화는 1,000자, 이력은 최대 12개(프롬프트에는 각 350자), 세션은 최대 100개·30분 유효, AI 캐시는 최대 100건으로 제한합니다.

대화 결과는 조건의 **제안**입니다. 사용자가 확인해야 적용합니다. 언급하지 않은 조건은 유지하며, AI가 기존 제한을 완화하려 하면 거절하고 직접 수정을 안내합니다. 화면에서 조건을 직접 삭제·수정하는 것은 사용자의 명시적 변경입니다. 모호한 장기 보유 의사와 계좌 일부 범위는 확인 대상으로 처리합니다. 범위 밖 질문은 조건 비교 기능으로 안내하도록 프롬프트를 제한합니다. 확인 질문에 대한 짧은 긍정·부정은 서버 상태를 기준으로 처리하며, 긍정도 조건 제안만 생성합니다. 종목 변경·새 대화·만료 시 이전 확인 질문은 폐기됩니다. 새 대화는 이미 적용한 조건을 해제하지 않습니다.

저장된 기업 근거는 실제 원문을 확인한 짧은 요약입니다. 추가로 시뮬레이션 시 OpenAI 웹 검색으로 최근 뉴스·공시·실적발표 일정·날짜가 확인된 밸류에이션 자료를 조사합니다. URL 인용이 달린 문장과 보유 수량·사용자 제한·절세 목표·의사결정 우선순위를 OpenAI에 전달합니다. OpenAI가 서로 다른 두 가상 매도 전략의 종목별 수량과 근거를 제안합니다. Java는 보유하지 않은 종목, 잘못된 수량, 사용자 제한 위반, 출처 ID 오류를 거부하고 각 전략의 세액·수수료·남은 자산을 계산합니다. 보유 유지 기준과 조건 내 최대 절세액도 Java가 계산합니다. 출처가 없거나 검색·제안·검증에 실패하면 기존 Java 계산 시나리오를 유지합니다. 검색 결과는 30분간 메모리에 보관하며 시뮬레이션마다 최대 2회 OpenAI 응답 호출이 추가될 수 있습니다. 가상 주가·환율은 최신 값으로 갱신되지 않습니다. 자료의 사실 충실성은 URL 인용만으로 보장되지 않으므로 원문 확인이 필요합니다. 테스트는 모의 OpenAI 응답만 사용합니다.

## API

전략 제안 입력에는 검색 응답 중 `url_citation`으로 출처가 연결된 문장만 사용합니다. 화면에서 각 문장과 해당 출처를 함께 확인할 수 있습니다.

| API | 역할 |
|---|---|
| `GET /api/portfolio` | 계좌·보유 종목·원화 평가액·연 손익·세금 규칙 |
| `POST /api/scenarios` | `{symbol, conditions, customQuantity}`로 비교 |
| `POST /api/simulation` | `{conditions}`로 전체 보유 종목의 세 가지 시나리오 계산 |
| `POST /api/simulation/assess` | `{conditions, decisionPriority}`로 검색 후 AI가 두 매도 전략을 제안하고 Java가 검증·계산; 실패 시 계산 기준 유지 |
| `POST /api/simulation/explain` | `{conditions}`를 재계산해 시나리오별 OpenAI 해설 생성 |
| `GET /api/research` | 4개 기업의 성장·위험 요약과 실제 출처 |
| `POST /api/conditions/extract` | `{message, existing, selectedSymbol, conversationId}`로 대화 조건 제안 |
| `DELETE /api/conversations/{id}` | 대화 이력·확인 질문 초기화 (적용 조건 유지) |
| `GET /api/evidence?symbol=TSLA` | 종목과 기준일에 맞는 근거 |
| `POST /api/explain` | 서버가 다시 계산한 결과와 검색 자료로 설명 |
| `GET /api/ai/usage` | 키 노출 없는 설정 여부·호출·토큰·추정 비용 |

계산 요청 예시:

```json
{"symbol":"TSLA","conditions":{"symbols":{"AAPL":{"excluded":true},"TSLA":{"excluded":false,"maxPercent":50}},"targetReductionKrw":550000},"customQuantity":20}
```

## 검증과 확장

IDE의 Gradle 창에서 백엔드 `test` 또는 `build`를 실행합니다. 프론트엔드는 `package.json`의 `build`를 실행합니다. 명령을 쓴다면 각각의 폴더에서 다음과 같습니다.

```text
backend:   gradlew.bat clean build
frontend:  npm run build
```

테스트 HTML 보고서는 `backend/build/reports/tests/test/index.html`, 실행 JAR은 `backend/build/libs/tax-scenario-0.1.0.jar`에 생성됩니다. 백엔드 Gradle과 프론트엔드 npm 빌드는 서로 독립적입니다.

검증 내역은 `docs/VERIFICATION.md`, 시연 순서는 `docs/DEMO.md`, 행사 요구사항은 `docs/HACKATHON.md`에 있습니다.

수정 시작점: 계좌와 거래 데이터는 `DemoData`, 세무 가정은 `tax-rule.json`, 검색 자료는 `evidence.json`, 계산 변경은 `ScenarioService`와 대응 테스트입니다. 화면 흐름은 `App.jsx`, 재사용 UI는 `ui.jsx`, 브랜드 구성은 `ServiceLayout.jsx`로 나눴습니다. 기능이 늘어나면 컴포넌트 파일 분리, 계좌별 매도 배분 선택, 증권사 원가 검증, 영속 사용자 조건, 자료 버전 관리, 인증을 차례로 확장하세요. 현재는 로컬 해커톤 시연용으로 공개 배포를 위한 사용자 인증과 다중 사용자 세션을 포함하지 않습니다.
