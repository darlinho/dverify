package io.github.cyfko.dverify;


import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public class MySQLTest extends DatabaseTest {
    public MySQLTest() {
        super(new MySQLContainer<>(DockerImageName.parse("mysql:lts")));
    }
}
