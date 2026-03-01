import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.7.18"
	id("io.spring.dependency-management") version "1.1.4"
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	kotlin("plugin.jpa") version "1.9.25"
}

group = "com.persons.finder"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

// Security: override BOM-managed versions to patch CVEs without upgrading to Spring Boot 3.x
ext["tomcat.version"] = "9.0.109"           // fixes CRITICAL CVE-2025-24813 + 5 HIGH Tomcat CVEs
ext["logback.version"] = "1.2.13"           // fixes CVE-2023-6378, CVE-2023-6481
ext["spring-framework.version"] = "5.3.34"  // fixes CVE-2024-22243, CVE-2024-22259, CVE-2024-22262
ext["jackson-bom.version"] = "2.15.4"       // fixes CVE-2025-52999
ext["snakeyaml.version"] = "1.33"           // fixes CVE-2022-25857

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.h2database:h2:2.2.220")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("net.jqwik:jqwik:1.7.4")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "11"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
