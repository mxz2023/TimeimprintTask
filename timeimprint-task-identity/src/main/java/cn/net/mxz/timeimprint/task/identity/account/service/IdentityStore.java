package cn.net.mxz.timeimprint.task.identity.account.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityStore {

    private final JdbcTemplate jdbc;

    public IdentityStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isActive(String tenantId, String actorKey) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_identity_user WHERE tenant_id = ? AND actor_key = ? AND account_status = 'ACTIVE'",
                Integer.class,
                tenantId,
                actorKey);
        return n != null && n > 0;
    }

    public Optional<IdentityUser> findByPhone(String tenantId, String phone) {
        return one("SELECT * FROM tt_identity_user WHERE tenant_id = ? AND phone_number = ?", tenantId, phone);
    }

    public Optional<IdentityUser> findByActor(String tenantId, String actorKey) {
        return one("SELECT * FROM tt_identity_user WHERE tenant_id = ? AND actor_key = ?", tenantId, actorKey);
    }

    public Optional<IdentityUser> findById(long userId) {
        return one("SELECT * FROM tt_identity_user WHERE user_id = ?", userId);
    }

    public Optional<IdentityUser> findByTokenHash(byte[] tokenHash) {
        List<IdentityUser> rows = jdbc.query(
                """
                SELECT u.* FROM tt_identity_user u
                JOIN tt_identity_session s ON s.user_id = u.user_id
                WHERE s.token_hash = ?
                """,
                (rs, i) -> map(rs),
                tokenHash);
        return rows.stream().findFirst();
    }

    public long insertUser(IdentityUser draft, LocalDateTime now) {
        jdbc.update(
                """
                INSERT INTO tt_identity_user(
                  tenant_id, actor_key, phone_number, username, password_hash, nickname,
                  account_status, is_admin, authorization_verified_at, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                draft.tenantId(),
                draft.actorKey(),
                draft.phoneNumber(),
                draft.username(),
                draft.passwordHash(),
                draft.nickname(),
                draft.accountStatus(),
                draft.admin() ? 1 : 0,
                draft.authorizationVerifiedAt() == null ? null : Timestamp.valueOf(draft.authorizationVerifiedAt()),
                Timestamp.valueOf(now));
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    public int countUsers() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tt_identity_user", Integer.class);
        return n == null ? 0 : n;
    }

    public void markAuthorized(long userId, LocalDateTime when) {
        jdbc.update(
                "UPDATE tt_identity_user SET authorization_verified_at = ? WHERE user_id = ?",
                Timestamp.valueOf(when),
                userId);
    }

    public void touchLogin(long userId, LocalDateTime when) {
        jdbc.update("UPDATE tt_identity_user SET last_login_at = ? WHERE user_id = ?", Timestamp.valueOf(when), userId);
    }

    public void updatePassword(long userId, String hash) {
        jdbc.update("UPDATE tt_identity_user SET password_hash = ? WHERE user_id = ?", hash, userId);
    }

    public void updateNickname(long userId, String nickname) {
        jdbc.update("UPDATE tt_identity_user SET nickname = ? WHERE user_id = ?", nickname, userId);
    }

    public void updatePhoneAndUsername(long userId, String phone) {
        jdbc.update(
                "UPDATE tt_identity_user SET phone_number = ?, username = ? WHERE user_id = ?",
                phone,
                phone,
                userId);
    }

    public void disable(long userId, String username) {
        jdbc.update(
                "UPDATE tt_identity_user SET account_status = 'DISABLED', username = ? WHERE user_id = ?",
                username,
                userId);
    }

    public void insertCode(String phone, String code, LocalDateTime now) {
        jdbc.update(
                "INSERT INTO tt_identity_verification_code(phone_number, code, created_at) VALUES (?,?,?)",
                phone,
                code,
                Timestamp.valueOf(now));
    }

    public Optional<LocalDateTime> latestCodeAt(String phone, String code) {
        List<Timestamp> rows = jdbc.query(
                """
                SELECT created_at FROM tt_identity_verification_code
                WHERE phone_number = ? AND code = ?
                ORDER BY verification_code_id DESC LIMIT 1
                """,
                (rs, i) -> rs.getTimestamp("created_at"),
                phone,
                code);
        return rows.stream().findFirst().map(Timestamp::toLocalDateTime);
    }

    public void insertSession(long userId, byte[] tokenHash, LocalDateTime now) {
        jdbc.update(
                "INSERT INTO tt_identity_session(user_id, token_hash, created_at) VALUES (?,?,?)",
                userId,
                tokenHash,
                Timestamp.valueOf(now));
    }

    public void deleteSessions(long userId) {
        jdbc.update("DELETE FROM tt_identity_session WHERE user_id = ?", userId);
    }

    public List<IdentityUser> search(String keyword, int offset, int limit) {
        String like = "%" + keyword + "%";
        if (keyword.isBlank()) {
            return jdbc.query(
                    "SELECT * FROM tt_identity_user ORDER BY created_at DESC, user_id DESC LIMIT ? OFFSET ?",
                    (rs, i) -> map(rs),
                    limit,
                    offset);
        }
        return jdbc.query(
                """
                SELECT * FROM tt_identity_user
                WHERE phone_number LIKE ? OR username LIKE ? OR nickname LIKE ?
                ORDER BY created_at DESC, user_id DESC LIMIT ? OFFSET ?
                """,
                (rs, i) -> map(rs),
                like,
                like,
                like,
                limit,
                offset);
    }

    public int countSearch(String keyword) {
        if (keyword.isBlank()) {
            return countUsers();
        }
        String like = "%" + keyword + "%";
        Integer n = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM tt_identity_user
                WHERE phone_number LIKE ? OR username LIKE ? OR nickname LIKE ?
                """,
                Integer.class,
                like,
                like,
                like);
        return n == null ? 0 : n;
    }

    public Optional<Long> findSocialUserId(String provider, String appType, String openid, String unionid) {
        List<Long> byOpen = jdbc.query(
                """
                SELECT user_id FROM tt_identity_social_account
                WHERE provider = ? AND app_type = ? AND openid = ?
                """,
                (rs, i) -> rs.getLong("user_id"),
                provider,
                appType,
                openid);
        if (!byOpen.isEmpty()) {
            return Optional.of(byOpen.get(0));
        }
        if (unionid == null || unionid.isBlank()) {
            return Optional.empty();
        }
        List<Long> byUnion = jdbc.query(
                "SELECT user_id FROM tt_identity_social_account WHERE provider = ? AND unionid = ? LIMIT 1",
                (rs, i) -> rs.getLong("user_id"),
                provider,
                unionid);
        return byUnion.stream().findFirst();
    }

    public void insertSocial(
            long userId, String provider, String appType, String openid, String unionid, String nickname, String avatar) {
        jdbc.update(
                """
                INSERT INTO tt_identity_social_account(
                  user_id, provider, app_type, openid, unionid, nickname, avatar_url, raw_json, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,CAST('{}' AS JSON),UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """,
                userId,
                provider,
                appType,
                openid,
                unionid == null || unionid.isBlank() ? null : unionid,
                nickname,
                avatar == null ? "" : avatar);
    }

    public void moveSocial(long fromUserId, long toUserId, String provider) {
        jdbc.update(
                "UPDATE tt_identity_social_account SET user_id = ? WHERE user_id = ? AND provider = ?",
                toUserId,
                fromUserId,
                provider);
    }

    public int deleteSocial(long userId, String provider) {
        return jdbc.update(
                "DELETE FROM tt_identity_social_account WHERE user_id = ? AND provider = ?", userId, provider);
    }

    public boolean hasSocial(long userId, String provider) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tt_identity_social_account WHERE user_id = ? AND provider = ?",
                Integer.class,
                userId,
                provider);
        return n != null && n > 0;
    }

    private Optional<IdentityUser> one(String sql, Object... args) {
        List<IdentityUser> rows = jdbc.query(sql, (rs, i) -> map(rs), args);
        return rows.stream().findFirst();
    }

    private static IdentityUser map(ResultSet rs) throws SQLException {
        Timestamp authorized = rs.getTimestamp("authorization_verified_at");
        return new IdentityUser(
                rs.getLong("user_id"),
                rs.getString("tenant_id"),
                rs.getString("actor_key"),
                rs.getString("phone_number"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("nickname"),
                rs.getString("account_status"),
                rs.getInt("is_admin") == 1,
                authorized == null ? null : authorized.toLocalDateTime());
    }

    static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
