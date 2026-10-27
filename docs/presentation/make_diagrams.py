"""Generate slide-ready SVG diagrams from the current prototype's code flow."""
from pathlib import Path
from html import escape

OUT = Path(__file__).parent
W, H = 1920, 1080
NAVY = "#09265C"
BLUE = "#0068F5"
PALE = "#EAF3FF"
INK = "#183459"
MUTED = "#657B9F"
LINE = "#D5E3F6"
GREEN = "#008F84"
PURPLE = "#704FC6"


def text(x, y, value, size=28, color=INK, weight=500, anchor="start"):
    return f'<text x="{x}" y="{y}" fill="{color}" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}">{escape(value)}</text>'


def rect(x, y, w, h, fill="white", stroke=LINE, radius=25, sw=2):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>'


def pill(x, y, w, label, fill=PALE, color=BLUE):
    return rect(x, y, w, 44, fill, "none", 22, 0) + text(x + w / 2, y + 29, label, 21, color, 700, "middle")


def arrow(x1, y1, x2, y2, color=BLUE, width=5, dashed=False):
    dash = ' stroke-dasharray="12 10"' if dashed else ""
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{color}" stroke-width="{width}" stroke-linecap="round" marker-end="url(#arrow)"{dash}/>'


def card(x, y, w, h, eyebrow, title, lines, accent=BLUE, fill="white"):
    s = rect(x, y, w, h, fill)
    s += f'<rect x="{x}" y="{y}" width="8" height="{h}" rx="4" fill="{accent}"/>'
    s += text(x + 32, y + 46, eyebrow, 21, accent, 700)
    s += text(x + 32, y + 103, title, 34, NAVY, 800)
    for i, line in enumerate(lines):
        s += text(x + 32, y + 153 + i * 38, line, 23, MUTED)
    return s


def base(section, title, subtitle):
    s = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<defs>
<linearGradient id="bg" x2="1" y2="1"><stop stop-color="#F6FAFF"/><stop offset="1" stop-color="#E7F2FF"/></linearGradient>
<marker id="arrow" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto" markerUnits="userSpaceOnUse"><path d="M2 2L10 6L2 10" fill="none" stroke="{BLUE}" stroke-width="2.3"/></marker>
<style>text{{font-family:'Malgun Gothic','Apple SD Gothic Neo','Noto Sans KR',sans-serif;paint-order:stroke;}}</style>
</defs><rect width="1920" height="1080" fill="url(#bg)"/>
<circle cx="1810" cy="50" r="280" fill="#D8EAFF" opacity=".58"/>
<circle cx="55" cy="1015" r="190" fill="#DCEBFF" opacity=".42"/>
'''
    s += pill(72, 55, 185, section, "#DDEEFF", BLUE)
    s += text(72, 172, title, 62, NAVY, 800)
    s += text(75, 220, subtitle, 26, MUTED)
    return s


def finish(s, caption, name):
    s += f'<line x1="72" y1="1006" x2="1848" y2="1006" stroke="{LINE}" stroke-width="2"/>'
    s += text(72, 1044, caption, 21, MUTED)
    s += text(1848, 1044, "WON 절세 시나리오 · 해커톤 프로토타입", 21, BLUE, 700, "end")
    (OUT / name).write_text(s + "</svg>", encoding="utf-8")


def architecture():
    s = base("01  SYSTEM", "서비스 아키텍처", "화면·계산·AI의 역할을 분리한 독립 실행형 웹 프로토타입")
    s += card(74, 320, 405, 405, "사용자 화면 · 5173", "React + Vite", ["자산·기업 자료·대화", "시나리오·최종 보고서", "화면 상태와 조건 초안 관리"], BLUE)
    s += card(663, 320, 590, 405, "API 서버 · 8080", "Java + Spring Boot", ["REST API  /api/*", "BigDecimal 세금·매도 시뮬레이션", "조건 검증·출처 ID 검증", "대화 문맥: 메모리 30분"], NAVY)
    s += card(1437, 320, 405, 405, "선택적 외부 연결", "OpenAI API", ["웹 검색·기사 요약", "자연어 조건 추출", "근거 기반 전략 제안"], PURPLE)
    s += arrow(490, 525, 645, 525)
    s += text(566, 494, "/api 프록시", 22, BLUE, 700, "middle")
    s += arrow(1270, 525, 1420, 525)
    s += text(1345, 494, "AI 요청", 22, BLUE, 700, "middle")
    s += rect(663, 762, 590, 173, "#EEF6FF", "#CDE1FB", 22)
    s += text(695, 815, "시연 데이터", 28, NAVY, 800)
    s += text(695, 859, "가상 계좌·보유 종목·세금 규칙: JSON", 23, INK)
    s += text(695, 898, "별도 DB / 실계좌 연동 없음", 22, MUTED)
    s += rect(74, 762, 405, 173, "#FFFFFF", LINE, 22)
    s += text(103, 815, "브라우저 요청", 27, NAVY, 800)
    s += text(103, 859, "GET · POST JSON 응답", 23, INK)
    s += text(103, 898, "프론트·백엔드 별도 실행", 22, MUTED)
    s += rect(1437, 762, 405, 173, "#F5F1FF", "#E2D8FB", 22)
    s += text(1467, 815, "연결 실패 시", 27, NAVY, 800)
    s += text(1467, 859, "Java 계산 결과는 유지", 23, INK)
    s += text(1467, 898, "AI 근거·해설은 사용 불가 표시", 22, MUTED)
    finish(s, "현재 구현 기준 · 계산은 Java, 기업 자료와 자연어 해석은 외부 AI 연결", "01-architecture.svg")


def journey():
    s = base("02  JOURNEY", "사용자 시나리오", "자산 확인에서 시작해, 내 의사를 반영한 절세안을 선택합니다")
    cards = [
        (74, 310, "01 · 진입", "내 자산 확인", ["보유 종목과 평가손익", "현재 예상 양도소득세"], BLUE),
        (685, 310, "02 · 근거", "기업 자료 확인", ["기사·공시·원문 링크", "보유·매도 검토 포인트"], GREEN),
        (1296, 310, "03 · 대화", "내 조건 말하기", ["“테슬라는 오래 보유할래”", "AI가 조건 확인·초안 생성"], PURPLE),
        (1296, 665, "04 · 재계산", "다시 시뮬레이션", ["확인한 조건을 반영", "Java가 매도 수량·세액 계산"], BLUE),
        (685, 665, "05 · 비교", "3가지 안 비교", ["절세액·잔여 자산·근거", "원하는 시나리오 선택"], GREEN),
        (74, 665, "06 · 결정", "보고서 확인", ["선택한 안의 계산 상세", "가정과 출처 다시 확인"], PURPLE),
    ]
    for x, y, eyebrow, title, lines, color in cards:
        s += card(x, y, 550, 260, eyebrow, title, lines, color)
    s += arrow(635, 440, 665, 440)
    s += arrow(1246, 440, 1276, 440)
    s += arrow(1570, 578, 1570, 648)
    s += arrow(1290, 795, 1262, 795)
    s += arrow(679, 795, 651, 795)
    s += pill(760, 594, 400, "요구사항이 바뀌면 대화 → 재계산", "#DAE9FF", BLUE)
    finish(s, "사용자의 매도 의사 확인 후 계산 · 주문 실행 기능은 없음", "02-user-journey.svg")


def requests():
    s = base("03  REQUESTS", "요청·응답 흐름", "사용자 행동 하나가 어떤 API와 서비스로 이어지는지")
    lanes = [(85, "React 화면"), (530, "Spring API"), (975, "Java 계산"), (1420, "OpenAI API")]
    for x, label in lanes:
        s += rect(x, 277, 365, 64, "#E5F0FF", "none", 16, 0)
        s += text(x + 182, 320, label, 27, NAVY, 800, "middle")
        s += f'<line x1="{x+182}" y1="350" x2="{x+182}" y2="940" stroke="{LINE}" stroke-width="3" stroke-dasharray="9 12"/>'
    rows = [
        (385, "① 자산 화면", "GET /portfolio", "가상 계좌 데이터", None),
        (470, "② 분석 시작", "POST /simulation", "BigDecimal 계산", 2),
        (555, "    동시 요청", "POST /research/live", "웹 검색·출처 요약", 3),
        (640, "③ 투자 의사", "POST /conditions/extract", "조건 해석·질문", 3),
        (725, "④ 재계산", "POST /simulation", "확인 조건 재계산", 2),
        (810, "⑤ 전략 평가", "POST /simulation/assess", "자료 기반 제안", 3),
    ]
    centers = [x + 182 for x, _ in lanes]
    for y, stage, route, detail, target in rows:
        s += pill(72, y - 20, 220, stage, "#FFFFFF", BLUE)
        s += arrow(350, y, centers[1] - 17, y, BLUE, 4)
        s += text(520, y - 14, route, 20, NAVY, 700, "middle")
        if target is not None:
            s += arrow(735, y, centers[target] - 17, y, BLUE, 4)
            s += text((735 + centers[target]) / 2, y - 14, detail, 20, NAVY, 700, "middle")
        else:
            s += text(765, y + 9, detail, 20, MUTED)
    s += rect(430, 884, 1250, 70, "#FFFFFF", LINE, 16)
    s += text(1055, 927, "Spring이 결과 JSON을 응답 → React가 시나리오·근거·보고서를 표시", 25, NAVY, 700, "middle")
    finish(s, "결과 JSON을 React가 표시 · AI 실패 시 계산 결과만 표시 · 실제 매매 요청 없음", "03-request-flow.svg")


if __name__ == "__main__":
    architecture()
    journey()
    requests()
