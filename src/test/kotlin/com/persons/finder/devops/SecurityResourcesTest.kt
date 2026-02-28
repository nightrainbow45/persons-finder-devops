package com.persons.finder.devops

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Unit tests for Kubernetes RBAC and Security resource templates.
 * Validates: Requirements 3.1, 1.1
 *
 * Tests ServiceAccount, RBAC (Role/RoleBinding), NetworkPolicy,
 * and ImagePullSecret Helm templates for correct structure and configuration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityResourcesTest {

    private val templatesDir = Paths.get(
        System.getProperty("user.dir"),
        "devops", "helm", "persons-finder", "templates"
    )

    // ========== ServiceAccount Tests ==========

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ServiceAccountTemplateTest {

        private lateinit var content: String
        private lateinit var lines: List<String>

        @BeforeAll
        fun setup() {
            val path = templatesDir.resolve("serviceaccount.yaml")
            assertTrue(Files.isRegularFile(path), "serviceaccount.yaml should exist")
            content = Files.readString(path)
            lines = content.lines()
        }

        @Test
        fun `template should define apiVersion v1`() {
            assertTrue(
                lines.any { it.trim().startsWith("apiVersion:") && it.contains("v1") },
                "ServiceAccount template should define apiVersion: v1"
            )
        }

        @Test
        fun `template should define kind ServiceAccount`() {
            assertTrue(
                lines.any { it.trim().startsWith("kind:") && it.contains("ServiceAccount") },
                "Template should define kind: ServiceAccount"
            )
        }

        @Test
        fun `template should be conditional on serviceAccount create`() {
            assertTrue(
                content.contains(".Values.serviceAccount.create"),
                "Template should be conditionally rendered based on .Values.serviceAccount.create"
            )
        }

        @Test
        fun `template should use serviceAccountName helper for name`() {
            assertTrue(
                content.contains("persons-finder.serviceAccountName"),
                "Template should use persons-finder.serviceAccountName helper"
            )
        }

        @Test
        fun `template should use common labels helper`() {
            assertTrue(
                content.contains("persons-finder.labels"),
                "Template should use persons-finder.labels helper"
            )
        }

        @Test
        fun `template should support annotations for IRSA`() {
            assertTrue(
                content.contains(".Values.serviceAccount.annotations"),
                "Template should support annotations from values for IAM role association (IRSA)"
            )
        }

        @Test
        fun `template should set automountServiceAccountToken`() {
            assertTrue(
                content.contains("automountServiceAccountToken"),
                "Template should configure automountServiceAccountToken"
            )
        }
    }

    // ========== RBAC Tests ==========

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class RbacTemplateTest {

        private lateinit var content: String
        private lateinit var lines: List<String>

        @BeforeAll
        fun setup() {
            val path = templatesDir.resolve("rbac.yaml")
            assertTrue(Files.isRegularFile(path), "rbac.yaml should exist")
            content = Files.readString(path)
            lines = content.lines()
        }

        @Test
        fun `template should be conditional on rbac create`() {
            assertTrue(
                content.contains(".Values.rbac.create"),
                "Template should be conditionally rendered based on .Values.rbac.create"
            )
        }

        @Test
        fun `template should define a Role resource`() {
            assertTrue(
                lines.any { it.trim() == "kind: Role" },
                "Template should define kind: Role"
            )
        }

        @Test
        fun `template should define a RoleBinding resource`() {
            assertTrue(
                lines.any { it.trim() == "kind: RoleBinding" },
                "Template should define kind: RoleBinding"
            )
        }

        @Test
        fun `template should use rbac authorization apiVersion`() {
            assertTrue(
                content.contains("rbac.authorization.k8s.io/v1"),
                "Template should use apiVersion: rbac.authorization.k8s.io/v1"
            )
        }

        @Test
        fun `Role should have minimal permissions for configmaps and secrets`() {
            assertTrue(
                content.contains("configmaps") && content.contains("secrets"),
                "Role should grant access to configmaps and secrets"
            )
        }

        @Test
        fun `Role should only allow read verbs`() {
            assertTrue(
                content.contains("get") && content.contains("list") && content.contains("watch"),
                "Role should allow get, list, watch verbs"
            )
            assertFalse(
                content.contains("\"create\"") || content.contains("\"update\"") ||
                    content.contains("\"delete\"") || content.contains("\"patch\""),
                "Role should NOT allow create, update, delete, or patch verbs (minimal permissions)"
            )
        }

        @Test
        fun `RoleBinding should reference ServiceAccount`() {
            assertTrue(
                content.contains("persons-finder.serviceAccountName"),
                "RoleBinding should reference the ServiceAccount using the helper"
            )
        }

        @Test
        fun `RoleBinding should reference the Role`() {
            assertTrue(
                content.contains("roleRef:"),
                "RoleBinding should have a roleRef section"
            )
            assertTrue(
                content.contains("kind: Role"),
                "RoleBinding roleRef should reference kind: Role"
            )
        }

        @Test
        fun `RoleBinding should include namespace`() {
            assertTrue(
                content.contains(".Release.Namespace"),
                "RoleBinding should reference .Release.Namespace for the subject"
            )
        }

        @Test
        fun `template should use fullname helper for names`() {
            assertTrue(
                content.contains("persons-finder.fullname"),
                "Template should use persons-finder.fullname helper for resource names"
            )
        }

        @Test
        fun `template should use common labels helper`() {
            assertTrue(
                content.contains("persons-finder.labels"),
                "Template should use persons-finder.labels helper"
            )
        }
    }

    // ========== NetworkPolicy Tests ==========

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class NetworkPolicyTemplateTest {

        private lateinit var content: String
        private lateinit var lines: List<String>

        @BeforeAll
        fun setup() {
            val path = templatesDir.resolve("networkpolicy.yaml")
            assertTrue(Files.isRegularFile(path), "networkpolicy.yaml should exist")
            content = Files.readString(path)
            lines = content.lines()
        }

        @Test
        fun `template should be conditional on networkPolicy enabled`() {
            assertTrue(
                content.contains(".Values.networkPolicy.enabled"),
                "Template should be conditionally rendered based on .Values.networkPolicy.enabled"
            )
        }

        @Test
        fun `template should define kind NetworkPolicy`() {
            assertTrue(
                lines.any { it.trim() == "kind: NetworkPolicy" },
                "Template should define kind: NetworkPolicy"
            )
        }

        @Test
        fun `template should use networking k8s io apiVersion`() {
            assertTrue(
                content.contains("networking.k8s.io/v1"),
                "Template should use apiVersion: networking.k8s.io/v1"
            )
        }

        @Test
        fun `template should use selectorLabels for podSelector`() {
            assertTrue(
                content.contains("persons-finder.selectorLabels"),
                "Template should use persons-finder.selectorLabels for podSelector"
            )
        }

        @Test
        fun `template should reference policyTypes from values`() {
            assertTrue(
                content.contains(".Values.networkPolicy.policyTypes"),
                "Template should reference .Values.networkPolicy.policyTypes"
            )
        }

        @Test
        fun `template should reference ingress rules from values`() {
            assertTrue(
                content.contains(".Values.networkPolicy.ingress"),
                "Template should reference .Values.networkPolicy.ingress for ingress rules"
            )
        }

        @Test
        fun `template should reference egress rules from values`() {
            assertTrue(
                content.contains(".Values.networkPolicy.egress"),
                "Template should reference .Values.networkPolicy.egress for egress rules"
            )
        }

        @Test
        fun `template should use fullname helper for name`() {
            assertTrue(
                content.contains("persons-finder.fullname"),
                "Template should use persons-finder.fullname helper for metadata name"
            )
        }

        @Test
        fun `template should use common labels helper`() {
            assertTrue(
                content.contains("persons-finder.labels"),
                "Template should use persons-finder.labels helper"
            )
        }
    }

    // ========== ImagePullSecret Tests ==========

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ImagePullSecretTemplateTest {

        private lateinit var content: String
        private lateinit var lines: List<String>

        @BeforeAll
        fun setup() {
            val path = templatesDir.resolve("imagepullsecret.yaml")
            assertTrue(Files.isRegularFile(path), "imagepullsecret.yaml should exist")
            content = Files.readString(path)
            lines = content.lines()
        }

        @Test
        fun `template should iterate over imagePullSecrets`() {
            assertTrue(
                content.contains(".Values.imagePullSecrets"),
                "Template should reference .Values.imagePullSecrets"
            )
        }

        @Test
        fun `template should define kind Secret`() {
            assertTrue(
                lines.any { it.trim() == "kind: Secret" },
                "Template should define kind: Secret"
            )
        }

        @Test
        fun `template should use dockerconfigjson type`() {
            assertTrue(
                content.contains("kubernetes.io/dockerconfigjson"),
                "Template should use type: kubernetes.io/dockerconfigjson for ECR authentication"
            )
        }

        @Test
        fun `template should include dockerconfigjson data key`() {
            assertTrue(
                content.contains(".dockerconfigjson"),
                "Template should include .dockerconfigjson data key"
            )
        }

        @Test
        fun `template should use b64enc for encoding`() {
            assertTrue(
                content.contains("b64enc"),
                "Template should use b64enc for base64 encoding the docker config"
            )
        }

        @Test
        fun `template should include ECR credential refresh documentation`() {
            val hasEcrDoc = content.contains("ECR") || content.contains("ecr")
            assertTrue(
                hasEcrDoc,
                "Template should include documentation about ECR credential refresh"
            )
        }

        @Test
        fun `template should document automatic credential refresh approaches`() {
            val hasRefreshDoc = content.contains("refresh") || content.contains("IRSA") ||
                content.contains("credential-helper") || content.contains("CronJob")
            assertTrue(
                hasRefreshDoc,
                "Template should document approaches for automatic credential refresh"
            )
        }

        @Test
        fun `template should use common labels helper`() {
            assertTrue(
                content.contains("persons-finder.labels"),
                "Template should use persons-finder.labels helper"
            )
        }
    }
}
