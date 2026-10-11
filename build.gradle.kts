plugins {
    application
}

version = "0.1.0"

repositories { mavenCentral() }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation("org.apache.sshd:sshd-core:2.20.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.81") // Ed25519 OpenSSH keys with MINA SSHD 2.x
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.slf4j:slf4j-nop:2.0.17")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application { mainClass.set("it.flossconsulting.sshtunnel.Main") }
tasks.test { useJUnitPlatform() }

tasks.processResources {
    from("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md")
    from("licenses") { into("licenses") }
}
