package io.github.cyfko.dverify;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cyfko.dverify.exceptions.DataExtractionException;
import io.github.cyfko.dverify.exceptions.JsonEncodingException;
import io.github.cyfko.dverify.impl.GenericSignerVerifier;
import io.github.cyfko.dverify.impl.db.DatabaseBroker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DatabaseTest {

    private final JdbcDatabaseContainer<?> sqlContainer;
    private Signer signer;
    private Verifier verifier;
    private Revoker revoker;
    private DataSource dataSource;

    public DatabaseTest(JdbcDatabaseContainer<?> sqlContainer){
        this.sqlContainer = sqlContainer;
        sqlContainer.start();
    }

    private DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(sqlContainer.getJdbcUrl());
        config.setUsername(sqlContainer.getUsername());
        config.setPassword(sqlContainer.getPassword());
        config.setDriverClassName(sqlContainer.getDriverClassName());
        config.setMaximumPoolSize(5);  // tune per your load
        return new HikariDataSource(config);
    }

    @BeforeAll
    public void setUpClass() {
        dataSource = createDataSource();

        Broker broker = new DatabaseBroker(dataSource, "broker_messages");
        GenericSignerVerifier genericSignerVerifier = new GenericSignerVerifier(broker);
        signer = genericSignerVerifier;
        verifier = genericSignerVerifier;
        revoker = genericSignerVerifier;
    }

    @AfterAll
    public void tearDownClass() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
        sqlContainer.stop();
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_method_with_valid_data_should_returns_token(TokenMode mode) throws JsonEncodingException {
        UserData data = new UserData("john.doe@example.com");

        String token = signer.sign(data, 60, mode, mode.name().hashCode());

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_method_with_invalid_data_should_throws_exception(TokenMode mode) {
        Object invalidData = null; // Simulating invalid data

        Assertions.assertThrows(IllegalArgumentException.class, () -> signer.sign(invalidData, 60, mode, mode.name().hashCode() + 1));
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_method_with_expired_duration_should_throws_exception(TokenMode mode) {
        UserData data = new UserData("john.doe@example.com");

        Assertions.assertThrows(IllegalArgumentException.class, () -> signer.sign(data, -5, mode, mode.name().hashCode() + 2));
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_valid_data_should_returns_token(TokenMode mode) throws JsonEncodingException {
        UserData data = new UserData("john.doe@example.com");

        String token = signer.sign(data, 60, mode, mode.name().hashCode() + 3);

        assertNotNull(token, "JWT should not be null");
        assertFalse(token.isEmpty(), "JWT should not be empty");
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_invalid_data_should_throws_exception(TokenMode mode) {
        Object invalidData = null; // Simulating invalid data

        assertThrows(IllegalArgumentException.class, () -> signer.sign(invalidData, 60, mode, mode.name().hashCode() + 4));
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void verify_valid_token_should_returns_payload(TokenMode mode) {
        UserData data = new UserData("john.doe@example.com");
        String token = signer.sign(data, 600, mode, mode.name().hashCode() + 5); // Generate a valid token
        //Thread.sleep(5000); // Wait 5 secs to ensure that the keys has been propagated to database

        UserData verifiedData = verifier.verify(token, UserData.class);

        assertNotNull(verifiedData);
        assertEquals(data.getEmail(), verifiedData.getEmail());
    }

    @Test
    public void verify_invalid_token_should_throws_exception() {
        String invalidToken = "invalid.token.token"; // Simulating an invalid token

        assertThrows(DataExtractionException.class, () -> verifier.verify(invalidToken, UserData.class));
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void verify_expired_token_should_throws_exception(TokenMode mode) throws InterruptedException {
        UserData data = new UserData("john.doe@example.com");
        String token = signer.sign(data, 1, mode, mode.name().hashCode() + 6); // Token with short duration
        Thread.sleep(3000); // Wait 3 seconds for the token to expire

        assertThrows(DataExtractionException.class, () -> verifier.verify(token, UserData.class));
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void verify_revoked_token_should_throws_exception(TokenMode mode) throws InterruptedException {
        UserData data = new UserData("john.doe@example.com");
        String token = signer.sign(data, 3600, mode, mode.name().hashCode() + 7); // Token with long duration (1 hour)
        Thread.sleep(2000); // Wait 2 seconds before issuing the revocation command

        revoker.revoke(token);
        Thread.sleep(2000); // Wait 2 seconds to ensure revocation took place

        assertThrows(DataExtractionException.class, () -> verifier.verify(token, UserData.class));
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void verify_revoked_token_by_tracked_id_should_throws_exception(TokenMode mode) throws InterruptedException {
        UserData data = new UserData("john.doe@example.com");
        final long trackingId = mode.name().hashCode() + 8;

        String token = signer.sign(data, 3600, mode, trackingId); // Token with long duration (1 hour)
        Thread.sleep(2000); // Wait 2 seconds before issuing the revocation command

        revoker.revoke(trackingId);
        Thread.sleep(2000); // Wait 2 seconds to ensure revocation took place

        assertThrows(DataExtractionException.class, () -> verifier.verify(token, UserData.class));
    }
}
