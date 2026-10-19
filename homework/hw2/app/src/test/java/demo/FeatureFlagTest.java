package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppController.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FeatureFlagTest {

    @Autowired
    MockMvc mvc;

    @Test
    void flagIsOffByDefault() throws Exception {
        mvc.perform(get("/message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AppController.OLD_MESSAGE))
                .andExpect(jsonPath("$.newGreeting").value(false));
        mvc.perform(get("/admin/feature"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void enablingChangesMessage() throws Exception {
        mvc.perform(get("/admin/feature").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(get("/message"))
                .andExpect(jsonPath("$.message").value(AppController.NEW_MESSAGE))
                .andExpect(jsonPath("$.newGreeting").value(true));
    }

    @Test
    void disablingRestoresOldMessage() throws Exception {
        mvc.perform(get("/admin/feature").param("enabled", "true")).andExpect(status().isOk());
        mvc.perform(get("/admin/feature").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/message"))
                .andExpect(jsonPath("$.message").value(AppController.OLD_MESSAGE));
    }

    @Test
    void statusWithoutParamDoesNotChangeFlag() throws Exception {
        mvc.perform(get("/admin/feature").param("enabled", "true")).andExpect(status().isOk());
        mvc.perform(get("/admin/feature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void invalidValueIsRejectedAndFlagUnchanged() throws Exception {
        mvc.perform(get("/admin/feature").param("enabled", "abc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/message"))
                .andExpect(jsonPath("$.message").value(AppController.OLD_MESSAGE));
    }

    @Test
    void invalidValueDoesNotTurnOffEnabledFlag() throws Exception {
        mvc.perform(get("/admin/feature").param("enabled", "true")).andExpect(status().isOk());
        mvc.perform(get("/admin/feature").param("enabled", "maybe"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/message"))
                .andExpect(jsonPath("$.message").value(AppController.NEW_MESSAGE));
    }
}