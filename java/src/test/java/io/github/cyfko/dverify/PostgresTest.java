package io.github.cyfko.dverify;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class PostgresTest extends DatabaseTest {
    public PostgresTest() {
        super(new PostgreSQLContainer<>(DockerImageName.parse("postgres:15")));
    }
}
