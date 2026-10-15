package com.example.elsa_store.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mỗi loại lỗi ra đúng HTTP status; lỗi 500 không để lộ thông điệp nội bộ. */
class GlobalExceptionHandlerTest {

    @RestController
    static class Endpoints {
        record Body(@NotBlank String name) {}

        @GetMapping("/illegal")
        String illegal() {
            throw new IllegalArgumentException("Bạn phải truyền status");
        }

        @GetMapping("/param")
        String param(@RequestParam Long orderId) {
            return "ok " + orderId;
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody Body body) {
            return body.name();
        }

        @GetMapping("/integrity")
        String integrity() {
            throw new DataIntegrityViolationException("FK products.category_id");
        }

        @GetMapping("/lock")
        String lock() {
            throw new CannotAcquireLockException("Deadlock found when trying to get lock");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("password=secret at line 42");
        }

        @GetMapping("/app")
        String app() {
            throw new ResourceNotFoundException("Order not found");
        }

        @GetMapping("/multipart")
        String multipart() {
            throw new MultipartException("Failed to parse multipart servlet request");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoints())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void wrongBodyFormatForAFileEndpointIsABadRequestNotA500() throws Exception {
        mvc.perform(get("/multipart"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Expected a multipart/form-data request"));
    }

    @Test
    void illegalArgumentIsABadRequestWithItsMessage() throws Exception {
        mvc.perform(get("/illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1010))
                .andExpect(jsonPath("$.message").value("Bạn phải truyền status"));
    }

    @Test
    void missingAndMistypedParametersAreValidationErrors() throws Exception {
        mvc.perform(get("/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalExceptionHandler.VALIDATION_CODE))
                .andExpect(jsonPath("$.message").value("orderId: must not be null"));
        mvc.perform(get("/param").param("orderId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("orderId: invalid value"));
    }

    @Test
    void invalidAndMalformedBodies() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalExceptionHandler.VALIDATION_CODE))
                .andExpect(jsonPath("$.message", containsString("name:")));
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mvc.perform(put("/illegal")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void constraintViolationsAndLocksAreConflicts() throws Exception {
        mvc.perform(get("/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DATA_IN_USE.getCode()))
                .andExpect(jsonPath("$.message", not(containsString("FK"))));
        mvc.perform(get("/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_UPDATE.getCode()));
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(999))
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    @Test
    void appExceptionsKeepTheirOwnStatus() throws Exception {
        mvc.perform(get("/app"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1009))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }
}
