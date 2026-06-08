import java.sql.*;
import java.util.*;

public class LibraryRepository {
    // DB 연결 정보
    private final String URL = "jdbc:mariadb://192.168.100.20:3306/library";
    private final String USER = "cjulib";
    private final String PASSWORD = "security";

    /**
     * MariaDB 연결을 위한 전용 메소드입니다.
     */
    private Connection getConnection() throws SQLException {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("드라이버 로드 실패: " + e.getMessage());
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * 메모리의 모든 도서 정보를 MariaDB에 동기화(저장)합니다.
     */
    public void saveBooks(Map<Integer, Book> bookMap) {
        String sql = "INSERT INTO books (book_id, title, author, is_available, member_id) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "title = VALUES(title), " +
                "author = VALUES(author), " +
                "is_available = VALUES(is_available), " +
                "member_id = VALUES(member_id)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Book book : bookMap.values()) {
                pstmt.setInt(1, book.getId());
                pstmt.setString(2, book.getTitle());
                pstmt.setString(3, book.getAuthor());
                pstmt.setBoolean(4, book.isAvailable());

                if (book.getBorrowerId() == null || "null".equals(book.getBorrowerId())) {
                    pstmt.setNull(5, java.sql.Types.VARCHAR);
                } else {
                    pstmt.setString(5, book.getBorrowerId());
                }
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            System.out.println("[시스템] 모든 도서 데이터가 MariaDB에 동기화되었습니다.");

        } catch (SQLException e) {
            System.err.println("[오류] DB 저장(saveBooks) 실패: " + e.getMessage());
        }
    }

    /**
     * 특정 도서 ID를 받아 데이터베이스에서 해당 도서를 삭제합니다.
     */
    public boolean deleteBook(int bookId) {
        String sql = "DELETE FROM books WHERE book_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, bookId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                System.out.println("[시스템] 도서 번호 " + bookId + "번이 성공적으로 삭제되었습니다.");
                return true;
            } else {
                System.out.println("[알림] 삭제할 도서 번호 " + bookId + "번을 찾을 수 없습니다.");
                return false;
            }

        } catch (SQLException e) {
            System.err.println("[오류] DB 삭제 작업 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * 데이터베이스로부터 모든 도서 정보를 조회하여 메모리에 로드합니다.
     */
    public Map<Integer, Book> loadBooks() {
        Map<Integer, Book> bookMap = new HashMap<>();
        String sql = "SELECT * FROM books";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("book_id");
                String title = rs.getString("title");
                String author = rs.getString("author");
                boolean available = rs.getBoolean("is_available");
                String mid = rs.getString("member_id");

                bookMap.put(id, new Book(id, title, author, available, mid == null ? "null" : mid));
            }
        } catch (SQLException e) {
            System.err.println("[오류] 로드 실패: " + e.getMessage());
        }
        return bookMap;
    }

    /**
     * 사용자 로그인을 위한 정보를 조회합니다.
     * <p><b>보안 조치 완료:</b> PreparedStatement 파라미터 바인딩을 적용하여 SQL Injection을 차단했습니다.</p>
     *
     * @param id 사용자 아이디
     * @param pw 사용자 비밀번호
     * @return 인증된 {@link User} 객체 (일치 정보 없을 시 null)
     */
    public User loadUser(String id, String pw) {
        // 🛠 [보안 패치] 문자열 더하기 방식을 전면 제거하고 안전한 위치 홀더(?) 구조로 변경
        String sql = "SELECT * FROM users WHERE user_id = ? AND password = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 🛠 [보안 패치] 입력값을 쿼리 구조와 완전히 분리하여 안전하게 세팅
            pstmt.setString(1, id);
            pstmt.setString(2, pw);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getString("user_id"),
                            rs.getString("password"),
                            rs.getString("type")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[오류] 로그인 조회 실패: " + e.getMessage());
        }
        return null;
    }
}