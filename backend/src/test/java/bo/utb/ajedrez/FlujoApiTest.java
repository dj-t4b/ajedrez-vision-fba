package bo.utb.ajedrez;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Recorre el flujo principal de la API contra H2: login, roles, partida y jugadas validadas. */
@SpringBootTest
@AutoConfigureMockMvc
class FlujoApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void flujoCompleto() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/partidas")).andExpect(status().isUnauthorized());
        llamar(post("/api/auth/login"), null, "{\"email\":\"admin@fba.bo\",\"password\":\"mala\"}")
                .andExpect(status().isUnauthorized());

        String admin = login("admin@fba.bo", "admin123");

        // ADMIN crea un árbitro y un jugador
        llamar(post("/api/usuarios"), admin, "{\"nombre\":\"Árbitro\",\"email\":\"arbitro@fba.bo\",\"password\":\"arbitro1\",\"rol\":\"ARBITRO\"}")
                .andExpect(status().isCreated());
        llamar(post("/api/usuarios"), admin, "{\"nombre\":\"Jugador\",\"email\":\"jugador@fba.bo\",\"password\":\"jugador1\",\"rol\":\"JUGADOR\"}")
                .andExpect(status().isCreated());
        llamar(post("/api/usuarios"), admin, "{\"nombre\":\"Dup\",\"email\":\"ARBITRO@fba.bo\",\"password\":\"xxxxxx\",\"rol\":\"JUGADOR\"}")
                .andExpect(status().isUnprocessableEntity());

        String arbitro = login("arbitro@fba.bo", "arbitro1");
        String jugador = login("jugador@fba.bo", "jugador1");

        // Roles: el jugador no gestiona usuarios ni crea partidas
        mvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + jugador)).andExpect(status().isForbidden());
        String partidaJson = "{\"torneo\":\"Abierto FBA\",\"ronda\":1,\"blancas\":\"Ana\",\"negras\":\"Luis\"}";
        llamar(post("/api/partidas"), jugador, partidaJson).andExpect(status().isForbidden());

        JsonNode creada = leer(llamar(post("/api/partidas"), arbitro, partidaJson).andExpect(status().isCreated()));
        long id = creada.at("/partida/id").asLong();

        // Jugada ilegal rechazada, formato inválido y recurso inexistente
        jugar(arbitro, id, "e2e5").andExpect(status().isUnprocessableEntity());
        jugar(arbitro, id, "peon").andExpect(status().isBadRequest());
        mvc.perform(get("/api/partidas/999").header("Authorization", "Bearer " + arbitro)).andExpect(status().isNotFound());
        mvc.perform(get("/api/no-existe").header("Authorization", "Bearer " + arbitro)).andExpect(status().isNotFound());

        // Mate del loco: 1. f3 e5 2. g4 Qh4#
        jugar(arbitro, id, "f2f3").andExpect(status().isOk());
        jugar(arbitro, id, "e7e5").andExpect(status().isOk());
        jugar(arbitro, id, "g2g4").andExpect(status().isOk());
        JsonNode fin = leer(jugar(arbitro, id, "d8h4").andExpect(status().isOk()));
        assert "FINALIZADA".equals(fin.at("/partida/estado").asText());
        assert "0-1".equals(fin.at("/partida/resultado").asText());
        assert "1. f3 e5 2. g4 Qh4# 0-1".equals(fin.at("/pgn").asText()) : fin.at("/pgn").asText();
        jugar(arbitro, id, "a2a3").andExpect(status().isUnprocessableEntity());

        // Deshacer reabre la partida
        JsonNode des = leer(llamar(delete("/api/partidas/" + id + "/jugadas/ultima"), arbitro, null).andExpect(status().isOk()));
        assert "EN_CURSO".equals(des.at("/partida/estado").asText());
        assert des.at("/jugadas").size() == 3;

        // El jugador sí puede consultar
        mvc.perform(get("/api/partidas/" + id).header("Authorization", "Bearer " + jugador)).andExpect(status().isOk());
        JsonNode dash = leer(mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        assert dash.at("/jugadasVision").asLong() == 3;

        // No se puede borrar al único admin
        long adminId = leer(mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + admin))).at("/id").asLong();
        llamar(delete("/api/usuarios/" + adminId), admin, null).andExpect(status().isUnprocessableEntity());
    }

    private ResultActions jugar(String token, long id, String uci) throws Exception {
        return llamar(post("/api/partidas/" + id + "/jugadas"), token,
                "{\"uci\":\"" + uci + "\",\"origen\":\"VISION\",\"confianza\":0.9}");
    }

    private String login(String email, String password) throws Exception {
        return leer(llamar(post("/api/auth/login"), null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}").andExpect(status().isOk()))
                .get("token").asText();
    }

    private ResultActions llamar(MockHttpServletRequestBuilder req, String token, String cuerpo) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (cuerpo != null) req.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        return mvc.perform(req);
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
