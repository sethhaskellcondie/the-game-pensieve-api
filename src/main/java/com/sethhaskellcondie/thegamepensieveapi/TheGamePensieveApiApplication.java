package com.sethhaskellcondie.thegamepensieveapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication is the same as @Configuration, @EnableAutoConfiguration, and @ComponentScan
// There should only be one @SpringBootApplication annotation used in a project
//
// NO CORS CONFIGURATION, deliberately. This app used to register a credentialed CORS mapping for
// http://localhost:4200 on every path, in every profile — a leftover from the era when a browser talked to
// this API directly. Nothing does any more: the web app is a BFF, its API_BASE_URL is server-only and never
// reaches the browser (see the-game-pensieve-web-v2/src/lib/apiBase.ts), and the MCP sidecar is a server
// too. In production the backend publishes no host port at all and is reachable only on the compose
// network, so a browser cannot make a cross-origin request to it in the first place.
//
// With no mapping registered Spring sends no Access-Control-Allow-Origin header, so browsers refuse every
// cross-origin read — which is the correct posture for an API with no browser clients. If a genuine browser
// client is ever added, add a mapping scoped to that origin; do not restore a wildcard path with
// allowCredentials(true).
@SpringBootApplication
public class TheGamePensieveApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TheGamePensieveApiApplication.class, args);
    }
}
