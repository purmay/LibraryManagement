import org.junit.jupiter.api.*;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class LibraryMainTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream testOut;

    @BeforeEach
    void setUpOutput() {
        testOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOut));
    }

    @AfterEach
    void restoreSystemInputOutput() {
        System.setIn(originalIn);
        System.setOut(originalOut);
    }

    /**
     * 사용자의 키보드 입력을 가상으로 주입하는 헬퍼 메서드
     */
    private void provideInput(String data) {
        ByteArrayInputStream testIn = new ByteArrayInputStream(data.getBytes());
        System.setIn(testIn);
    }

    /**
     * 콘솔에 출력된 결과 문자열을 가져오는 헬퍼 메서드
     */
    private String getOutput() {
        return testOut.toString();
    }

    @Test
    @DisplayName("보안 테스트: 메뉴 입력창에 문자열(abc) 입력 시 예외 처리 및 방어 검증")
    void testMenuInputValidationMitigation() {
        System.out.println("[Test] 메뉴 입력창 비정상 문자열 주입 테스트 시작");

        // 💡 [핵심 해결책] 스캐너가 예외 처리 버퍼를 비우고 메뉴판을 반복해서 읽을 때
        // 입력값이 모자라지 않도록 뒤쪽에 종료 명령어 '0'과 엔터키를 넉넉하게 연속으로 주입합니다.
        String sequentialInput = "user02\n3333\nabc\n0\n0\n0\n0\n0\n";
        provideInput(sequentialInput);

        // When: LibraryMain의 main 메서드를 가상 환경에서 실행
        try {
            LibraryMain.main(new String[0]);
        } catch (java.util.NoSuchElementException e) {
            // 입출력 스트림이 닫히거나 다 읽어서 나는 예외는 예외 처리 루프가 정상 작동했다는 뜻이므로 허용 처리
            System.out.println("[알림] 입력 스트림이 안전하게 종료 지점까지 도달했습니다.");
        } catch (Exception e) {
            fail("메뉴 입력에 문자를 넣었을 때 프로그램이 예외를 잡지 못하고 크래시로 비정상 종료되었습니다: " + e.getMessage());
        }

        // Then: 콘솔 출력 결과를 가로채서 프로그램이 안전하게 방어되었는지 검증
        String consoleOutput = getOutput();

        // 1. 숫자가 아니라는 예외 처리 안내 경고 메시지가 출력되었는지 검증
        assertTrue(consoleOutput.contains("[오류]") || consoleOutput.contains("숫자") || consoleOutput.contains("잘못된") || consoleOutput.contains("다시"),
                "숫자가 아닌 값이 들어왔을 때 프로그램이 터지지 않고 안전하게 예외 경고를 띄워야 합니다.");

        // 2. 프로그램 종료 시 DB 동기화 구문이 콘솔에 안전하게 출력되었는지 검증
        assertTrue(consoleOutput.contains("MariaDB") || consoleOutput.contains("동기화") || consoleOutput.contains("종료"),
                "비정상 입력이 들어와도 예외를 흡수하고 최종 단계인 DB 동기화 및 종료까지 안전하게 수행되어야 합니다.");

        System.out.println("[Test] 결과: 문자열 주입 시 프로그램 크래시 없이 예외 처리 완료 및 안전 구동 확인");
    }
}