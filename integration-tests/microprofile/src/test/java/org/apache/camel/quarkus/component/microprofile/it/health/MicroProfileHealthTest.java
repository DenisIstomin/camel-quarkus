/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.component.microprofile.it.health;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.apache.camel.ServiceStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MicroProfileHealthTest {

    @Test
    public void testHealthUpStatus() {
        RestAssured.when().get("/q/health").then()
                .contentType(ContentType.JSON)
                .header("Content-Type", containsString("charset=UTF-8"))
                .body("status", is("UP"),
                        "checks.status.findAll().unique()", contains("UP"),
                        "checks.find { it.name == 'camel-routes' }", notNullValue(),
                        "checks.find { it.name == 'camel-consumers' }", notNullValue(),
                        "checks.find { it.name == 'context' }", notNullValue(),
                        "checks.find { it.name == 'context' }.data.'context.name'", notNullValue());
    }

    @Test
    public void testHealthDownStatus() {
        try {
            RestAssured.given()
                    .queryParam("healthCheckEnabled", "true")
                    .post("/microprofile-health/failing-check")
                    .then()
                    .statusCode(204);

            RestAssured.when().get("/q/health").then()
                    .contentType(ContentType.JSON)
                    .header("Content-Type", containsString("charset=UTF-8"))
                    .body("status", is("DOWN"),
                            "checks.findAll { it.name == 'failing-check' }.status", contains("DOWN", "DOWN"),
                            "checks.findAll { it.name != 'failing-check' }.status.unique()", contains("UP"));
        } finally {
            RestAssured.given()
                    .queryParam("healthCheckEnabled", "false")
                    .post("/microprofile-health/failing-check")
                    .then()
                    .statusCode(204);
        }
    }

    @Test
    public void testLivenessUpStatus() {
        RestAssured.when().get("/q/health/live").then()
                .contentType(ContentType.JSON)
                .header("Content-Type", containsString("charset=UTF-8"))
                .body("status", is("UP"),
                        "checks.status.findAll().unique()", contains("UP"),
                        "checks.find { it.name == 'test-liveness' }.data.isLive as Boolean", is(true));
    }

    @Test
    public void testLivenessDownStatus() {
        try {
            RestAssured.given()
                    .queryParam("healthCheckEnabled", "true")
                    .post("/microprofile-health/failing-check")
                    .then()
                    .statusCode(204);

            RestAssured.when().get("/q/health/live").then()
                    .contentType(ContentType.JSON)
                    .header("Content-Type", containsString("charset=UTF-8"))
                    .body("status", is("DOWN"),
                            "checks.find { it.name == 'failing-check' }.status", is("DOWN"),
                            "checks.findAll { it.name != 'failing-check' }.status.unique()", contains("UP"));
        } finally {
            RestAssured.given()
                    .queryParam("healthCheckEnabled", "false")
                    .post("/microprofile-health/failing-check")
                    .then()
                    .statusCode(204);
        }
    }

    @Test
    public void testReadinessUpStatus() {
        RestAssured.when().get("/q/health/ready").then()
                .contentType(ContentType.JSON)
                .header("Content-Type", containsString("charset=UTF-8"))
                .body("status", is("UP"),
                        "checks.status.findAll().unique()", contains("UP"),
                        "checks.find { it.name == 'test-readiness' }.data.isReady as Boolean", is(true));
    }

    @Test
    public void testReadinessDownStatus() {
        try {
            RestAssured.given()
                    .queryParam("healthCheckEnabled", "true")
                    .post("/microprofile-health/failing-check")
                    .then()
                    .statusCode(204);

            RestAssured.when().get("/q/health/ready").then()
                    .contentType(ContentType.JSON)
                    .header("Content-Type", containsString("charset=UTF-8"))
                    .body("status", is("DOWN"),
                            "checks.find { it.name == 'failing-check' }.status", is("DOWN"),
                            "checks.findAll { it.name != 'failing-check' }.status.unique()", contains("UP"));
        } finally {
            RestAssured.given()
                    .queryParam("healthCheckEnabled", "false")
                    .post("/microprofile-health/failing-check")
                    .then()
                    .statusCode(204);
        }
    }

    @Test
    public void testRouteStoppedDownStatus() {
        try {
            RestAssured.post("/microprofile-health/route/healthyRoute/stop")
                    .then()
                    .statusCode(204);

            RestAssured.when().get("/q/health").then()
                    .contentType(ContentType.JSON)
                    .header("Content-Type", containsString("charset=UTF-8"))
                    .body("status", is("DOWN"),
                            "checks.find { it.name == 'camel-routes' }.status", is("DOWN"),
                            "checks.find { it.name == 'camel-routes' }.data.'route.id'", is("healthyRoute"),
                            "checks.find { it.name == 'camel-routes' }.data.'route.status'",
                            is(ServiceStatus.Stopped.toString()),
                            "checks.find { it.name == 'camel-consumers' }.status", is("DOWN"),
                            "checks.find { it.name == 'camel-consumers' }.data.'route.id'", is("healthyRoute"),
                            "checks.find { it.name == 'camel-consumers' }.data.'route.status'",
                            is(ServiceStatus.Stopped.toString()));
        } finally {
            RestAssured.post("/microprofile-health/route/healthyRoute/start")
                    .then()
                    .statusCode(204);
        }
    }

    @Test
    public void testFailureThreshold() {
        try {
            RestAssured.given()
                    .queryParam("healthCheckEnabled", "true")
                    .post("/microprofile-health/failure-threshold")
                    .then()
                    .statusCode(204);

            // Configured failure threshold and interval should allow the initial health state be UP
            RestAssured.when().get("/q/health").then()
                    .contentType(ContentType.JSON)
                    .header("Content-Type", containsString("charset=UTF-8"))
                    .body("status", is("UP"),
                            "checks.findAll { it.name == 'failure-threshold' }.status.unique()", contains("UP"));

            // Poll the health endpoint until the threshold / interval is exceeded and the health state transitions to DOWN
            Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(50, TimeUnit.MILLISECONDS).until(() -> {
                JsonPath result = RestAssured.when().get("/q/health").then()
                        .contentType(ContentType.JSON)
                        .header("Content-Type", containsString("charset=UTF-8"))
                        .extract()
                        .jsonPath();

                String status = result.getString("status");
                List<String> routeStatus = result.getList("checks.findAll { it.name == 'failure-threshold' }.status.unique()");
                return status.equals("DOWN") && routeStatus.contains("DOWN");
            });

            RestAssured.given()
                    .queryParam("returnStatusUp", true)
                    .post("/microprofile-health/failure-threshold/return/status")
                    .then()
                    .statusCode(204);

            // Try again with a poll delay > the failure interval and wait for the health state to transition to UP
            Awaitility.await().atMost(10, TimeUnit.SECONDS).pollDelay(50, TimeUnit.MILLISECONDS).until(() -> {
                JsonPath result = RestAssured.when().get("/q/health").then()
                        .contentType(ContentType.JSON)
                        .header("Content-Type", containsString("charset=UTF-8"))
                        .extract()
                        .jsonPath();

                String status = result.getString("status");
                List<String> routeStatus = result.getList("checks.findAll { it.name == 'failure-threshold' }.status.unique()");
                return status.equals("UP") && routeStatus.contains("UP");
            });
        } finally {
            RestAssured.given()
                    .queryParam("returnStatusUp", false)
                    .post("/microprofile-health/failure-threshold/return/status")
                    .then()
                    .statusCode(204);

            RestAssured.given()
                    .queryParam("healthCheckEnabled", "false")
                    .post("/microprofile-health/failure-threshold")
                    .then()
                    .statusCode(204);
        }
    }
}
