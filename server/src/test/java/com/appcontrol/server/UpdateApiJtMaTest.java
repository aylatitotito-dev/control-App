package com.appcontrol.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** La versión nueva de la aplicación se ofrece sin pedir token (no lleva datos de nadie). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:srvupd;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UpdateApiJtMaTest {

    @Autowired
    private MockMvc mvcMa;

    @Test
    void ofreceLaFichaYElArchivoSinToken() throws Exception {
        MvcResult infoMa = mvcMa.perform(get("/api/app/latest")).andReturn();
        assertEquals(200, infoMa.getResponse().getStatus());
        assertEquals(true, infoMa.getResponse().getContentAsString().contains("20261125120000"));
        MvcResult fileMa = mvcMa.perform(get("/api/app/download")).andReturn();
        assertEquals(200, fileMa.getResponse().getStatus());
        assertEquals("JAR-DE-PRUEBA", fileMa.getResponse().getContentAsString());
    }
}
