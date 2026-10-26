# Codex 인수인계

다른 PC에서 이 저장소를 열었을 때 먼저 읽을 문서입니다. 프로젝트의 상세 구조·설정·가정·API는 루트 `README.md`가 기준이고, 이 문서는 현재 작업 상태와 다음 Codex 세션의 진행 원칙을 요약합니다.

## 현재 상태

- 저장소: `https://github.com/magici4n/woori-ax-hackathon`
- 계속 사용할 브랜치: `main`만 사용합니다. 현재 작업은 `main`의 `1261b05` (`feat: add portfolio-first tax simulation flow`)까지 원격 `origin/main`에 push되어 있습니다.
- 프론트엔드와 백엔드는 독립 실행 프로젝트입니다. 루트 통합 Gradle 프로젝트로 합치지 않습니다.
- 구현된 사용자 흐름: 자산 확인 → 전체 보유 기준 절세 시뮬레이션 → 세 가지 시나리오·산출 근거·기업 자료 확인 → 투자 의사를 대화로 전달 → 제안 조건을 확인한 후 재시뮬레이션.
- 계산과 대화 조건 적용은 Java/Spring에서 처리합니다. React는 화면·상태·API 연결을 담당합니다. 데이터베이스 없이 가상 계좌 및 로컬 JSON 자료를 사용합니다.
- 기능과 가정의 상세 설명, 실행 방법, API 목록은 루트 `README.md`; 시연은 `docs/DEMO.md`; 검증 이력은 `docs/VERIFICATION.md`; 출처는 `docs/SOURCES.md`를 확인하세요.

## 다음 세션에서 지킬 사항

1. **테스트에서 Gemini API를 호출하지 마세요.** 사용량을 소모하는 실제 Gemini 요청이나 브라우저 시연을 새로 하지 않습니다. Gemini 관련 테스트는 mock/stub 응답으로만 실행하고 계산·조건 파싱·화면 흐름을 검증합니다.
2. 키 문자열을 채팅, 코드, 로그, 스크린샷, 커밋에 출력하지 마세요. `backend/src/main/resources/application.properties`는 사용자의 로컬 비밀 설정으로 `.gitignore` 처리되어 있습니다. 내용을 읽거나 수정하지 말고, 상태 확인 시에도 파일 내용을 출력하지 않습니다.
3. API 키 없이도 계산·수동 조건 입력·자료 조회가 동작하는 것을 유지합니다. AI 연결 검증은 사용자가 별도로 요청할 때까지 하지 않습니다.
4. 사용자 선호: 터미널 명령만 나열하기보다 IDE에서 프로젝트 구조를 이해하고 직접 실행할 수 있게 안내합니다. backend는 IntelliJ, frontend는 VS Code/IDE의 `dev` 실행을 기준으로 설명합니다.
5. 금융·세무 수치는 가상 시연값과 단순 추정입니다. 실제 투자 권유·세무 신고 결과처럼 표현하지 않습니다. 최신 규정이나 외부 출처가 필요한 변경은 검증된 근거를 문서화합니다.

## IDE 실행

### 백엔드

1. IntelliJ에서 저장소의 `backend` 폴더를 Gradle 프로젝트로 엽니다.
2. Gradle Wrapper와 JDK 17 또는 21을 선택합니다.
3. `backend/src/main/java/com/woori/tax/TaxApplication.java`에서 실행합니다. Working directory는 `backend`로 둡니다.
4. API는 `http://127.0.0.1:8080`에서 제공됩니다.

### 프론트엔드

1. VS Code에서 저장소의 `frontend` 폴더를 엽니다.
2. 의존성이 최초 설치되어 있지 않을 때만 IDE의 npm 기능에서 `npm install`을 실행합니다.
3. `frontend/package.json`의 `dev` 스크립트를 실행합니다.
4. 브라우저에서 `http://127.0.0.1:5173/`을 엽니다. Vite가 `/api`를 8080 백엔드로 프록시합니다.

두 서버는 각자 실행·중지합니다. 두 프로젝트의 실행 설정은 루트 폴더에 Gradle을 추가하지 않습니다. 터미널 실행이 필요한 경우 명령은 `README.md`에 있습니다.

## Gemini 설정 메모

키가 이미 있는 PC에서는 `TaxApplication`의 IntelliJ 실행 설정 → Environment variables에 `GEMINI_API_KEY`를 로컬로 입력합니다. 키 값은 인수인계 문서나 GitHub에 옮기지 않습니다. 다른 PC에 비밀 키가 자동 복사된다고 가정하지 마세요. 앱의 `AI_MAX_CALLS`나 비용 설정은 Google의 실제 API 한도와 별개이며, 사용자 지시가 있기 전까지 테스트 중 실제 API 호출은 금지입니다.

## 다음 작업 시작 순서

1. 새 PC에서 저장소를 clone하거나 최신 `main`을 pull하고 현재 브랜치가 `main`인지 확인합니다. 원격 `develop`이 보이더라도 전환하거나 삭제하지 말고 `main`에서만 작업합니다.
2. 이 문서와 `README.md`의 데이터 흐름/API/제약을 읽습니다.
3. 사용자가 요청한 변경과 현재 소스 상태를 먼저 비교하고, 기존 변경을 덮어쓰지 않습니다.
4. 외부 API 없이 실행 가능한 확인만 합니다. 사용자가 테스트를 요청했을 때도 Gemini 테스트는 mock/stub을 사용합니다.
5. 작업 후 변경 파일·확인 결과·한계를 간단히 보고합니다. GitHub 업로드는 사용자가 요청한 경우 `main`으로 올립니다.

## 지금까지 남은 검토 범위

현재 프로토타입은 인증·다중 사용자 데이터 격리·DB 영속 저장·실계좌/증권사 연동·실시간 뉴스 수집·PER 밸류에이션·실제 주가 예측·실제 세무 신고 검증을 포함하지 않습니다. 대화 상태는 서버 메모리 기반이며 재시작하면 사라집니다. 기존 테스트와 검증 기록은 `docs/VERIFICATION.md`를 참고하되, 실제 Gemini 요청을 재현하지 마세요.
