package com.persons.finder

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Test
    fun `health endpoint returns 200 when application is ready`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `health endpoint returns response with status field`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.components").exists())
    }

    @Test
    fun `health endpoint returns 503 when readiness state is refusing traffic`() {
        // Change readiness state to REFUSING_TRAFFIC
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC)

        try {
            mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"))
        } finally {
            // Restore readiness state
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC)
        }
    }

    @Test
    fun `liveness endpoint returns 200 when application is live`() {
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `liveness endpoint returns 503 when liveness state is broken`() {
        AvailabilityChangeEvent.publish(applicationContext, LivenessState.BROKEN)

        try {
            mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.status").value("DOWN"))
        } finally {
            AvailabilityChangeEvent.publish(applicationContext, LivenessState.CORRECT)
        }
    }
}
