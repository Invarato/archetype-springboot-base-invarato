package ${groupId}.client.ejemplo;

import ${groupId}.client.api.ExampleControllerApi;
import ${groupId}.client.model.MyTableResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba que el cliente generado <b>funciona</b>, no solo que compila.
 *
 * <p>El build ya verificaba que el generador produce clases; eso deja fuera lo que de verdad falla: que
 * las rutas sean las del contrato, que el token viaje en la cabecera y que el JSON se convierta a los
 * modelos. Aquí se levanta un servidor de mentira en un puerto libre y se habla con él usando
 * exactamente el mismo código que {@link EjemploDeUso} — así el ejemplo <b>no puede pudrirse</b>: si
 * alguien cambia la forma de construir el cliente y no actualiza el ejemplo, esto se pone rojo.</p>
 *
 * <p>El servidor es el del JDK ({@code com.sun.net.httpserver}), sin dependencias nuevas. No se levanta
 * la aplicación de verdad a propósito: eso ya lo prueban los tests de {@code app}, y aquí obligaría a
 * arrastrar el servidor entero —con su base de datos— dentro del módulo del cliente.</p>
 */
class ClienteGeneradoTest {

    private HttpServer servidor;
    private final List<String> cabecerasDeAutorizacion = new ArrayList<>();

    @BeforeEach
    void levantarServidorDeMentira() throws IOException {
        // Puerto 0 = que el sistema elija uno libre. Con un puerto fijo, dos builds a la vez chocan.
        servidor = HttpServer.create(new InetSocketAddress(0), 0);

        servidor.createContext("/api/v1/examples/hello", intercambio -> {
            anotarAutorizacion(intercambio.getRequestHeaders().getFirst("Authorization"));
            responder(intercambio, "text/plain;charset=UTF-8", "Hello World");
        });

        servidor.createContext("/api/v1/examples", intercambio -> {
            anotarAutorizacion(intercambio.getRequestHeaders().getFirst("Authorization"));
            responder(intercambio, "application/json",
                    "[{\"name\":\"Uno\",\"surname\":\"A\",\"description\":\"D\",\"myTableParentId\":null}]");
        });

        servidor.start();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    private ExampleControllerApi api() {
        return EjemploDeUso.api("http://localhost:" + servidor.getAddress().getPort(),
                () -> "un-token-de-mentira");
    }

    @Test
    void llamaALaRutaCorrectaYConvierteLaRespuesta() {
        assertEquals("Hello World", api().sayHello());

        List<MyTableResponse> todos = api().listAll();

        assertEquals(1, todos.size());
        // Que esto llegue relleno demuestra que el JSON se mapeo al modelo generado, que es donde se nota
        // si el contrato y el cliente se han desincronizado.
        assertEquals("Uno", todos.getFirst().getName());
        assertEquals("A", todos.getFirst().getSurname());
    }

    @Test
    void mandaElTokenEnCadaPeticion() {
        api().sayHello();
        api().listAll();

        assertEquals(2, cabecerasDeAutorizacion.size(), "Alguna peticion salio sin cabecera Authorization");
        assertTrue(cabecerasDeAutorizacion.stream().allMatch("Bearer un-token-de-mentira"::equals),
                "El token no viaja como Bearer: " + cabecerasDeAutorizacion);
    }

    private void anotarAutorizacion(String valor) {
        cabecerasDeAutorizacion.add(valor);
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio,
                                  String tipoDeContenido, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", tipoDeContenido);
        intercambio.sendResponseHeaders(200, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
