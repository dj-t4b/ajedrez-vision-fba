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

/**
 * Recorre el flujo de un torneo contra H2: roles por torneo con su matriz de permisos,
 * vinculación de celulares con PIN, jugadas desde la cámara, corrección desde la web y reportes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FlujoApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void flujoCompleto() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/partidas")).andExpect(status().isUnauthorized());
        llamar(post("/api/auth/login"), null, "{\"email\":\"admin@fba.bo\",\"password\":\"mala\"}").andExpect(status().isUnauthorized());
        String admin = login("admin@fba.bo", "admin123");

        // Usuarios (rol global USUARIO); los roles se dan por torneo
        long org = usuario(admin, "Organizadora", "org@fba.bo");
        long ap = usuario(admin, "Árbitro principal", "ap@fba.bo");
        long arb1 = usuario(admin, "Árbitro uno", "arb1@fba.bo");
        long arb2 = usuario(admin, "Árbitro dos", "arb2@fba.bo");
        llamar(post("/api/usuarios"), admin, "{\"nombre\":\"Dup\",\"email\":\"ORG@fba.bo\",\"password\":\"xxxxxx\",\"rol\":\"USUARIO\"}")
                .andExpect(status().isUnprocessableEntity());
        String tOrg = login("org@fba.bo", "clave123"), tAp = login("ap@fba.bo", "clave123");
        String tArb1 = login("arb1@fba.bo", "clave123"), tArb2 = login("arb2@fba.bo", "clave123");

        // Solo el administrador crea torneos
        String torneoJson = "{\"nombre\":\"Abierto FBA\",\"categoria\":\"Absoluto\",\"ciudad\":\"La Paz\"}";
        llamar(post("/api/torneos"), tOrg, torneoJson).andExpect(status().isForbidden());
        long torneo = leer(llamar(post("/api/torneos"), admin, torneoJson).andExpect(status().isCreated())).get("id").asLong();
        long otro = leer(llamar(post("/api/torneos"), admin, "{\"nombre\":\"Otro\"}")).get("id").asLong();

        // Equipo: admin → organizador → árbitro principal → árbitros (con mesas)
        asignar(tOrg, torneo, org, "ORGANIZADOR").andExpect(status().isForbidden());
        asignar(admin, torneo, org, "ORGANIZADOR").andExpect(status().isCreated());
        asignar(tOrg, torneo, arb1, "ARBITRO").andExpect(status().isForbidden());
        asignar(tOrg, torneo, ap, "ARBITRO_PRINCIPAL").andExpect(status().isCreated());
        asignar(tAp, torneo, arb1, "ARBITRO").andExpect(status().isCreated());
        llamar(post("/api/torneos/" + torneo + "/asignaciones"), tAp,
                "{\"usuarioId\":" + arb2 + ",\"rol\":\"ARBITRO\",\"mesas\":[2]}").andExpect(status().isCreated());
        asignar(tAp, torneo, arb1, "ARBITRO").andExpect(status().isUnprocessableEntity()); // ya tiene rol

        // Cada uno ve solo sus torneos, con su rol
        JsonNode deArb2 = leer(llamar(get("/api/torneos"), tArb2, null));
        assert deArb2.size() == 1 && "ARBITRO".equals(deArb2.get(0).get("miRol").asText()) : deArb2;
        llamar(get("/api/torneos/" + otro), tArb2, null).andExpect(status().isForbidden());

        // El organizador edita el torneo; el árbitro principal no
        llamar(put("/api/torneos/" + torneo), tOrg, "{\"nombre\":\"Abierto FBA 2026\",\"categoria\":\"Absoluto\"}").andExpect(status().isOk());
        llamar(put("/api/torneos/" + torneo), tAp, torneoJson).andExpect(status().isForbidden());

        // Solo el árbitro principal carga partidas y jugadores; una partida en curso por mesa
        String mesa1 = "{\"torneoId\":" + torneo + ",\"mesa\":1,\"ronda\":1,\"blancas\":\"Ana\",\"negras\":\"Luis\"}";
        llamar(post("/api/partidas"), tOrg, mesa1).andExpect(status().isForbidden());
        long partida = leer(llamar(post("/api/partidas"), tAp, mesa1).andExpect(status().isCreated())).at("/partida/id").asLong();
        llamar(post("/api/partidas"), tAp, mesa1).andExpect(status().isUnprocessableEntity());
        llamar(post("/api/partidas"), tAp, "{\"torneoId\":" + torneo + ",\"mesa\":2,\"ronda\":1,\"blancas\":\"Eva\",\"negras\":\"Raúl\"}")
                .andExpect(status().isCreated());

        // Vinculación: PIN de un solo torneo; cada celular recibe código y la primera mesa libre
        llamar(post("/api/torneos/" + torneo + "/vinculacion"), tArb1, null).andExpect(status().isForbidden());
        String pin = leer(llamar(post("/api/torneos/" + torneo + "/vinculacion"), tAp, null).andExpect(status().isOk())).get("pin").asText();
        llamar(post("/api/dispositivo/vincular"), null, "{\"pin\":\"" + (pin.equals("000000") ? "111111" : "000000") + "\"}")
                .andExpect(status().isUnauthorized());
        JsonNode cel1 = leer(llamar(post("/api/dispositivo/vincular"), null, "{\"pin\":\"" + pin + "\"}").andExpect(status().isOk()));
        assert "C-01".equals(cel1.get("codigo").asText()) && cel1.get("mesa").asInt() == 1 : cel1;
        JsonNode cel2 = leer(llamar(post("/api/dispositivo/vincular"), null, "{\"pin\":\"" + pin + "\"}"));
        assert "C-02".equals(cel2.get("codigo").asText()) && cel2.get("mesa").asInt() == 2 : cel2;
        String d1 = cel1.get("token").asText();
        mvc.perform(get("/api/dispositivo/estado")).andExpect(status().isUnauthorized());
        JsonNode estado = leer(mvc.perform(get("/api/dispositivo/estado").header("X-Dispositivo", d1)).andExpect(status().isOk()));
        assert estado.at("/partida/partida/id").asLong() == partida;

        // El celular registra siempre la jugada más probable (mate del loco)
        camara(d1, "f2f3", 0.9).andExpect(status().isOk());
        camara(d1, "e7e5", 0.3).andExpect(status().isOk());
        camara(d1, "e2e5", 0.9).andExpect(status().isUnprocessableEntity()); // ilegal
        JsonNode p = leer(llamar(get("/api/partidas/" + partida), tArb1, null));
        assert "C-01 · Mesa 1".equals(p.at("/jugadas/0/registradaPor").asText()) : p;
        assert p.at("/permisos/arbitrar").asBoolean() && !p.at("/permisos/gestionar").asBoolean();

        // Corrección desde la web: el árbitro de esa mesa reemplaza la jugada 2; el de otra mesa no puede
        llamar(post("/api/partidas/" + partida + "/jugadas/2/corregir"), tArb2, "{\"uci\":\"e7e6\"}").andExpect(status().isForbidden());
        llamar(get("/api/partidas/" + partida), tArb2, null).andExpect(status().isForbidden());
        assert leer(llamar(get("/api/partidas?torneoId=" + torneo), tArb2, null)).size() == 1; // solo su mesa
        JsonNode corregida = leer(llamar(post("/api/partidas/" + partida + "/jugadas/2/corregir"), tArb1, "{\"uci\":\"e7e6\"}")
                .andExpect(status().isOk()));
        assert "1. f3 e6 *".equals(corregida.at("/pgn").asText()) : corregida.at("/pgn").asText();
        assert "Árbitro uno".equals(corregida.at("/jugadas/1/registradaPor").asText());

        // El organizador ve, pero no finaliza; el celular termina la partida; el árbitro principal la reabre
        llamar(get("/api/partidas/" + partida), tOrg, null).andExpect(status().isOk());
        llamar(patch("/api/partidas/" + partida + "/resultado"), tOrg, "{\"resultado\":\"1-0\"}").andExpect(status().isForbidden());
        JsonNode fin = leer(llamar(post("/api/dispositivo/resultado"), null, "{\"resultado\":\"1-0\"}", d1).andExpect(status().isOk()));
        assert "FINALIZADA".equals(fin.at("/partida/estado").asText());
        llamar(post("/api/partidas/" + partida + "/reabrir"), tArb1, null).andExpect(status().isForbidden());
        llamar(post("/api/partidas/" + partida + "/reabrir"), tAp, null).andExpect(status().isOk());

        // Mesas de los celulares: el árbitro principal reasigna; dos celulares no comparten mesa
        long idCel2 = leer(llamar(get("/api/torneos/" + torneo + "/dispositivos"), tAp, null)).get(1).get("id").asLong();
        llamar(put("/api/dispositivos/" + idCel2 + "/mesa"), tAp, "{\"mesa\":1}").andExpect(status().isUnprocessableEntity());
        llamar(put("/api/dispositivos/" + idCel2 + "/mesa"), tArb1, "{\"mesa\":3}").andExpect(status().isForbidden());
        llamar(put("/api/dispositivos/" + idCel2 + "/mesa"), tAp, "{\"mesa\":3}").andExpect(status().isOk());

        // Reportes: cualquier rol del torneo; el de todos los torneos, solo el administrador
        byte[] pdf = llamar(get("/api/reportes/partidas.pdf?torneoId=" + torneo), tArb1, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assert new String(pdf, 0, 5).equals("%PDF-");
        llamar(get("/api/reportes/partidas.pdf"), tOrg, null).andExpect(status().isForbidden());
        byte[] xlsx = llamar(get("/api/reportes/partidas.xlsx"), admin, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assert xlsx[0] == 'P' && xlsx[1] == 'K';
        llamar(get("/api/reportes/partidas/" + partida + ".pdf"), tOrg, null).andExpect(status().isOk());

        // Desvincular: el token del celular deja de servir
        long idCel1 = leer(llamar(get("/api/torneos/" + torneo + "/dispositivos"), tAp, null)).get(0).get("id").asLong();
        llamar(delete("/api/dispositivos/" + idCel1), tOrg, null).andExpect(status().isForbidden());
        llamar(delete("/api/dispositivos/" + idCel1), tArb1, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/dispositivo/estado").header("X-Dispositivo", d1)).andExpect(status().isUnauthorized());

        // Torneos: solo el administrador elimina, y solo sin partidas
        llamar(delete("/api/torneos/" + otro), tOrg, null).andExpect(status().isForbidden());
        llamar(delete("/api/torneos/" + torneo), admin, null).andExpect(status().isUnprocessableEntity());
        llamar(delete("/api/torneos/" + otro), admin, null).andExpect(status().isNoContent());

        // Directorio para elegir árbitros; no se puede borrar al único administrador
        assert leer(llamar(get("/api/usuarios/directorio"), tAp, null)).size() == 5;
        llamar(get("/api/usuarios"), tAp, null).andExpect(status().isForbidden());
        long adminId = leer(mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + admin))).at("/id").asLong();
        llamar(delete("/api/usuarios/" + adminId), admin, null).andExpect(status().isUnprocessableEntity());
    }

    private long usuario(String admin, String nombre, String email) throws Exception {
        return leer(llamar(post("/api/usuarios"), admin,
                "{\"nombre\":\"" + nombre + "\",\"email\":\"" + email + "\",\"password\":\"clave123\",\"rol\":\"USUARIO\"}")
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions asignar(String token, long torneo, long usuario, String rol) throws Exception {
        return llamar(post("/api/torneos/" + torneo + "/asignaciones"), token, "{\"usuarioId\":" + usuario + ",\"rol\":\"" + rol + "\"}");
    }

    private ResultActions camara(String dispositivo, String uci, double confianza) throws Exception {
        return llamar(post("/api/dispositivo/jugadas"), null, "{\"uci\":\"" + uci + "\",\"confianza\":" + confianza + "}", dispositivo);
    }

    private String login(String email, String password) throws Exception {
        return leer(llamar(post("/api/auth/login"), null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}").andExpect(status().isOk()))
                .get("token").asText();
    }

    private ResultActions llamar(MockHttpServletRequestBuilder req, String token, String cuerpo) throws Exception {
        return llamar(req, token, cuerpo, null);
    }

    private ResultActions llamar(MockHttpServletRequestBuilder req, String token, String cuerpo, String dispositivo) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (dispositivo != null) req.header("X-Dispositivo", dispositivo);
        if (cuerpo != null) req.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        return mvc.perform(req);
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
