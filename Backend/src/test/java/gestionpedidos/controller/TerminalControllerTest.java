package gestionpedidos.controller;

import gestionpedidos.service.TerminalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TerminalController.class)
public class TerminalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TerminalService terminalService;

    @Test
    void dummy() {
    }
}
