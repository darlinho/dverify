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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DatabaseTest {

    private final JdbcDatabaseContainer<?> sqlContainer;
    private Signer signer;
    private Verifier verifier;

    public DatabaseTest(JdbcDatabaseContainer<?> sqlContainer){
        this.sqlContainer = sqlContainer;
    }

    private DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(sqlContainer.getJdbcUrl());
        config.setUsername(sqlContainer.getUsername());
        config.setPassword(sqlContainer.getPassword());
        config.setDriverClassName(sqlContainer.getDriverClassName());
        return new HikariDataSource(config);
    }

    @BeforeAll
    public void setUpClass() { sqlContainer.start(); }

    @AfterAll
    public void tearDownClass() {
        sqlContainer.stop();
    }

    @BeforeEach
    public void setUp() throws IOException {
        Broker broker = new DatabaseBroker(createDataSource(), "broker_messages");
        GenericSignerVerifier genericSignerVerifier = new GenericSignerVerifier(broker);
        signer = genericSignerVerifier;
        verifier = genericSignerVerifier;
    }

    @AfterEach
    public void tearDown() {

    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_method_with_valid_data_should_returns_token(TokenMode mode) throws JsonEncodingException {
        UserData data = new UserData("john.doe@example.com");
        Duration duration = Duration.ofHours(2);

        String token = signer.sign(data, duration, mode);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_method_with_invalid_data_should_throws_exception(TokenMode mode) {
        Object invalidData = null; // Simulating invalid data
        Duration duration = Duration.ofHours(2);

        Assertions.assertThrows(IllegalArgumentException.class, () -> signer.sign(invalidData, duration, mode));
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_method_with_expired_duration_should_throws_exception(TokenMode mode) {
        UserData data = new UserData("john.doe@example.com");
        Duration duration = Duration.ofMinutes(-5); // Negative duration

        Assertions.assertThrows(IllegalArgumentException.class, () -> signer.sign(data, duration, mode));
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_valid_data_should_returns_token(TokenMode mode) throws JsonEncodingException {
        UserData data = new UserData("john.doe@example.com");
        Duration duration = Duration.ofHours(2);

        String token = signer.sign(data, duration, mode);

        assertNotNull(token, "JWT should not be null");
        assertFalse(token.isEmpty(), "JWT should not be empty");
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void sign_invalid_data_should_throws_exception(TokenMode mode) {
        Object invalidData = null; // Simulating invalid data
        Duration duration = Duration.ofHours(2);

        assertThrows(IllegalArgumentException.class, () -> signer.sign(invalidData, duration, mode));
    }

    @ParameterizedTest()
    @EnumSource(value = TokenMode.class)
    public void verify_valid_token_should_returns_payload(TokenMode mode) throws InterruptedException {
        UserData data = new UserData("john.doe@example.com");
        String token = signer.sign(data, Duration.ofHours(2), mode); // Generate a valid token
        Thread.sleep(5000); // Wait 5 secs to ensure that the keys has been propagated to kafka

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
        String token = signer.sign(data, Duration.ofMillis(1), mode); // Token with short duration
        Thread.sleep(10); // Wait for the token to expire

        assertThrows(DataExtractionException.class, () -> verifier.verify(token, UserData.class));
    }
}
