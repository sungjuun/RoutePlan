package com.routeplan.testsupport;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PostgisTestContainer {

    private static final DockerImageName IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.5")
            .asCompatibleSubstituteFor("postgres");

    private PostgisTestContainer() {
    }

    public static PostgreSQLContainer create() {
        return new PostgreSQLContainer(IMAGE);
    }
}
